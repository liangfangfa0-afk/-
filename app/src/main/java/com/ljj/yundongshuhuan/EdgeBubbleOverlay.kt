package com.ljj.yundongshuhuan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Transparent, non-interactive full-screen layer that draws subtle motion cues at screen edges. */
class EdgeBubbleOverlay(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = 0x66FFFFFF
    }
    private var motionX = 0f
    private var motionY = 0f
    private var intensity = 0f
    private val count = 7

    fun updateMotion(x: Float, y: Float, magnitude: Float) {
        motionX = (x / 3.5f).coerceIn(-1f, 1f)
        motionY = (y / 3.5f).coerceIn(-1f, 1f)
        intensity = (magnitude / 3f).coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val edge = dp(25f)
        val top = dp(58f)
        val bottom = h - dp(66f)
        if (bottom <= top) return
        val base = dp(7.2f)
        for (i in 0 until count) {
            val fraction = (i + 1f) / (count + 1f)
            val y = top + (bottom - top) * fraction
            val phase = (i - (count - 1) / 2f) / 3f
            val driftX = motionX * dp(13f) * (0.65f + 0.35f * kotlin.math.abs(phase))
            val driftY = motionY * dp(15f) * (0.65f + 0.35f * kotlin.math.abs(phase))
            val pulse = 1f + intensity * 0.16f
            val radius = base * (0.82f + (i % 3) * 0.12f) * pulse
            drawBubble(canvas, edge + driftX, y + driftY, radius)
            drawBubble(canvas, w - edge - driftX, y - driftY, radius)
        }
        // Small top and bottom pairs make the cue read as a perimeter rather than two columns.
        for (i in 0 until 3) {
            val x = w * (i + 1f) / 4f
            val shift = motionX * dp(12f)
            drawBubble(canvas, x + shift, top * 0.52f + motionY * dp(8f), base * 0.78f)
            drawBubble(canvas, x - shift, h - dp(31f) - motionY * dp(8f), base * 0.78f)
        }
    }

    private fun drawBubble(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        fill.shader = RadialGradient(
            cx - radius * 0.28f, cy - radius * 0.32f, radius * 1.55f,
            intArrayOf(0xDDF4FBFF.toInt(), 0xA94DA8E8.toInt(), 0x654B91D8.toInt()),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, fill)
        fill.shader = null
        canvas.drawCircle(cx, cy, radius, rim)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
