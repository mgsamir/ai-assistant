package com.example.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object Neon {
    val CYAN = Color.parseColor("#19D8FF")
    val BG = Color.parseColor("#02060C")
    val RED = Color.parseColor("#FF3B4E")

    fun panel(radius: Float, stroke: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.parseColor("#CC081824"))
        g.cornerRadius = radius
        g.setStroke(stroke, CYAN)
        return g
    }

    fun circle(): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(Color.parseColor("#0C2A3A"))
        g.setStroke(5, CYAN)
        return g
    }

    fun styleButton(b: Button) {
        b.background = panel(24f, 3)
        b.setTextColor(CYAN)
        b.isAllCaps = false
        b.minWidth = 0
        b.minHeight = 0
        b.setPadding(0, 0, 0, 0)
        b.stateListAnimator = null
    }
}

class OrbView(ctx: Context) : View(ctx) {
    var mode = 0       // 0 आराम, 1 सुन रहा, 2 सोच रहा, 3 बोल रहा
    var level = 0f     // आवाज़ की ताकत 0 से 1
    private var smooth = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var a3 = 0f
    private var lastT = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rect2 = RectF()
    private var dash: DashPathEffect? = null
    private val amber = Color.parseColor("#FFB020")
    private val start = System.nanoTime()

    private fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a shl 24)

    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        setMeasuredDimension(s, s)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val r = min(w, h) / 2f
        dash = DashPathEffect(floatArrayOf(r * 0.09f, r * 0.06f), 0f)
    }

    override fun onDraw(c: Canvas) {
        val t = (System.nanoTime() - start) / 1e9f
        val dt = t - lastT
        lastT = t
        val target = if (mode == 3) 0.35f + 0.3f * abs(sin(t * 7f)) else level
        smooth += (target - smooth) * 0.2f
        val sp = when (mode) { 2 -> 3f; 1 -> 1.6f; 3 -> 1.3f; else -> 1f }
        a1 += dt * 14f * sp
        a2 -= dt * 22f * sp
        a3 += dt * 8f * sp
        val accent = if (mode == 2) amber else Neon.CYAN
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * 0.96f
        val u = r / 100f
        val pulse = 1f + smooth * 0.05f

        // चमक
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(withAlpha(accent, 110), withAlpha(accent, 40), 0),
            floatArrayOf(0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null

        // घेरे
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.BUTT
        p.color = accent
        p.strokeWidth = 1.2f * u
        p.pathEffect = dash
        c.save()
        c.rotate(a1, cx, cy)
        c.drawCircle(cx, cy, r * 0.93f * pulse, p)
        c.restore()
        p.pathEffect = null

        p.strokeWidth = 2.6f * u
        val rr = r * 0.80f * pulse
        rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
        c.save()
        c.rotate(a2, cx, cy)
        for (i in 0 until 4) c.drawArc(rect, i * 90f + 8f, 62f, false, p)
        c.restore()

        p.color = Neon.RED
        p.strokeWidth = 1.8f * u
        val r3 = r * 0.88f
        rect.set(cx - r3, cy - r3, cx + r3, cy + r3)
        c.save()
        c.rotate(a3, cx, cy)
        c.drawArc(rect, 20f, 12f, false, p)
        c.drawArc(rect, 140f, 8f, false, p)
        c.drawArc(rect, 255f, 14f, false, p)
        c.restore()

        p.color = accent
        p.strokeWidth = 1f * u
        c.drawCircle(cx, cy, r * 0.66f * pulse, p)

        // भीतर की डिस्क
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#04141E")
        c.drawCircle(cx, cy, r * 0.62f * pulse, p)

        // रोबोट
        val bob = sin(t * 2f) * r * 0.012f
        val hw = r * 0.30f
        val hh = r * 0.23f
        val top = cy - hh + bob
        val bottom = cy + hh + bob

        p.color = accent
        c.drawRoundRect(RectF(cx - hw - r * 0.06f, cy - hh * 0.35f + bob, cx - hw + 2f, cy + hh * 0.35f + bob), 2f * u, 2f * u, p)
        c.drawRoundRect(RectF(cx + hw - 2f, cy - hh * 0.35f + bob, cx + hw + r * 0.06f, cy + hh * 0.35f + bob), 2f * u, 2f * u, p)

        rect2.set(cx - hw, top, cx + hw, bottom)
        p.color = Color.parseColor("#062433")
        c.drawRoundRect(rect2, hh * 0.7f, hh * 0.7f, p)
        p.style = Paint.Style.STROKE
        p.color = accent
        p.strokeWidth = 2.5f * u
        c.drawRoundRect(rect2, hh * 0.7f, hh * 0.7f, p)

        c.drawLine(cx, top, cx, top - r * 0.11f, p)
        p.style = Paint.Style.FILL
        c.drawCircle(cx, top - r * 0.13f, r * 0.03f, p)

        val blink = if ((t % 4f) < 0.12f) 0.12f else 1f
        val er = r * 0.058f
        val ex = hw * 0.48f
        val ey = cy - hh * 0.08f + bob
        c.drawOval(RectF(cx - ex - er, ey - er * blink, cx - ex + er, ey + er * blink), p)
        c.drawOval(RectF(cx + ex - er, ey - er * blink, cx + ex + er, ey + er * blink), p)

        val my = cy + hh * 0.5f + bob
        if (mode == 3) {
            val mh = hh * 0.08f + smooth * hh * 0.45f
            c.drawRoundRect(RectF(cx - hw * 0.28f, my, cx + hw * 0.28f, my + mh), mh / 2f, mh / 2f, p)
        } else {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.8f * u
            p.strokeCap = Paint.Cap.ROUND
            c.drawArc(RectF(cx - hw * 0.25f, my - hh * 0.2f, cx + hw * 0.25f, my + hh * 0.2f), 20f, 140f, false, p)
            p.style = Paint.Style.FILL
        }

        postInvalidateOnAnimation()
    }
}

class WaveView(ctx: Context) : View(ctx) {
    var level = 0f
    var active = false
    private var smooth = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val start = System.nanoTime()

    override fun onDraw(c: Canvas) {
        val t = (System.nanoTime() - start) / 1e9f
        smooth += ((if (active) level else 0.03f) - smooth) * 0.25f
        val n = 41
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = w / n
        p.color = Neon.CYAN
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = gap * 0.45f
        for (i in 0 until n) {
            val x = gap * (i + 0.5f)
            val d = abs(i - n / 2f) / (n / 2f)
            val env = 1f - d * 0.85f
            val wob = 0.5f + 0.5f * sin(t * 6f + i * 0.6f)
            val amp = (0.08f + smooth * 0.92f * wob) * env
            val bh = max(4f, h * amp)
            c.drawLine(x, h / 2f - bh / 2f, x, h / 2f + bh / 2f, p)
        }
        postInvalidateOnAnimation()
    }
}
