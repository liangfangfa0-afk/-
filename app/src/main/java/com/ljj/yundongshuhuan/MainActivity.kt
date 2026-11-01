package com.ljj.yundongshuhuan

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var stateText: TextView
    private lateinit var actionButton: Button
    private lateinit var permissionText: TextView
    private var receiverRegistered = false
    private var waitingForOverlayPermission = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != MotionMonitorService.ACTION_UPDATE) return
            val state = intent.getStringExtra(MotionMonitorService.EXTRA_STATUS) ?: "正在读取传感器"
            stateText.text = state
            val active = state != "监测已停止" && state != "传感器启动失败" && state != "设备没有可用的加速度传感器"
            actionButton.text = if (active) "停止屏幕气泡" else "开启屏幕气泡"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        requestNotificationPermission()
        refreshPermissionState()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(34), dp(26), dp(26))
            setBackgroundColor(Color.rgb(8, 16, 30))
        }
        val brand = TextView(this).apply {
            text = "YUNDONG  /  SHUHUAN"
            textSize = 12f
            letterSpacing = 0.18f
            setTextColor(Color.rgb(123, 181, 238))
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "晕动舒缓"
            textSize = 32f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(5))
        }
        val subtitle = TextView(this).apply {
            text = "让视觉拥有稳定的运动参照"
            textSize = 15f
            setTextColor(Color.rgb(174, 190, 211))
            gravity = Gravity.CENTER
        }
        val preview = BubblePreview(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(260)).apply {
                topMargin = dp(26)
                bottomMargin = dp(24)
            }
        }
        stateText = TextView(this).apply {
            text = "准备就绪"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        }
        actionButton = Button(this).apply {
            text = "开启屏幕气泡"
            textSize = 16f
            isAllCaps = false
            setOnClickListener { onMainAction() }
        }
        permissionText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(155, 174, 199))
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(10))
        }
        val footer = TextView(this).apply {
            text = "气泡显示在屏幕四周，不拦截触摸操作。使用时可从应用通知中停止。"
            textSize = 12f
            setTextColor(Color.rgb(124, 143, 168))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), 0)
        }
        root.addView(brand)
        root.addView(title)
        root.addView(subtitle)
        root.addView(preview)
        root.addView(stateText)
        root.addView(actionButton, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(permissionText)
        root.addView(footer)
        setContentView(root)
    }

    private fun onMainAction() {
        val active = getSharedPreferences("yundongshuhuan", MODE_PRIVATE).getBoolean("running", false)
        if (active) {
            startService(Intent(this, MotionMonitorService::class.java).setAction(MotionMonitorService.ACTION_STOP))
            actionButton.text = "开启屏幕气泡"
            stateText.text = "已停止"
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }
        startMonitor()
    }

    private fun startMonitor() {
        val intent = Intent(this, MotionMonitorService::class.java).setAction(MotionMonitorService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        actionButton.text = "停止屏幕气泡"
        stateText.text = "正在启动气泡提示"
    }

    private fun refreshPermissionState() {
        val overlay = Settings.canDrawOverlays(this)
        permissionText.text = if (overlay) "屏幕悬浮权限：已允许" else "需要允许“显示在其他应用上层”才能显示边缘气泡"
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false
            if (Settings.canDrawOverlays(this)) startMonitor()
        }
        if (getSharedPreferences("yundongshuhuan", MODE_PRIVATE).getBoolean("running", false)) {
            actionButton.text = "停止屏幕气泡"
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(MotionMonitorService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
        receiverRegistered = true
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/** Static preview of the perimeter cue shown when the feature is enabled. */
private class BubblePreview(context: Context) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = Color.rgb(17, 31, 51)
        canvas.drawRoundRect(0f, 0f, w, h, 28f, 28f, paint)
        paint.color = Color.rgb(88, 157, 224)
        val d = resources.displayMetrics.density
        for (i in 0 until 7) {
            val y = h * (i + 1f) / 8f
            val radius = d * (5.5f + (i % 3) * 1.2f)
            canvas.drawCircle(22f * d, y, radius, paint)
            canvas.drawCircle(w - 22f * d, y, radius, paint)
        }
        for (i in 0 until 3) {
            val x = w * (i + 1f) / 4f
            canvas.drawCircle(x, 19f * d, 5f * d, paint)
            canvas.drawCircle(x, h - 19f * d, 5f * d, paint)
        }
        paint.color = Color.rgb(176, 198, 224)
        paint.textSize = 12f * d
        paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText("屏幕四周动态气泡", w / 2f, h / 2f + 4f * d, paint)
    }
}
