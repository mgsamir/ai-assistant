package com.example.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Rangeen gola (AMOLED black par) + ghere mein dots. Awaaz ke hisaab se saans leta hai.
class OrbView(ctx: Context) : View(ctx) {
    // 0 chup, 1 sun raha, 2 soch raha, 3 bol raha
    var mode = 0
    var level = 0f          // mic ki awaaz 0..1

    private val colors = intArrayOf(
        Color.parseColor("#FF3B30"), Color.parseColor("#FF9500"), Color.parseColor("#FFD60A"),
        Color.parseColor("#34C759"), Color.parseColor("#30D5C8"), Color.parseColor("#0A84FF"),
        Color.parseColor("#BF5AF2"), Color.parseColor("#FF3B30"))
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val mx = Matrix()
    private var rot = 0f
    private var scale = 1f
    private var smooth = 0f
    private var t = 0f
    private var last = 0L

    private fun hue(a: Float): Int {
        val p = ((a % 360f) + 360f) % 360f / 360f * (colors.size - 1)
        val i = p.toInt().coerceAtMost(colors.size - 2)
        val f = p - i
        val c1 = colors[i]; val c2 = colors[i + 1]
        fun m(x: Int, y: Int) = (x + (y - x) * f).toInt()
        return Color.rgb(m(Color.red(c1), Color.red(c2)), m(Color.green(c1), Color.green(c2)), m(Color.blue(c1), Color.blue(c2)))
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
        last = now
        t += dt
        // abhi kitna ubhaar chahiye
        val target = when (mode) {
            1 -> level
            3 -> 0.35f + 0.3f * (0.5f + 0.5f * sin(t * 7f)) * (0.6f + 0.4f * sin(t * 3.1f))
            2 -> 0.15f + 0.1f * sin(t * 4f)
            else -> 0.05f + 0.05f * sin(t * 1.6f)
        }
        smooth += (target - smooth) * (if (target > smooth) 0.35f else 0.12f)
        scale += ((1f + smooth * 0.45f) - scale) * 0.3f
        rot += dt * (if (mode == 2) 140f else if (mode == 0) 18f else 40f + smooth * 160f)

        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h * 0.42f
        val r = w * 0.24f * scale

        // bahar ki halki roshni
        glow.shader = RadialGradient(cx, cy, r * 1.9f,
            intArrayOf(Color.argb((60 + smooth * 120).toInt().coerceAtMost(180), 80, 140, 255), Color.TRANSPARENT),
            floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.9f, glow)

        // gola: ghoomta hua rainbow
        val sweep = SweepGradient(cx, cy, colors, null)
        mx.setRotate(rot, cx, cy)
        sweep.setLocalMatrix(mx)
        fill.shader = sweep
        clip.reset(); clip.addCircle(cx, cy, r, Path.Direction.CW)
        c.save(); c.clipPath(clip)
        c.drawCircle(cx, cy, r, fill)
        // andar naram safed chamak aur kinare par gehraai
        shade.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 1.25f,
            intArrayOf(Color.argb(150, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(150, 0, 0, 0)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, shade)
        c.restore()

        // dots ki ring: gole ke rang se match
        val n = 56
        val ringR = r * 1.38f
        for (i in 0 until n) {
            val a = i * 360f / n
            val rad = (a + rot * 0.3f) * PI.toFloat() / 180f
            val wave = 0.5f + 0.5f * sin(t * (if (mode == 2) 6f else 3f) + i * 0.55f)
            val push = smooth * 0.35f * wave
            val rr = ringR * (1f + push)
            dot.color = hue(a - rot)
            dot.alpha = (110 + 145 * (0.4f * wave + smooth).coerceIn(0f, 1f)).toInt()
            val s = w * 0.0075f * (1f + 1.6f * smooth * wave) + 1.5f
            c.drawCircle(cx + rr * cos(rad), cy + rr * sin(rad), s, dot)
        }
        postInvalidateOnAnimation()
    }
}
