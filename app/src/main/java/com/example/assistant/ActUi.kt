package com.example.assistant

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (act.resources.displayMetrics.heightPixels * 0.82f).toInt())
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.also { it.dimAmount = 0.6f }
        }
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
            btn.setOnClickListener { d.dismiss() }
            body.removeAllViews()
            val list = steps()
            if (list.isEmpty()) {
                body.addView(TextView(act).apply {
                    text = "Working..."; textSize = 16f; setTextColor(Color.parseColor("#9A9AA2")); setPadding(dp(act, 6), dp(act, 20), 0, 0)
                })
            }
            for (s in list) {
                val row = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(act, 12), 0, dp(act, 12)) }
                val iconKind = when (s.kind) { 1 -> 48; 2 -> 49; else -> 0 }
                if (iconKind != 0) {
                    row.addView(FrameLayout(act).apply {
                        background = GradientDrawable().apply { cornerRadius = dp(act, 12).toFloat(); setColor(Color.parseColor("#232323")) }
                        addView(IconView(act).apply { kind = iconKind }, FrameLayout.LayoutParams(-1, -1))
                    }, LinearLayout.LayoutParams(dp(act, 40), dp(act, 40)))
                } else {
                    row.addView(FrameLayout(act).apply {
                        addView(View(act).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#4A4A4A")) } },
                            FrameLayout.LayoutParams(dp(act, 9), dp(act, 9), Gravity.CENTER))
                    }, LinearLayout.LayoutParams(dp(act, 40), dp(act, 40)))
                }
                val tx = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                val failed = s.state == Act.FAIL
                tx.addView(TextView(act).apply {
                    text = (if (failed) "Failed: " else "") + s.title + (if (failed) "  ⚠" else "")
                    textSize = 17f
                    setTextColor(if (iconKind != 0) Color.WHITE else Color.parseColor("#D8D8DC"))
                    if (iconKind != 0) typeface = Typeface.DEFAULT_BOLD
                })
                if (s.detail.isNotBlank()) tx.addView(TextView(act).apply {
                    text = s.detail.lines().first().take(110); textSize = 13.5f; setTextColor(Color.parseColor("#9A9AA2")); maxLines = 2
                })
                row.addView(tx, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(act, 12) })
                if (s.state == Act.RUN) row.addView(DotsView(act), LinearLayout.LayoutParams(dp(act, 34), dp(act, 22)))
                else row.addView(TextView(act).apply { text = String.format("%.1fs", s.secs()); textSize = 12.5f; setTextColor(Color.parseColor("#7A7A82")) })
                if (s.req.isNotBlank() || s.detail.isNotBlank() || s.res.isNotBlank()) row.setOnClickListener { showDetail(s) }
                body.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
        }

        showList()
        ActUi.refresh = { if (d.isShowing) { val o = openStep; if (o != null) showDetail(o) else showList() } }
        d.setOnDismissListener { ActUi.refresh = null }
        d.setContentView(root)
        d.show()
    }
}
