package com.motionguard

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var motionSensor: Sensor? = null
    private var isMonitoring = false
    private lateinit var statusText: TextView
    private lateinit var valueText: TextView
    private lateinit var toggleButton: Button
    private lateinit var cueView: MotionCueView

    private var gravity = FloatArray(3)
    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f
    private var lastUiUpdate = 0L
    private var sampleCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 32, 28, 24)
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "尚未开始检测"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 8)
        }

        cueView = MotionCueView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                280
            ).apply { topMargin = 16 }
        }

        valueText = TextView(this).apply {
            text = "等待传感器数据"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }

        toggleButton = Button(this).apply {
            text = "开始检测"
            setOnClickListener { setMonitoring(!isMonitoring) }
        }

        val note = TextView(this).apply {
            text = "第一版仅在本页面打开时采集传感器数据。动态圆点是应用内原型，不会覆盖其他应用。"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        root.addView(title)
        root.addView(statusText)
        root.addView(cueView)
        root.addView(valueText)
        root.addView(toggleButton)
        root.addView(note)
        setContentView(root)
    }

    private fun setMonitoring(enabled: Boolean) {
        if (enabled == isMonitoring) return
        isMonitoring = enabled
        if (enabled) {
            val sensor = motionSensor
            if (sensor == null) {
                isMonitoring = false
                statusText.text = "设备未提供可用的加速度传感器"
                return
            }
            gravity = FloatArray(3)
            filteredX = 0f
            filteredY = 0f
            filteredZ = 0f
            sampleCount = 0
            val registered = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            if (!registered) {
                isMonitoring = false
                statusText.text = "传感器启动失败"
                return
            }
            toggleButton.text = "停止检测"
            statusText.text = "正在采集运动数据"
        } else {
            sensorManager.unregisterListener(this)
            toggleButton.text = "开始检测"
            statusText.text = "检测已停止"
            valueText.text = "传感器已停止"
            cueView.setMotion(0f, 0f, 0f)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isMonitoring || event.values.size < 3) return

        val x: Float
        val y: Float
        val z: Float
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val alpha = 0.88f
            for (i in 0..2) {
                gravity[i] = alpha * gravity[i] + (1f - alpha) * event.values[i]
            }
            x = event.values[0] - gravity[0]
            y = event.values[1] - gravity[1]
            z = event.values[2] - gravity[2]
        } else {
            x = event.values[0]
            y = event.values[1]
            z = event.values[2]
        }

        val smooth = 0.22f
        filteredX += smooth * (x - filteredX)
        filteredY += smooth * (y - filteredY)
        filteredZ += smooth * (z - filteredZ)
        sampleCount++

        val magnitude = sqrt(
            filteredX * filteredX +
                filteredY * filteredY +
                filteredZ * filteredZ
        )

        cueView.setMotion(filteredX, filteredY, magnitude)

        val now = System.currentTimeMillis()
        if (now - lastUiUpdate > 180L) {
            lastUiUpdate = now
            val source = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                "线性加速度传感器"
            } else {
                "加速度计（已做重力分离）"
            }
            valueText.text = String.format(
                Locale.getDefault(),
                "%s\nX %.2f  Y %.2f  Z %.2f m/s²\n强度 %.2f m/s² · 样本 %d",
                source, filteredX, filteredY, filteredZ, magnitude, sampleCount
            )
            statusText.text = if (magnitude > 1.15f) {
                "检测到较明显的运动变化（不代表已确认乘车）"
            } else {
                "正在检测运动变化"
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onResume() {
        super.onResume()
        // Sensors are registered only after the user explicitly starts monitoring.
    }

    override fun onPause() {
        if (isMonitoring) {
            sensorManager.unregisterListener(this)
            isMonitoring = false
            toggleButton.text = "开始检测"
            statusText.text = "页面离开前已暂停传感器"
        }
        super.onPause()
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }
}
