package com.ljj.yundongshuhuan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Smooth, damped particle motion with one vivid opaque color per selected theme. */
class EdgeBubbleOverlay(context: Context) : View(context) {
    private data class Particle(
        var x: Float, var y: Float, val radius: Float,
        val driftX: Float, val driftY: Float, val phase: Float,
        val sizeAmplitude: Float
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val particles = ArrayList<Particle>()
    private val colors = intArrayOf(
        0xFF00D9FF.toInt(), // vivid cyan
        0xFFFF6D00.toInt(), // vivid orange
        0xFFB6FF00.toInt(), // electric lime
        0xFFB000FF.toInt()  // electric violet
    )
    private var fieldWidth = 0f
    private var fieldHeight = 0f
    private var lastFrameNanos = 0L
    private var targetX = 0f
    private var targetY = 0f
    private var targetTurn = 0f
    private var flowX = 0f
    private var flowY = 0f
    private var flowTurn = 0f
    private var sensitivity = 4.0f
    private var paletteIndex = 0
    private var elapsed = 0f
    private var moving = true
    private var idleAmount = 1f

    fun setStyle(value: Float, palette: Int) {
        sensitivity = value.coerceIn(0.6f, 4.0f)
        paletteIndex = palette.coerceIn(0, 3)
        postInvalidateOnAnimation()
    }

    /**
     * 驱动气泡运动：
     * - stationary：静止时气泡停止（摆动衰减到几乎不动）
     * - 运动中：线性加速度反向（车动气泡反向），yawRate 转向驱动横向漂移
     *   车辆左转（yawRate > 0）→ 气泡向右流动；右转 → 向左流动
     */
    fun updateMotion(x: Float, y: Float, magnitude: Float, yawRate: Float, stationary: Boolean) {
        val gain = sensitivity / 2.4f
        if (stationary) {
            targetX = 0f
            targetY = 0f
            targetTurn = 0f
            moving = false
        } else {
            val turnGain = sensitivity * 0.50f
            targetTurn = (yawRate * turnGain).coerceIn(-1.0f, 1.0f)
            targetX = ((-x / 3.2f) * gain + targetTurn).coerceIn(-1.0f, 1.0f)
            targetY = (y / 3.2f * gain).coerceIn(-1.0f, 1.0f)
            moving = true
        }
        postInvalidateOnAnimation()
    }

    private fun seedParticles(w: Float, h: Float) {
        particles.clear()
        val random = Random(7319)
        repeat(54) { index ->
            val angle = random.nextFloat() * (Math.PI.toFloat() * 2f)
            val spread = 0.28f + random.nextFloat() * 0.67f
            val nx = 0.5f + cos(angle) * spread * 0.48f
            val ny = 0.5f + sin(angle) * spread * 0.46f
            val sizeTier = if (index % 2 == 0) 0.78f else 1.38f
            particles += Particle(
                x = nx.coerceIn(0.035f, 0.965f) * w,
                y = ny.coerceIn(0.035f, 0.965f) * h,
                radius = dp(2.0f + random.nextFloat() * 1.5f) * sizeTier,
                driftX = (random.nextFloat() - 0.5f) * dp(8f),
                driftY = (random.nextFloat() - 0.5f) * dp(8f),
                phase = random.nextFloat() * 6.28f,
                sizeAmplitude = 0.08f + random.nextFloat() * 0.08f
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

        // 静止时摆动衰减到几乎不动，运动时恢复
        val idleTarget = if (moving) 1f else 0.05f
        idleAmount += (idleTarget - idleAmount) * (1f - exp(-dt * 3.0f))

        // Critically damped-style easing filters sensor jitter and avoids abrupt direction changes.
        val response = 1f - exp(-dt * 4.2f)
        flowX += (targetX - flowX) * response
        flowY += (targetY - flowY) * response
        flowTurn += (targetTurn - flowTurn) * response
        val velocityX = (flowX + flowTurn) * dp(200f)
        val velocityY = flowY * dp(200f)
        fill.color = colors[paletteIndex] // ARGB alpha is always 255.

        particles.forEach { particle ->
            val localX = particle.driftX * sin(elapsed * 0.55f + particle.phase) * idleAmount
            val localY = particle.driftY * cos(elapsed * 0.48f + particle.phase) * idleAmount
            particle.x += (velocityX + localX) * dt
            particle.y += (velocityY + localY) * dt

            val margin = dp(12f)
            if (particle.x < -margin) particle.x = w + margin
            if (particle.x > w + margin) particle.x = -margin
            if (particle.y < -margin) particle.y = h + margin
            if (particle.y > h + margin) particle.y = -margin

            val sizePulse = 1f + particle.sizeAmplitude * sin(elapsed * 1.1f + particle.phase)
            canvas.drawCircle(particle.x, particle.y, particle.radius * sizePulse, fill)
        }
        postInvalidateOnAnimation()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
