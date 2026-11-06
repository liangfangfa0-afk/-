package com.ljj.yundongshuhuan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** 两侧常驻大气泡：左 10 右 10，不透明；静止收回两边，运动时沿方向流动。 */
class EdgeBubbleOverlay(context: Context) : View(context) {
    private data class Particle(
        var x: Float, var y: Float, val radius: Float,
        val side: Int,            // -1 = 左侧, +1 = 右侧
        val driftY: Float,
        val phase: Float,
        val sizeAmplitude: Float
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 255
    }
    private val particles = ArrayList<Particle>()
    private val colors = intArrayOf(
        0xFF00D9FF.toInt(),
        0xFFFF6D00.toInt(),
        0xFFB6FF00.toInt(),
        0xFFB000FF.toInt()
    )
    private var fieldWidth = 0f
    private var fieldHeight = 0f
    private var lastFrameNanos = 0L
    private var flowX = 0f
    private var flowTurn = 0f
    private var targetX = 0f
    private var targetTurn = 0f
    private var sensitivity = 4.0f
    private var paletteIndex = 0
    private var elapsed = 0f
    private var stationary = false

    fun setStyle(value: Float, palette: Int) {
        sensitivity = value.coerceIn(0.6f, 4.0f)
        paletteIndex = palette.coerceIn(0, 3)
        postInvalidateOnAnimation()
    }

    /** 车辆左转(yawRate>0)→气泡向右流；右转→向左流；静止收回两边。 */
    fun updateMotion(x: Float, y: Float, magnitude: Float, yawRate: Float, isStationary: Boolean) {
        val gain = sensitivity / 2.4f
        stationary = isStationary
        if (stationary) {
            targetX = 0f
            targetTurn = 0f
        } else {
            val turnGain = sensitivity * 0.50f
            targetTurn = (yawRate * turnGain).coerceIn(-1.0f, 1.0f)
            targetX = ((-x / 3.2f) * gain + targetTurn).coerceIn(-1.0f, 1.0f)
        }
        postInvalidateOnAnimation()
    }

    private fun seedParticles(w: Float, h: Float) {
        particles.clear()
        val random = Random(42)
        repeat(10) { i ->
            val ty = (i + 0.5f) / 10f
            particles += Particle(
                x = (0.04f + random.nextFloat() * 0.10f) * w,
                y = ty * h + (random.nextFloat() - 0.5f) * dp(40f),
                radius = dp(10f + random.nextFloat() * 8f),
                side = -1,
                driftY = (random.nextFloat() - 0.5f) * dp(10f),
                phase = random.nextFloat() * 6.28f,
                sizeAmplitude = 0.06f + random.nextFloat() * 0.06f
            )
        }
        repeat(10) { i ->
            val ty = (i + 0.5f) / 10f
            particles += Particle(
                x = (0.86f + random.nextFloat() * 0.10f) * w,
                y = ty * h + (random.nextFloat() - 0.5f) * dp(40f),
                radius = dp(10f + random.nextFloat() * 8f),
                side = +1,
                driftY = (random.nextFloat() - 0.5f) * dp(10f),
                phase = random.nextFloat() * 6.28f,
                sizeAmplitude = 0.06f + random.nextFloat() * 0.06f
            )
        }
        fieldWidth = w
        fieldHeight = h
        lastFrameNanos = 0L
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (particles.isEmpty() || w != fieldWidth || h != fieldHeight) seedParticles(w, h)

        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0.016f
            else ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.04f)
        lastFrameNanos = now
        elapsed += dt

        val response = 1f - exp(-dt * 4.2f)
        flowX += (targetX - flowX) * response
        flowTurn += (targetTurn - flowTurn) * response
        val velocityX = (flowX + flowTurn) * dp(220f)

        fill.color = colors[paletteIndex]

        // 静止时两侧收敛到边缘位置；运动时沿速度场漂移但被 clamp 在各自半屏
        val leftHomeX = 0.06f * w
        val rightHomeX = 0.94f * w
        val idleReturn = if (stationary) 1f - exp(-dt * 3.5f) else 0f

        particles.forEach { p ->
            // 静止时：往 home 位置收
            if (stationary) {
                val home = if (p.side < 0) leftHomeX else rightHomeX
                p.x += (home - p.x) * idleReturn
            } else {
                p.x += velocityX * dt
            }

            // clamp 在各自半屏范围，不跨界
            val minX = if (p.side < 0) 0.02f * w else 0.66f * w
            val maxX = if (p.side < 0) 0.34f * w else 0.98f * w
            p.x = p.x.coerceIn(minX, maxX)

            // 纵向轻微呼吸浮动（不飘）
            p.y += p.driftY * sin(elapsed * 0.6f + p.phase) * dt * 0.6f
            p.y = p.y.coerceIn(dp(20f), h - dp(20f))

            val sizePulse = 1f + p.sizeAmplitude * sin(elapsed * 1.0f + p.phase)
            canvas.drawCircle(p.x, p.y, p.radius * sizePulse, fill)
        }
        postInvalidateOnAnimation()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
