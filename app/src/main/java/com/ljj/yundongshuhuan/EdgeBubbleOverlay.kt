package com.ljj.yundongshuhuan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

class EdgeBubbleOverlay(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.1f); color = 0x77FFFFFF }
    private var motionX = 0f
    private var motionY = 0f
    private var intensity = 0f
    private var sensitivity = 1.8f
    private var paletteIndex = 0
    private val palettes = arrayOf(
        intArrayOf(0xFFE8F8FF.toInt(),0xFF55B8F4.toInt(),0x66318BEA),
        intArrayOf(0xFFE9FFF8.toInt(),0xFF62D9B7.toInt(),0x6622B99A),
        intArrayOf(0xFFF6F0FF.toInt(),0xFFB89AFF.toInt(),0x668C68E8),
        intArrayOf(0xFFFFF5E9.toInt(),0xFFFFB969.toInt(),0x66F28D48)
    )
    fun setStyle(value: Float, palette: Int) { sensitivity = value.coerceIn(0.6f,3.5f); paletteIndex = palette.coerceIn(0,3); postInvalidateOnAnimation() }
    fun updateMotion(x: Float, y: Float, magnitude: Float) {
        val gain = sensitivity / 1.8f
        motionX = (x / 2.5f * gain).coerceIn(-1.35f,1.35f)
        motionY = (y / 2.5f * gain).coerceIn(-1.35f,1.35f)
        intensity = (magnitude / 2.1f * gain).coerceIn(0f,1.35f)
        postInvalidateOnAnimation()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val edge = dp(25f); val top = dp(58f); val bottom = h - dp(66f)
        if (bottom <= top) return
        val base = dp(7.2f); val colors = palettes[paletteIndex]
        for (i in 0 until 7) {
            val fraction = (i + 1f) / 8f; val y = top + (bottom-top)*fraction
            val phase = (i-3f)/3f
            val dx = motionX * dp(18f) * (0.7f + 0.3f*kotlin.math.abs(phase))
            val dy = motionY * dp(20f) * (0.7f + 0.3f*kotlin.math.abs(phase))
            val pulse = (1f + intensity*0.30f).coerceIn(1f,1.45f)
            val radius = base * (0.82f + (i%3)*0.12f) * pulse
            drawBubble(canvas,edge+dx,y+dy,radius,colors)
            drawBubble(canvas,w-edge-dx,y-dy,radius,colors)
        }
        for (i in 0 until 3) {
            val x = w*(i+1f)/4f; val dx = motionX*dp(15f); val dy = motionY*dp(10f)
            val r = base*(0.78f+intensity*0.10f)
            drawBubble(canvas,x+dx,top*0.52f+dy,r,colors)
            drawBubble(canvas,x-dx,h-dp(31f)-dy,r,colors)
        }
    }
    private fun drawBubble(canvas: Canvas,cx: Float,cy: Float,radius: Float,colors: IntArray) {
        fill.shader = RadialGradient(cx-radius*0.28f,cy-radius*0.32f,radius*1.55f,colors,floatArrayOf(0f,0.62f,1f),Shader.TileMode.CLAMP)
        canvas.drawCircle(cx,cy,radius,fill); fill.shader = null; canvas.drawCircle(cx,cy,radius,rim)
    }
    private fun dp(v: Float) = v * resources.displayMetrics.density
}