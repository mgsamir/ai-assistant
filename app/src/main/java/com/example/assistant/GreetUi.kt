package com.example.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Naye (khali) chat par dikhne wala rangeen chaar-nok wala taara (Gemini jaisa), dheere dheere dhadakta hai
class SparkleView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    override fun onDraw(c: Canvas) {
        val t = SystemClock.uptimeMillis() / 1000f
        val k = 0.94f + 0.06f * sin(t * 2.2f)
        val r = min(width, height) / 2f * k
        val cx = width / 2f
        val cy = height / 2f
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r,
            intArrayOf(Color.parseColor("#4F8BFF"), Color.parseColor("#9B6BFF"), Color.parseColor("#FF6BA8")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(cx, cy - r)
        path.quadTo(cx, cy, cx + r, cy)
        path.quadTo(cx, cy, cx, cy + r)
        path.quadTo(cx, cy, cx - r, cy)
        path.quadTo(cx, cy, cx, cy - r)
        path.close()
        c.save()
        c.rotate(12f * sin(t * 0.8f), cx, cy)
        c.drawPath(path, p)
        c.restore()
        postInvalidateOnAnimation()
    }
}

// Khulte hi neeche se uthti rangeen bindiyon ki lehar (Gemini jaisi), kuch second mein ghul jaati hai
class GlowView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t0 = 0L
    private var running = false
    private val pink = Color.parseColor("#FF5FA2")
    private val purple = Color.parseColor("#8F6BFF")
    private val blue = Color.parseColor("#4F8BFF")

    fun start() { t0 = SystemClock.uptimeMillis(); running = true; invalidate() }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
        return Color.rgb(r, g, bl)
    }

    override fun onDraw(c: Canvas) {
        if (!running) return
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        if (t > 3.4f) { running = false; return }
        val d = resources.displayMetrics.density
        // chaudai: dono kinaron tak poori (pehli aur aakhri bindi theek kinare par)
        val cols = max(8, (width / (9f * d)).toInt())
        val gx = width.toFloat() / cols
        val rows = max(10, (height / (9f * d)).toInt())
        val gy = height.toFloat() / rows
        val rise = min(1f, t / 1.7f)                              // lehar neeche se upar tak badhti hai
        val fade = 1f - min(1f, max(0f, (t - 1.9f) / 1.4f))       // phir poori ghul jaati hai
        for (i in 0..cols) {
            val fx = i / cols.toFloat()
            val col = if (fx < 0.5f) mix(pink, purple, fx * 2f) else mix(purple, blue, (fx - 0.5f) * 2f)
            for (j in 0..rows) {
                val fy = j / rows.toFloat()                           // 0 neeche, 1 upar
                val reach = ((rise * 1.15f - fy) / 0.22f).coerceIn(0f, 1f)
                val base = (1f - fy) * (1f - fy * 0.35f)
                val wave = 0.62f + 0.38f * sin(i * 0.33f + j * 0.17f + t * 3.4f)
                val a = reach * base * wave * fade
                if (a <= 0.02f) continue
                p.color = col
                p.alpha = (a * 215f).toInt().coerceIn(0, 255)
                c.drawCircle(i * gx, height - j * gy, d * (0.8f + 1.7f * a), p)
            }
        }
        postInvalidateOnAnimation()
    }
}
