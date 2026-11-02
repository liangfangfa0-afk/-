package com.ljj.yundongshuhuan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Free-moving particle field; particles are not attached to fixed screen edges. */
class EdgeBubbleOverlay(context: Context) : View(context) {
    private data class Particle(
        var x: Float, var y: Float, val radius: Float, val alpha: Float,
        val driftX: Float, val driftY: Float, val phase: Float, val sizeAmplitude: Float
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val particles = ArrayList<Particle>()
    private var fieldWidth = 0f
    private var fieldHeight = 0f
    private var lastFrameNanos = 0L
    private var targetX = 0f
    private var targetY = 0f
    private var flowX = 0f
    private var flowY = 0f
    private var sensitivity = 1.8f
    private var paletteIndex = 0
    private var elapsed = 0f
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
        targetX = (-x / 3.2f * gain).coerceIn(-1.4f, 1.4f)
        targetY = (y / 3.2f * gain).coerceIn(-1.4f, 1.4f)
        postInvalidateOnAnimation()
    }

    private fun seedParticles(w: Float, h: Float) {
        particles.clear()
        val random = Random(2701)
        repeat(54) {
            val angle = random.nextFloat() * (Math.PI.toFloat() * 2f)
            val radius = 0.28f + random.nextFloat() * 0.67f
            val nx = 0.5f + kotlin.math.cos(angle) * radius * 0.48f
            val ny = 0.5f + kotlin.math.sin(angle) * radius * 0.46f
            particles += Particle(
                x = nx.coerceIn(0.035f, 0.965f) * w,
                y = ny.coerceIn(0.035f, 0.965f) * h,
                radius = dp(1.2f + random.nextFloat() * 2.6f),
                alpha = 0.28f + random.nextFloat() * 0.42f,
                driftX = (random.nextFloat() - 0.5f) * dp(8f),
                driftY = (random.nextFloat() - 0.5f) * dp(8f),
                phase = random.nextFloat() * 6.28f,\n                sizeAmplitude = 0.18f + random.nextFloat() * 0.30f
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

        val response = 1f - kotlin.math.exp(-dt * 3.8f)
        flowX += (targetX - flowX) * response
        flowY += (targetY - flowY) * response
        val velocityX = flowX * dp(205f)
        val velocityY = flowY * dp(205f)
        val color = palettes[paletteIndex]

        particles.forEach { particle ->
            val localX = particle.driftX * kotlin.math.sin(elapsed * 0.72f + particle.phase)
            val localY = particle.driftY * kotlin.math.cos(elapsed * 0.64f + particle.phase)
            particle.x += (velocityX + localX) * dt
            particle.y += (velocityY + localY) * dt

            val margin = dp(12f)
            if (particle.x < -margin) particle.x = w + margin
            if (particle.x > w + margin) particle.x = -margin
            if (particle.y < -margin) particle.y = h + margin
            if (particle.y > h + margin) particle.y = -margin

            val pulse = 0.88f + 0.12f * kotlin.math.sin(elapsed * 1.1f + particle.phase)
            val alpha = min(255, max(0, (particle.alpha * pulse * 255f).toInt()))
            fill.color = (color and 0x00FFFFFF) or (alpha shl 24)
            val sizePulse = 1f + particle.sizeAmplitude * kotlin.math.sin(elapsed * 1.35f + particle.phase)\n            canvas.drawCircle(particle.x, particle.y, particle.radius * sizePulse, fill)
        }
        postInvalidateOnAnimation()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
