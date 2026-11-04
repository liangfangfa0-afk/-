package com.ljj.yundongshuhuan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.random.Random

/** Free-moving, fully opaque vivid particle field. */
class EdgeBubbleOverlay(context: Context) : View(context) {
    private data class Particle(
        var x: Float, var y: Float, val radius: Float,
        val driftX: Float, val driftY: Float, val phase: Float,
        val sizeAmplitude: Float, val colorIndex: Int
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
    private var sensitivity = 4.0f
    private var paletteIndex = 0
    private var elapsed = 0f

    // Every theme contains several vivid colors; each particle keeps its own color.
    private val palettes = arrayOf(
        intArrayOf(0xFFFF1744.toInt(), 0xFFFF9100.toInt(), 0xFFFFEA00.toInt(), 0xFF00E676.toInt(), 0xFF00B0FF.toInt(), 0xFFD500F9.toInt()),
        intArrayOf(0xFFFF4081.toInt(), 0xFFFF6D00.toInt(), 0xFFFFD600.toInt(), 0xFFF50057.toInt(), 0xFFFF3D00.toInt(), 0xFFFFAB00.toInt()),
        intArrayOf(0xFF00E5FF.toInt(), 0xFF2979FF.toInt(), 0xFF651FFF.toInt(), 0xFF00BFA5.toInt(), 0xFF3D5AFE.toInt(), 0xFFAA00FF.toInt()),
        intArrayOf(0xFFC6FF00.toInt(), 0xFF00E676.toInt(), 0xFF00E5FF.toInt(), 0xFFFFEA00.toInt(), 0xFFFF4081.toInt(), 0xFF76FF03.toInt())
    )

    fun setStyle(value: Float, palette: Int) {
        sensitivity = value.coerceIn(0.6f, 4.0f)
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
        val random = Random(7201)
        repeat(54) { index ->
            val angle = random.nextFloat() * (Math.PI.toFloat() * 2f)
            val spread = 0.28f + random.nextFloat() * 0.67f
            val nx = 0.5f + kotlin.math.cos(angle) * spread * 0.48f
            val ny = 0.5f + kotlin.math.sin(angle) * spread * 0.46f
            // Alternate small and large particles for a deliberately varied rhythm.
            val sizeTier = if (index % 2 == 0) 0.78f else 1.38f
            particles += Particle(
                x = nx.coerceIn(0.035f, 0.965f) * w,
                y = ny.coerceIn(0.035f, 0.965f) * h,
                radius = dp(1.8f + random.nextFloat() * 1.8f) * sizeTier,
                driftX = (random.nextFloat() - 0.5f) * dp(10f),
                driftY = (random.nextFloat() - 0.5f) * dp(10f),
                phase = random.nextFloat() * 6.28f,
                sizeAmplitude = 0.12f + random.nextFloat() * 0.12f,
                colorIndex = random.nextInt(6)
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

        val response = 1f - kotlin.math.exp(-dt * 7.5f)
        flowX += (targetX - flowX) * response
        flowY += (targetY - flowY) * response
        val velocityX = flowX * dp(300f)
        val velocityY = flowY * dp(300f)
        val colors = palettes[paletteIndex]

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

            // Use the full ARGB color (alpha = 255): no fading or translucency.
            fill.color = colors[particle.colorIndex]
            val sizePulse = 1f + particle.sizeAmplitude * kotlin.math.sin(elapsed * 1.55f + particle.phase)
            canvas.drawCircle(particle.x, particle.y, particle.radius * sizePulse, fill)
        }
        postInvalidateOnAnimation()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
