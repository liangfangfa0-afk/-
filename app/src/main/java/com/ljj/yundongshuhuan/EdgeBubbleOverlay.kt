package com.ljj.yundongshuhuan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.abs

class EdgeBubbleOverlay(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.7f)
        color = 0x35FFFFFF
    }
    private var motionX = 0f
    private var motionY = 0f
    private var intensity = 0f
    private var sensitivity = 1.8f
    private var paletteIndex = 0
    private val palettes = intArrayOf(
        0xFFB8B8B8.toInt(), 0xFF82B7A0.toInt(),
        0xFFAA9AC8.toInt(), 0xFFD0A36D.toInt()
    )

    fun setStyle(value: Float, palette: Int) {
        sensitivity = value.coerceIn(0.6f, 3.5f)
        paletteIndex = palette.coerceIn(0, 3)
        postInvalidateOnAnimation()
    }

    fun updateMotion(x: Float, y: Float, magnitude: Float) {
        val gain = sensitivity / 1.8f
        // Reverse the measured acceleration for the visual cue direction.
        motionX = (-x / 2.5f * gain).coerceIn(-1.35f, 1.35f)
        motionY = (-y / 2.5f * gain).coerceIn(-1.35f, 1.35f)
        intensity = (magnitude / 2.1f * gain).coerceIn(0f, 1.2f)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val color = palettes[paletteIndex]
        val top = dp(54f)
        val bottom = h - dp(58f)
        val sideInset = dp(17f)
        val radius = dp(3.1f) + dp(0.8f) * intensity

        // Horizontal cue: the row translates opposite to the sensed lateral acceleration.
        val horizontalShift = motionX * dp(44f)
        val count = 9
        for (i in 0 until count) {
            val baseX = w * (i + 0.5f) / count
            val x = wrap(baseX + horizontalShift, w)
            val taper = 0.52f + 0.48f * (1f - abs(i - (count - 1) / 2f) / (count / 2f))
            val r = radius * taper
            drawDot(canvas, x, top, r, color, 0.72f)
            drawDot(canvas, x, bottom, r, color, 0.72f)
        }

        // Vertical cue: front/back motion uses the same reversed mapping on the Y axis.
        val verticalShift = motionY * dp(34f)
        for (i in 0 until 7) {
            val baseY = top + (bottom - top) * (i + 0.5f) / 7f
            val y = wrap(baseY + verticalShift - top, bottom - top) + top
            val taper = 0.62f + 0.38f * (1f - abs(i - 3f) / 4f)
            val r = radius * taper
            drawDot(canvas, sideInset, y, r, color, 0.68f)
            drawDot(canvas, w - sideInset, y, r, color, 0.68f)
        }
    }

    private fun drawDot(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, opacity: Float) {
        fill.color = (color and 0x00FFFFFF) or (((0x55 + (opacity * 0x35).toInt()).coerceIn(0, 255)) shl 24)
        canvas.drawCircle(x, y, radius, fill)
        rim.color = 0x28FFFFFF
        canvas.drawCircle(x, y, radius, rim)
    }

    private fun wrap(value: Float, range: Float): Float {
        if (range <= 0f) return value
        return ((value % range) + range) % range
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
