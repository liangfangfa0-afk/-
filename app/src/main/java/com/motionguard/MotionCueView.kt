package com.motionguard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** In-app visual prototype; this deliberately does not create a system overlay. */
class MotionCueView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var offsetX = 0f
    private var offsetY = 0f
    private var intensity = 0f

    fun setMotion(x: Float, y: Float, magnitude: Float) {
        offsetX = min(1f, max(-1f, x / 5f))
        offsetY = min(1f, max(-1f, y / 5f))
        intensity = min(1f, magnitude / 4f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = 0xFFF2F4F7.toInt()
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 28f, 28f, paint)

        val baseRadius = 7f + intensity * 2f
        val spacing = h / 8f
        for (i in 0 until 7) {
            val y = spacing * (i + 1)
            val verticalShift = offsetY * 18f
            val horizontalShift = offsetX * 22f
            val radius = baseRadius * (0.72f + (i % 3) * 0.12f)
            paint.color = 0xFF3978C6.toInt()
            canvas.drawCircle(22f + horizontalShift, y + verticalShift, radius, paint)
            canvas.drawCircle(w - 22f - horizontalShift, y - verticalShift, radius, paint)
        }

        paint.color = 0xFF526174.toInt()
        paint.textSize = 13f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("动态提示原型", w / 2f, h / 2f + h * 0.30f, paint)
    }
}
