package com.ljj.yundongshuhuan

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.*
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import java.util.ArrayDeque
import java.util.Locale

class MotionMonitorService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START = "com.ljj.yundongshuhuan.START"
        const val ACTION_STOP = "com.ljj.yundongshuhuan.STOP"
        const val ACTION_UPDATE = "com.ljj.yundongshuhuan.UPDATE"
        const val ACTION_CONFIG = "com.ljj.yundongshuhuan.CONFIG"
        const val EXTRA_SENSITIVITY = "sensitivity"
        const val EXTRA_COLOR = "bubble_color"
        const val EXTRA_STATUS = "status"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_Z = "z"
        const val EXTRA_INTENSITY = "intensity"
        const val EXTRA_SAMPLES = "samples"
        const val EXTRA_ROLL = "roll"
        const val EXTRA_PITCH = "pitch"
        const val EXTRA_AZIMUTH = "azimuth"
        const val EXTRA_YAW_RATE = "yaw_rate"
        const val EXTRA_STATIONARY = "stationary"
        private const val CHANNEL_ID = "motion_monitor"
        private const val NOTIFICATION_ID = 41
    }

    private lateinit var manager: SensorManager
    private var accelSensor: Sensor? = null
    private var linearSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var magneticSensor: Sensor? = null
    private var running = false
    private var overlay: EdgeBubbleOverlay? = null
    private var windowManager: WindowManager? = null

    // 重力校准（针对 TYPE_ACCELEROMETER 原始数据）
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    // 线性加速度低通滤波
    private var fx = 0f
    private var fy = 0f
    private var fz = 0f
    // 陀螺仪 yaw 角速度（rad/s，低通）
    private var yawRate = 0f
    // 姿态角
    private var roll = 0f
    private var pitch = 0f
    private var azimuth = 0f
    // 原始传感器缓存
    private var lastAccel = FloatArray(3) { 0f }
    private var lastMagnetic = FloatArray(3) { 0f }
    private var hasMagnetic = false
    private var hasAccelForOrientation = false
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var samples = 0
    private var sensitivity = 4.0f
    private var bubbleColor = 0
    private var lastBroadcast = 0L
    private var stationary = false
    private val window = ArrayDeque<Float>()
    private var sumSquares = 0.0

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(SENSOR_SERVICE) as SensorManager
        linearSensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        accelSensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magneticSensor = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CONFIG) {
            sensitivity = intent.getFloatExtra(EXTRA_SENSITIVITY, sensitivity).coerceIn(0.6f, 4.0f)
            bubbleColor = intent.getIntExtra(EXTRA_COLOR, bubbleColor).coerceIn(0, 3)
            overlay?.setStyle(sensitivity, bubbleColor)
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (running) return
        val s = linearSensor ?: accelSensor ?: run {
            broadcast("设备没有可用的加速度传感器")
            stopSelf()
            return
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        resetFilter()
        val prefs = getSharedPreferences("yundongshuhuan", MODE_PRIVATE)
        sensitivity = prefs.getFloat("sensitivity", 4.0f).coerceIn(0.6f, 4.0f)
        bubbleColor = prefs.getInt("bubble_color", 0).coerceIn(0, 3)
        val sensors = buildList {
            add(s)
            accelSensor?.let { add(it) }
            gyroSensor?.let { add(it) }
            magneticSensor?.let { add(it) }
        }
        running = sensors.all { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (!running) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            broadcast("传感器启动失败")
            stopSelf()
            return
        }
        getSharedPreferences("yundongshuhuan", MODE_PRIVATE).edit().putBoolean("running", true).apply()
        if (Settings.canDrawOverlays(this)) showOverlay()
        broadcast("屏幕气泡已开启")
    }

    private fun showOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        val view = EdgeBubbleOverlay(this).apply { setStyle(sensitivity, bubbleColor) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "晕动舒缓屏幕边缘提示"
            alpha = 1f
        }
        try {
            windowManager?.addView(view, params)
            overlay = view
        } catch (_: WindowManager.BadTokenException) {
            overlay = null
        } catch (_: SecurityException) {
            overlay = null
        }
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: IllegalArgumentException) {
        }
        overlay = null
    }

    private fun resetFilter() {
        gx = 0f; gy = 0f; gz = 0f
        fx = 0f; fy = 0f; fz = 0f
        yawRate = 0f
        roll = 0f; pitch = 0f; azimuth = 0f
        samples = 0
        stationary = false
        window.clear()
        sumSquares = 0.0
    }

    private fun stopMonitoring() {
        if (running) manager.unregisterListener(this)
        running = false
        removeOverlay()
        getSharedPreferences("yundongshuhuan", MODE_PRIVATE).edit().putBoolean("running", false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        broadcast("监测已停止")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running || event.values.size < 3) return
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> processLinear(event.values)
            Sensor.TYPE_ACCELEROMETER -> processAccel(event.values)
            Sensor.TYPE_GYROSCOPE -> processGyro(event.values)
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, lastMagnetic, 0, 3)
                hasMagnetic = true
                updateOrientation()
            }
        }
    }

    private fun processLinear(values: FloatArray) {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val k = .20f
        fx += k * (x - fx); fy += k * (y - fy); fz += k * (z - fz)
        val magnitude = sqrt(fx * fx + fy * fy + fz * fz)
        window.addLast(magnitude)
        sumSquares += magnitude.toDouble() * magnitude
        if (window.size > 50) {
            val old = window.removeFirst()
            sumSquares -= old.toDouble() * old
        }
        samples++
        val rms = if (window.isEmpty()) 0f else sqrt((sumSquares / window.size).coerceAtLeast(0.0)).toFloat()
        val state = currentState(rms, yawRate)
        stationary = state == "静止"
        overlay?.updateMotion(fx, fy, magnitude, yawRate, stationary)
        val now = System.currentTimeMillis()
        if (now - lastBroadcast >= 200L) {
            lastBroadcast = now
            broadcast(state, magnitude, rms)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
                NOTIFICATION_ID,
                buildNotification(
                    "$state · " + String.format(Locale.US, "横滚%.0f° 俯仰%.0f°", roll, pitch) +
                    " · RMS " + String.format(Locale.US, "%.2f", rms) + " m/s²"
                )
            )
        }
    }

    private fun processAccel(values: FloatArray) {
        val alpha = .88f
        gx = alpha * gx + (1 - alpha) * values[0]
        gy = alpha * gy + (1 - alpha) * values[1]
        gz = alpha * gz + (1 - alpha) * values[2]
        System.arraycopy(values, 0, lastAccel, 0, 3)
        hasAccelForOrientation = true
        // 姿态角（roll/pitch），使用原始含重力分量计算更稳定
        val rawX = values[0]
        val rawY = values[1]
        val rawZ = values[2]
        roll = Math.toDegrees(atan2(rawY.toDouble(), rawZ.toDouble())).toFloat()
        pitch = Math.toDegrees(atan2((-rawX).toDouble(), sqrt(rawY * rawY + rawZ * rawZ).toDouble())).toFloat()
        updateOrientation()
    }

    private fun processGyro(values: FloatArray) {
        // z 轴角速度 = 偏航（转向）角速度，正为逆时针（左转趋势）
        val rawYaw = values[2]
        yawRate += .25f * (rawYaw - yawRate)
    }

    private fun updateOrientation() {
        if (!hasAccelForOrientation) return
        if (hasMagnetic) {
            val ok = SensorManager.getRotationMatrix(
                rotationMatrix, null, lastAccel, lastMagnetic
            )
            if (ok) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuth < 0f) azimuth += 360f
                return
            }
        }
        // 无磁场传感器时方位角不可用
        azimuth = 0f
    }

    private fun currentState(rms: Float, yaw: Float): String {
        val still = rms < 0.14f && abs(yaw) < 0.10f
        return when {
            still -> "静止"
            yaw > 0.12f -> "正在左转"
            yaw < -0.12f -> "正在右转"
            fy > 0.16f -> "正在加速"
            fy < -0.16f -> "正在减速"
            rms < 0.45f -> "轻微运动"
            rms < 1.10f -> "持续运动"
            else -> "明显颠簸"
        }
    }

    private fun broadcast(state: String, magnitude: Float = 0f, rms: Float = 0f) {
        sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).apply {
            putExtra(EXTRA_STATUS, state)
            putExtra(EXTRA_X, fx)
            putExtra(EXTRA_Y, fy)
            putExtra(EXTRA_Z, fz)
            putExtra(EXTRA_INTENSITY, magnitude)
            putExtra(EXTRA_SAMPLES, samples)
            putExtra("rms", rms)
            putExtra(EXTRA_ROLL, roll)
            putExtra(EXTRA_PITCH, pitch)
            putExtra(EXTRA_AZIMUTH, azimuth)
            putExtra(EXTRA_YAW_RATE, yawRate)
            putExtra(EXTRA_STATIONARY, stationary)
        })
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "晕动舒缓运行状态", NotificationManager.IMPORTANCE_LOW)
            channel.description = "显示屏幕边缘气泡提示运行状态"
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(line: String = "屏幕边缘气泡正在运行"): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MotionMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("晕动舒缓")
            .setContentText(line)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止气泡", stop).build())
            .build()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        manager.unregisterListener(this)
        running = false
        removeOverlay()
        getSharedPreferences("yundongshuhuan", MODE_PRIVATE).edit().putBoolean("running", false).apply()
        super.onDestroy()
    }
}
