package com.motionguard

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "晕动舒缓"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        val status = TextView(this).apply {
            text = "项目初始化中\n\n当前版本尚未启用传感器检测或视觉提示。"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }

        content.addView(title)
        content.addView(status)
        setContentView(content)
    }
}
