package com.example.assistant

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.RippleDrawable
import android.view.animation.DecelerateInterpolator
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// Chaar dot jo ghoomte aur chamakte hain (kaam chalte samay, Claude jaisa)
class SpinDots(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4F8BFF") }
    override fun onDraw(c: Canvas) {
        val t = SystemClock.uptimeMillis()
        val ang = (t % 2400L) / 2400f * (2f * Math.PI.toFloat())
        val m = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val rr = m * 0.30f
        for (i in 0 until 4) {
            val a = ang + i * (Math.PI.toFloat() / 2f)
            val pulse = 0.5f + 0.5f * Math.sin((ang * 2f + i).toDouble()).toFloat()
            p.alpha = (140 + 115 * pulse).toInt()
            c.drawCircle(cx + rr * Math.cos(a.toDouble()).toFloat(), cy + rr * Math.sin(a.toDouble()).toFloat(), m * (0.075f + 0.04f * pulse), p)
        }
        postInvalidateOnAnimation()
    }
}

// Text par chamak ki patti baayen se daayen guzarti hai; chahe to shabd badalte rehte hain (Thinking, Mulling ...)
class ShimmerText(ctx: Context) : TextView(ctx) {
    var dim = Color.parseColor("#A8A8B2")
    var lit = Color.parseColor("#FFFFFF")
    var shimmer = true
    private var words: List<String>? = null
    init { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val tick = object : Runnable {
        override fun run() {
            val w = words ?: return
            val n = w[((SystemClock.uptimeMillis() / 1900L) % w.size).toInt()]
            if (text.toString() != n) text = n
            postDelayed(this, 500)
        }
    }

    fun cycle(list: List<String>?) {
        if (list === words) return
        words = list
        removeCallbacks(tick)
        if (list != null) { text = list[0]; post(tick) }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (words != null) post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        if (!shimmer || width <= 0) { paint.shader = null; super.onDraw(c); return }
        val w = width.toFloat()
        val t = (SystemClock.uptimeMillis() % 1700L) / 1700f
        val x = -w * 0.5f + t * w * 2.0f
        paint.shader = LinearGradient(x, 0f, x + w * 0.5f, 0f, intArrayOf(dim, lit, dim), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        super.onDraw(c)
        paint.shader = null
        postInvalidateOnAnimation()
    }
}

// Steps ke beech ki seedhi lakeer (timeline)
class TimeCol(ctx: Context, private val first: Boolean, private val last: Boolean) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E2E32"); strokeWidth = 2f * ctx.resources.displayMetrics.density }
    override fun onDraw(c: Canvas) {
        if (first && last) return
        val h = height.toFloat()
        c.drawLine(width / 2f, if (first) h / 2f else 0f, width / 2f, if (last) h / 2f else h, p)
    }
}

// Chalta hua step: dhadakta hua dot
class PulseDot(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4F8BFF") }
    override fun onDraw(c: Canvas) {
        val ph = (SystemClock.uptimeMillis() % 1100L) / 1100f
        val k = 0.5f + 0.5f * Math.sin(ph * 2.0 * Math.PI).toFloat()
        p.alpha = (150 + 105 * k).toInt()
        c.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f * (0.7f + 0.3f * k), p)
        postInvalidateOnAnimation()
    }
}

// Teen dot jo baari baari se chamakte hain ("kaam chal raha hai")
class DotsView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4F8BFF") }
    override fun onDraw(c: Canvas) {
        val t = SystemClock.uptimeMillis() / 320f
        val r = height * 0.13f
        for (i in 0 until 3) {
            val ph = ((t - i * 0.6f) % 3f + 3f) % 3f
            val a = if (ph < 1f) 1f - ph * 0.7f else 0.3f
            p.alpha = (255 * a).toInt()
            c.drawCircle(width * (0.2f + 0.3f * i), height / 2f, r * (if (ph < 1f) 1.25f else 1f), p)
        }
        postInvalidateOnAnimation()
    }
}

// "Kya kya kar raha hai": steps ki list, kisi step par dabao to uski poori jaankari
object ActUi {
    @Volatile var refresh: (() -> Unit)? = null
    val WORDS = listOf("Thinking", "Mulling", "Weighing", "Pondering", "Cogitating", "Reasoning")

    fun ripple(c: Context, r: Int = 0): android.graphics.drawable.Drawable =
        RippleDrawable(ColorStateList.valueOf(Color.parseColor("#2EFFFFFF")), null, GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = r.toFloat() })

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun summaryLabel(steps: List<Act.Step>): String {
        val n = steps.size
        val what = when {
            steps.any { it.kind == 1 } -> "Searched the web"
            steps.any { it.kind == 2 } -> "Used phone actions"
            steps.any { it.kind == 4 } -> "Created media"
            else -> "Thought"
        }
        return what + " · " + n + (if (n == 1) " step" else " steps")
    }

    fun sheet(act: Activity, fixed: List<Act.Step>? = null) {
        val d = Dialog(act)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(act, 28).toFloat(), dp(act, 28).toFloat(), dp(act, 28).toFloat(), dp(act, 28).toFloat(), 0f, 0f, 0f, 0f)
                setColor(Color.parseColor("#141414"))
            }
            setPadding(dp(act, 18), dp(act, 10), dp(act, 18), dp(act, 20))
        }
        root.addView(View(act).apply {
            background = GradientDrawable().apply { cornerRadius = dp(act, 3).toFloat(); setColor(Color.parseColor("#3A3A3A")) }
        }, LinearLayout.LayoutParams(dp(act, 40), dp(act, 5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(act, 10) })

        fun close() {
            root.animate().translationY(root.height.toFloat()).setDuration(240).setInterpolator(android.view.animation.AccelerateInterpolator(1.4f))
                .withEndAction { if (d.isShowing) d.dismiss() }.start()
        }
        val head = FrameLayout(act)
        val btn = IconView(act)
        val title = TextView(act).apply { textSize = 17f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        head.addView(btn, FrameLayout.LayoutParams(dp(act, 44), dp(act, 44), Gravity.START or Gravity.CENTER_VERTICAL))
        head.addView(title, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(head, LinearLayout.LayoutParams(-1, dp(act, 48)))

        val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(act, 6), 0, dp(act, 10)) }
        root.addView(ScrollView(act).apply { isVerticalScrollBarEnabled = false; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))

        var openStep: Act.Step? = null
        lateinit var showList: () -> Unit

        fun steps(): List<Act.Step> = fixed ?: Act.steps

        fun mono(text: String) = TextView(act).apply {
            this.text = text
            textSize = 13.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#E8E8E8"))
            setTextIsSelectable(true)
            setPadding(dp(act, 14), dp(act, 12), dp(act, 14), dp(act, 12))
            background = GradientDrawable().apply { cornerRadius = dp(act, 14).toFloat(); setColor(Color.parseColor("#1E1E1E")); setStroke(dp(act, 1), Color.parseColor("#2C2C2C")) }
        }

        fun label(t: String) = TextView(act).apply {
            text = t; textSize = 15f; setTextColor(Color.parseColor("#9A9AA2"))
            setPadding(dp(act, 2), dp(act, 16), 0, dp(act, 8))
        }

        fun showDetail(s: Act.Step) {
            openStep = s
            title.text = s.title
            title.maxLines = 1
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            btn.kind = 23
            btn.rotation = 0f
            btn.setOnClickListener { openStep = null; showList() }
            body.removeAllViews()
            val st = when (s.state) { Act.RUN -> "Running"; Act.FAIL -> "Failed"; else -> "Done" }
            body.addView(label("Status"))
            body.addView(mono(st + "  ·  " + String.format("%.1f", s.secs()) + " s"))
            if (s.req.isNotBlank()) { body.addView(label("Request")); body.addView(mono(s.req)) }
            if (s.detail.isNotBlank()) { body.addView(label("Detail")); body.addView(mono(s.detail)) }
            if (s.res.isNotBlank()) { body.addView(label("Response")); body.addView(mono(s.res)) }
        }

        showList = {
            openStep = null
            title.text = "Summary"
            btn.kind = 1
            btn.rotation = 45f
            btn.setOnClickListener { close() }
            body.removeAllViews()
            val list = steps()
            if (list.isEmpty()) {
                body.addView(TextView(act).apply {
                    text = "Working..."; textSize = 16f; setTextColor(Color.parseColor("#9A9AA2")); setPadding(dp(act, 6), dp(act, 20), 0, 0)
                })
            }
            list.forEachIndexed { idx, s ->
                val firstRow = idx == 0
                val lastRow = idx == list.size - 1
                val running = s.state == Act.RUN
                val iconKind = when (s.kind) { 1 -> 48; 2 -> 49; else -> 0 }
                val row = LinearLayout(act).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = ripple(act, dp(act, 10))
                    minimumHeight = dp(act, 48)
                }
                val col = FrameLayout(act)
                col.addView(TimeCol(act, firstRow, lastRow), FrameLayout.LayoutParams(-1, -1))
                if (iconKind != 0) {
                    col.addView(FrameLayout(act).apply {
                        background = GradientDrawable().apply { cornerRadius = dp(act, 11).toFloat(); setColor(Color.parseColor("#232323")) }
                        addView(IconView(act).apply { kind = iconKind }, FrameLayout.LayoutParams(-1, -1))
                    }, FrameLayout.LayoutParams(dp(act, 36), dp(act, 36), Gravity.CENTER))
                } else if (running) {
                    col.addView(PulseDot(act), FrameLayout.LayoutParams(dp(act, 11), dp(act, 11), Gravity.CENTER))
                } else {
                    col.addView(View(act).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#4A4A4E")) } },
                        FrameLayout.LayoutParams(dp(act, 9), dp(act, 9), Gravity.CENTER))
                }
                row.addView(col, LinearLayout.LayoutParams(dp(act, 40), -1))
                val tx = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(act, 9), 0, dp(act, 9)) }
                val failed = s.state == Act.FAIL
                val tv: TextView
                if (running) {
                    tv = ShimmerText(act).apply { dim = Color.parseColor(if (iconKind != 0) "#C4C4CC" else "#A8A8B2") }
                    if (s.kind == 0) (tv as ShimmerText).cycle(WORDS) else tv.text = s.title
                } else {
                    tv = TextView(act)
                    tv.text = (if (failed) "Failed: " else "") + s.title + (if (failed) "  \u26A0" else "")
                }
                tv.textSize = 17f
                if (!running) tv.setTextColor(if (iconKind != 0) Color.WHITE else Color.parseColor("#D8D8DC"))
                if (iconKind != 0) tv.typeface = Typeface.DEFAULT_BOLD
                tx.addView(tv)
                if (s.detail.isNotBlank() && !(running && s.kind == 0)) tx.addView(TextView(act).apply {
                    text = s.detail.lines().first().take(110); textSize = 13.5f; setTextColor(Color.parseColor("#9A9AA2")); maxLines = 2
                })
                row.addView(tx, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(act, 6) })
                if (!running) row.addView(TextView(act).apply { text = String.format("%.1fs", s.secs()); textSize = 12.5f; setTextColor(Color.parseColor("#7A7A82")) })
                if (s.req.isNotBlank() || s.detail.isNotBlank() || s.res.isNotBlank()) row.setOnClickListener { showDetail(s) }
                body.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
        }

        showList()
        ActUi.refresh = { if (d.isShowing) { val o = openStep; if (o != null) showDetail(o) else showList() } }
        d.setOnDismissListener { ActUi.refresh = null }
        d.setContentView(root)
        // window ka naap content lagane ke BAAD, warna sheet patli patti ban jaati hai
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (act.resources.displayMetrics.heightPixels * 0.82f).toInt())
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val lp = attributes
            lp.dimAmount = 0.6f
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = (act.resources.displayMetrics.heightPixels * 0.82f).toInt()
            attributes = lp
        }
        root.translationY = act.resources.displayMetrics.heightPixels.toFloat()
        d.show()
        root.post {
            root.animate().translationY(0f).setDuration(320).setInterpolator(DecelerateInterpolator(1.8f)).start()
        }
    }
}
