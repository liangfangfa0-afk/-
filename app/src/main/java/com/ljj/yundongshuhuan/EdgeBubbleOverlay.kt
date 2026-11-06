package com.ljj.yundongshuhuan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 两侧常驻气泡：左 10 右 10；静止收回两侧并渐隐，运动时平滑流动。 */
class EdgeBubbleOverlay(context: Context) : View(context) {
    private data class Particle(
        var x: Float, var y: Float, val radius: Float,
        var alpha: Float,
        val side: Int,            // -1 = 左侧, +1 = 右侧
        val driftY: Float,
        val phase: Float,
        val sizeAmplitude: Float
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
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
    private var targetAlpha = 0f
    private var motionLevel = 0f
    private var sensitivity = 4.0f
    private var paletteIndex = 0
    private var elapsed = 0f
    private var stationary = false

    fun setStyle(value: Float, palette: Int) {
        sensitivity = value.coerceIn(0.6f, 4.0f)
        paletteIndex = palette.coerceIn(0, 3)
        postInvalidateOnAnimation()
    }

    /** 车辆左转(yawRate>0)→气泡向右流；右转→向左流；静止收回两侧并渐隐。 */
    fun updateMotion(x: Float, y: Float, magnitude: Float, yawRate: Float, isStationary: Boolean) {
        val gain = sensitivity / 2.4f
        stationary = isStationary
        if (stationary) {
            targetX = 0f
            targetTurn = 0f
            targetAlpha = 0f
            motionLevel = 0f
        } else {
            val turnGain = sensitivity * 0.50f
            targetTurn = (yawRate * turnGain).coerceIn(-1.0f, 1.0f)
            targetX = ((-x / 3.2f) * gain + targetTurn).coerceIn(-1.0f, 1.0f)
            // 运动越强越亮，静止时淡出
            val level = (0.55f + magnitude * 0.30f).coerceIn(0f, 1f)
            motionLevel += (level - motionLevel) * 0.15f
            targetAlpha = (0.62f + motionLevel * 0.38f).coerceIn(0f, 1f)
        }
        postInvalidateOnAnimation()
    }

    private fun seedParticles(w: Float, h: Float) {
        particles.clear()
        val random = Random(42)
        repeat(10) { i ->
            val ty = (i + 0.5f) / 10f
            particles += Particle(
                x = (0.045f + random.nextFloat() * 0.09f) * w,
                y = ty * h + (random.nextFloat() - 0.5f) * dp(34f),
                radius = dp(6f + random.nextFloat() * 4.5f),
                alpha = 0f,
                side = -1,
                driftY = (random.nextFloat() - 0.5f) * dp(6f),
                phase = random.nextFloat() * 6.28f,
                sizeAmplitude = 0.03f + random.nextFloat() * 0.03f
            )
        }
        repeat(10) { i ->
            val ty = (i + 0.5f) / 10f
            particles += Particle(
                x = (0.865f + random.nextFloat() * 0.09f) * w,
                y = ty * h + (random.nextFloat() - 0.5f) * dp(34f),
                radius = dp(6f + random.nextFloat() * 4.5f),
                alpha = 0f,
                side = +1,
                driftY = (random.nextFloat() - 0.5f) * dp(6f),
                phase = random.nextFloat() * 6.28f,
                sizeAmplitude = 0.03f + random.nextFloat() * 0.03f
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
            else ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.042f)
        lastFrameNanos = now
        elapsed += dt

        // 速度场：更慢的阻尼 → 运动更顺，不生硬
        val response = 1f - exp(-dt * 2.4f)
        flowX += (targetX - flowX) * response
        flowTurn += (targetTurn - flowTurn) * response
        val velocityX = (flowX + flowTurn) * dp(190f)

        fill.color = colors[paletteIndex]

        val leftHomeX = 0.055f * w
        val rightHomeX = 0.945f * w
        val homeFollow = 1f - exp(-dt * 2.2f)

        particles.forEach { p ->
            // 位置：静止收回各自一侧；运动时全屏自由流动，不做半屏限制
            if (stationary) {
                val home = if (p.side < 0) leftHomeX else rightHomeX
                p.x += (home - p.x) * homeFollow
            } else {
                p.x += velocityX * dt
            }

            // 出屏后从另一侧绕回，不卡在任何边界
            val margin = p.radius * 2f
            if (p.x < -margin) p.x = w + margin
            if (p.x > w + margin) p.x = -margin

            // 纵向极缓呼吸
            p.y += p.driftY * sin(elapsed * 0.45f + p.phase) * dt * 0.5f
            p.y = p.y.coerceIn(dp(18f), h - dp(18f))

            // 渐隐：按目标透明度平滑过渡（静止淡出、运动淡入）
            p.alpha += (targetAlpha - p.alpha) * (1f - exp(-dt * 2.6f))

            // 贴边额外渐隐，让收进去的气泡自然消失
            val edgeScale = if (p.side < 0)
                (p.x / (0.075f * w)).coerceIn(0f, 1f)
            else
                ((w - p.x) / (0.075f * w)).coerceIn(0f, 1f)
            val finalAlpha = (p.alpha * edgeScale).coerceIn(0f, 1f)

            if (finalAlpha < 0.02f) return@forEach

            val sizePulse = 1f + p.sizeAmplitude * sin(elapsed * 0.8f + p.phase)
            fill.alpha = (finalAlpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.radius * sizePulse, fill)
        }
        postInvalidateOnAnimation()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
