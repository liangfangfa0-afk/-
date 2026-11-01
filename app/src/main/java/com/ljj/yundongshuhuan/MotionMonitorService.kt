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
import kotlin.math.sqrt
import java.util.ArrayDeque
import java.util.Locale

class MotionMonitorService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START = "com.ljj.yundongshuhuan.START"
        const val ACTION_STOP = "com.ljj.yundongshuhuan.STOP"
        const val ACTION_UPDATE = "com.ljj.yundongshuhuan.UPDATE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_Z = "z"
        const val EXTRA_INTENSITY = "intensity"
        const val EXTRA_SAMPLES = "samples"
        private const val CHANNEL_ID = "motion_monitor"
        private const val NOTIFICATION_ID = 41
    }

    private lateinit var manager: SensorManager
    private var sensor: Sensor? = null
    private var running = false
    private var overlay: EdgeBubbleOverlay? = null
    private var windowManager: WindowManager? = null
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private var fx = 0f
    private var fy = 0f
    private var fz = 0f
    private var samples = 0
    private var lastBroadcast = 0L
    private val window = ArrayDeque<Float>()
    private var sumSquares = 0.0

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        val s = sensor ?: run {
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
        running = manager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
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
        val view = EdgeBubbleOverlay(this)
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
        samples = 0
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
        val x: Float
        val y: Float
        val z: Float
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val alpha = .88f
            gx = alpha * gx + (1 - alpha) * event.values[0]
            gy = alpha * gy + (1 - alpha) * event.values[1]
            gz = alpha * gz + (1 - alpha) * event.values[2]
            x = event.values[0] - gx
            y = event.values[1] - gy
            z = event.values[2] - gz
        } else {
            x = event.values[0]; y = event.values[1]; z = event.values[2]
        }
        val k = .20f
        fx += k * (x - fx); fy += k * (y - fy); fz += k * (z - fz)
        val magnitude = sqrt(fx * fx + fy * fy + fz * fz)
        overlay?.updateMotion(fx, fy, magnitude)
        window.addLast(magnitude)
        sumSquares += magnitude.toDouble() * magnitude
        if (window.size > 50) {
            val old = window.removeFirst()
            sumSquares -= old.toDouble() * old
        }
        samples++
        val rms = if (window.isEmpty()) 0f else sqrt((sumSquares / window.size).coerceAtLeast(0.0)).toFloat()
        val now = System.currentTimeMillis()
        if (now - lastBroadcast >= 250L) {
            lastBroadcast = now
            val state = when {
                rms < .12f -> "相对静止"
                rms < .45f -> "轻微运动"
                rms < 1.10f -> "持续运动"
                else -> "明显颠簸"
            }
            broadcast(state, magnitude, rms)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
                NOTIFICATION_ID,
                buildNotification("$state · RMS " + String.format(Locale.US, "%.2f", rms) + " m/s²")
            )
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
