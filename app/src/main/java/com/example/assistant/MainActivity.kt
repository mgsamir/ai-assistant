package com.example.assistant

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// Chhote line-icons: 0 menu, 1 plus, 2 mic, 3 wave, 4 send, 5 like, 6 dislike, 7 share, 8 copy, 9 more, 10 speaker
class IconView(ctx: Context) : View(ctx) {
    var kind = 0
        set(v) { field = v; invalidate() }
    var tint = Color.parseColor("#E8EAF0")
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.8f
    }

    override fun onDraw(c: Canvas) {
        val size = minOf(width, height) * 0.55f
        val u = size / 24f
        p.color = tint
        c.save()
        c.translate((width - size) / 2f, (height - size) / 2f)
        c.scale(u, u)
        when (kind) {
            0 -> { c.drawLine(4f, 9f, 20f, 9f, p); c.drawLine(4f, 15f, 20f, 15f, p) }
            11 -> {
                c.drawLine(4f, 8f, 20f, 8f, p); c.drawLine(4f, 16f, 20f, 16f, p)
                c.drawCircle(15f, 8f, 2.2f, p); c.drawCircle(9f, 16f, 2.2f, p)
            }
            26 -> {
                c.drawLine(3f, 12f, 9f, 12f, p); c.drawLine(9f, 12f, 14f, 6f, p); c.drawLine(9f, 12f, 14f, 18f, p)
                c.drawLine(14f, 6f, 21f, 6f, p); c.drawLine(14f, 18f, 21f, 18f, p)
                c.drawLine(18f, 3.5f, 21f, 6f, p); c.drawLine(21f, 6f, 18f, 8.5f, p)
                c.drawLine(18f, 15.5f, 21f, 18f, p); c.drawLine(21f, 18f, 18f, 20.5f, p)
            }
            29 -> { c.drawRoundRect(RectF(3f, 6f, 21f, 19f), 2f, 2f, p); c.drawLine(3f, 9f, 10f, 9f, p); c.drawLine(10f, 9f, 12f, 6f, p) }
            30 -> { c.drawRoundRect(RectF(6f, 3f, 18f, 21f), 2f, 2f, p); c.drawLine(9f, 9f, 15f, 9f, p); c.drawLine(9f, 13f, 15f, 13f, p); c.drawLine(9f, 17f, 13f, 17f, p) }
            27, 28 -> {
                c.drawCircle(12f, 12f, 9f, p); c.drawOval(RectF(8f, 3f, 16f, 21f), p); c.drawLine(3f, 12f, 21f, 12f, p)
                if (kind == 28) { c.drawLine(15f, 15f, 21f, 21f, p); c.drawLine(21f, 15f, 15f, 21f, p) }
            }
            25 -> {
                c.drawArc(RectF(4f, 4f, 20f, 20f), -60f, 290f, false, p)
                c.drawLine(17.5f, 2.5f, 17.5f, 7f, p); c.drawLine(17.5f, 7f, 13f, 7f, p)
            }
            1 -> { c.drawLine(12f, 5f, 12f, 19f, p); c.drawLine(5f, 12f, 19f, 12f, p) }
            2, 12 -> {
                c.drawRoundRect(RectF(9f, 3f, 15f, 14f), 3f, 3f, p)
                c.drawArc(RectF(5f, 4f, 19f, 18f), 0f, 180f, false, p)
                c.drawLine(12f, 18f, 12f, 21f, p)
                if (kind == 12) c.drawLine(4f, 3f, 20f, 21f, p)
            }
            3 -> {
                c.drawLine(8f, 9f, 8f, 15f, p)
                c.drawLine(12f, 5f, 12f, 19f, p)
                c.drawLine(16f, 9f, 16f, 15f, p)
            }
            4 -> {
                c.drawLine(12f, 19f, 12f, 5f, p)
                c.drawLine(6f, 11f, 12f, 5f, p)
                c.drawLine(12f, 5f, 18f, 11f, p)
            }
            5, 6 -> {
                if (kind == 6) c.rotate(180f, 12f, 12f)
                c.drawRect(4f, 11f, 7f, 20f, p)
                val t = Path()
                t.moveTo(7f, 11f)
                t.lineTo(10.5f, 4f)
                t.cubicTo(12.1f, 4f, 13.2f, 5.2f, 12.9f, 6.8f)
                t.lineTo(12.4f, 10f)
                t.lineTo(18f, 10f)
                t.quadTo(20.4f, 10f, 20.4f, 13f)
                t.lineTo(19.3f, 18.2f)
                t.quadTo(19f, 20f, 16.9f, 20f)
                t.lineTo(7f, 20f)
                c.drawPath(t, p)
            }
            7 -> {
                c.drawLine(12f, 15f, 12f, 4f, p)
                c.drawLine(8f, 8f, 12f, 4f, p)
                c.drawLine(12f, 4f, 16f, 8f, p)
                val t = Path()
                t.moveTo(5f, 12f)
                t.lineTo(5f, 18f)
                t.quadTo(5f, 20f, 7f, 20f)
                t.lineTo(17f, 20f)
                t.quadTo(19f, 20f, 19f, 18f)
                t.lineTo(19f, 12f)
                c.drawPath(t, p)
            }
            8 -> {
                c.drawRoundRect(RectF(9f, 9f, 20f, 20f), 2.5f, 2.5f, p)
                val t = Path()
                t.moveTo(15f, 9f)
                t.lineTo(15f, 6.5f)
                t.quadTo(15f, 4f, 12.5f, 4f)
                t.lineTo(6.5f, 4f)
                t.quadTo(4f, 4f, 4f, 6.5f)
                t.lineTo(4f, 12.5f)
                t.quadTo(4f, 15f, 6.5f, 15f)
                t.lineTo(9f, 15f)
                c.drawPath(t, p)
            }
            9 -> {
                p.style = Paint.Style.FILL
                c.drawCircle(5f, 12f, 1.7f, p)
                c.drawCircle(12f, 12f, 1.7f, p)
                c.drawCircle(19f, 12f, 1.7f, p)
                p.style = Paint.Style.STROKE
            }
            13, 17 -> {
                c.drawRoundRect(RectF(3f, 4f, 21f, 20f), 3f, 3f, p)
                c.drawCircle(9f, 10f, 2f, p)
                val t = Path()
                t.moveTo(3f, 17f); t.lineTo(9f, 12.5f); t.lineTo(14f, 17f); t.lineTo(17f, 14.5f); t.lineTo(21f, 18f)
                c.drawPath(t, p)
                if (kind == 17) { c.drawLine(17f, 6.5f, 17f, 10.5f, p); c.drawLine(15f, 8.5f, 19f, 8.5f, p) }
            }
            14 -> {
                c.drawRoundRect(RectF(3f, 7f, 21f, 20f), 3f, 3f, p)
                c.drawCircle(12f, 13.5f, 3.6f, p)
                c.drawLine(8f, 7f, 9.5f, 4f, p); c.drawLine(9.5f, 4f, 14.5f, 4f, p); c.drawLine(14.5f, 4f, 16f, 7f, p)
            }
            15 -> {
                val t = Path()
                t.moveTo(6f, 3f); t.lineTo(14f, 3f); t.lineTo(19f, 8f); t.lineTo(19f, 21f); t.lineTo(6f, 21f); t.close()
                c.drawPath(t, p)
                c.drawLine(14f, 3f, 14f, 8f, p); c.drawLine(14f, 8f, 19f, 8f, p)
            }
            16 -> {
                val t = Path()
                t.moveTo(3f, 6f); t.lineTo(9f, 4f); t.lineTo(15f, 6f); t.lineTo(21f, 4f)
                t.lineTo(21f, 18f); t.lineTo(15f, 20f); t.lineTo(9f, 18f); t.lineTo(3f, 20f); t.close()
                c.drawPath(t, p)
                c.drawLine(9f, 4f, 9f, 18f, p); c.drawLine(15f, 6f, 15f, 20f, p)
            }
            18 -> {
                c.drawRoundRect(RectF(3f, 5f, 21f, 19f), 3f, 3f, p)
                val t = Path()
                t.moveTo(10f, 9f); t.lineTo(10f, 15f); t.lineTo(15f, 12f); t.close()
                c.drawPath(t, p)
            }
            19 -> {
                c.drawLine(9f, 17f, 9f, 5f, p); c.drawLine(9f, 5f, 19f, 3f, p); c.drawLine(19f, 3f, 19f, 15f, p)
                c.drawCircle(7f, 17f, 2.2f, p); c.drawCircle(17f, 15f, 2.2f, p)
            }
            20 -> {
                c.drawCircle(12f, 12f, 1.8f, p)
                for (ang in listOf(0f, 60f, 120f)) { c.save(); c.rotate(ang, 12f, 12f); c.drawOval(RectF(3f, 8f, 21f, 16f), p); c.restore() }
            }
            21 -> {
                val l = Path()
                l.moveTo(12f, 6f); l.quadTo(8f, 4f, 3f, 5f); l.lineTo(3f, 18f); l.quadTo(8f, 17f, 12f, 19.5f)
                val r = Path()
                r.moveTo(12f, 6f); r.quadTo(16f, 4f, 21f, 5f); r.lineTo(21f, 18f); r.quadTo(16f, 17f, 12f, 19.5f)
                c.drawPath(l, p); c.drawPath(r, p); c.drawLine(12f, 6f, 12f, 19.5f, p)
            }
            22 -> {
                c.drawCircle(10f, 8f, 3.2f, p)
                c.drawArc(RectF(3f, 14f, 17f, 28f), 180f, 180f, false, p)
                c.drawLine(19f, 4f, 19f, 9f, p); c.drawLine(16.5f, 6.5f, 21.5f, 6.5f, p)
            }
            23 -> { c.drawLine(19f, 12f, 5f, 12f, p); c.drawLine(11f, 6f, 5f, 12f, p); c.drawLine(5f, 12f, 11f, 18f, p) }
            else -> {
                val t = Path()
                t.moveTo(4f, 10f)
                t.lineTo(4f, 14f)
                t.lineTo(7f, 14f)
                t.lineTo(12f, 18f)
                t.lineTo(12f, 6f)
                t.lineTo(7f, 10f)
                t.close()
                c.drawPath(t, p)
                c.drawArc(RectF(10f, 8.5f, 17f, 15.5f), -50f, 100f, false, p)
                c.drawArc(RectF(6.5f, 5f, 20.5f, 19f), -45f, 90f, false, p)
            }
        }
        c.restore()
    }
}

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var input: EditText
    private lateinit var voiceBtn: IconView
    private lateinit var chatList: LinearLayout
    private lateinit var chatScroll: ScrollView
    private var pending: View? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // Engine se aane wali baat-cheet screen par dikhao
    private val cb = object : UiCb {
        override fun onStatus(s: String) {}
        override fun onChat(s: String) { runOnUiThread { showChat(s) } }
        override fun onMode(m: Int) { runOnUiThread { orb.mode = m; orbStatus.text = orbLabel(m) } }
        override fun onLevel(l: Float) { orb.level = l }
    }

    private fun showChat(raw: String) {
        val t = raw.trim()
        if (t.startsWith("Tum:")) {
            addUser(t.removePrefix("Tum:").trim())
            addPending()
            if (::orbList.isInitialized) orbAdd(true, t.removePrefix("Tum:").trim())
        } else if (t.startsWith("AI:")) {
            removePending()
            val body = t.removePrefix("AI:").trim()
            val who = body.substringAfterLast('\u2063', "")
            val txt = body.substringBeforeLast('\u2063').trim()
            addAi(txt, who)
            if (::orbList.isInitialized) orbAdd(false, txt)
        }
    }

    // ---- orb screen ki baat-cheet (ChatGPT jaisi) ----
    private fun orbAdd(mine: Boolean, text: String) {
        if (text.isEmpty()) return
        if (mine) {
            val row = LinearLayout(this).apply { gravity = Gravity.END }
            row.addView(TextView(this).apply {
                this.text = text
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(dp(18), dp(12), dp(18), dp(12))
                maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(26).toFloat()
                    setColor(Color.parseColor("#0A0A6B"))
                }
            })
            orbList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = dp(10) })
        } else {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            box.addView(TextView(this).apply {
                this.text = text
                textSize = 16.5f
                setTextColor(Color.parseColor("#F0F0F0"))
                setLineSpacing(0f, 1.25f)
            })
            val acts = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            acts.addView(icon(8, 40) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("AI", text))
                Toast.makeText(this, "Copy ho gaya", Toast.LENGTH_SHORT).show()
            })
            acts.addView(icon(7, 40) {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(i, null))
            })
            box.addView(acts, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); leftMargin = -dp(10) })
            orbList.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(10) })
        }
        orbScroll.post { orbScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun scrollDown() {
        chatScroll.post { chatScroll.scrollTo(0, chatList.height) }
    }

    // Meri baat: daayin taraf, gol bubble
    private var lastUserText = ""
    private var lastExtraViews: Pair<View, View>? = null
    private var lastBox: View? = null
    private var aiCount = 0

    private fun addUser(text: String) {
        lastUserText = text
        val row = LinearLayout(this).apply { gravity = Gravity.END }
        val tv = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.parseColor("#E8EAF0"))
            setPadding(dp(20), dp(14), dp(20), dp(14))
            maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(Color.parseColor("#171717"))
            }
        }
        row.addView(tv)
        chatList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
            bottomMargin = dp(10)
        })
        scrollDown()
    }

    private fun addPending() {
        val tv = TextView(this).apply {
            text = "..."
            textSize = 22f
            setTextColor(Color.parseColor("#9AA3B5"))
        }
        pending = tv
        chatList.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scrollDown()
    }

    private fun removePending() {
        pending?.let { chatList.removeView(it) }
        pending = null
    }

    // AI ka jawab: baayin taraf, bina bubble, neeche chhote buttons
    private fun addAi(text: String, who: String = "") {
        lastExtraViews?.let { it.first.visibility = View.GONE; it.second.visibility = View.GONE }
        lastExtraViews = null
        val myIdx = aiCount++
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tv = TextView(this).apply {
            this.text = styled(text)
            textSize = 16.5f
            setTextColor(Color.parseColor("#E8EAF0"))
            setLineSpacing(0f, 1.3f)
            setTextIsSelectable(true)
            isFocusable = false
            isFocusableInTouchMode = false
        }
        box.addView(tv)

        val acts = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val like = icon(5, 44) { }
        val dis = icon(6, 44) { }
        like.setOnClickListener {
            val on = like.tint != Color.parseColor("#4F8BFF")
            like.tint = if (on) Color.parseColor("#4F8BFF") else Color.parseColor("#E8EAF0")
            dis.tint = Color.parseColor("#E8EAF0")
        }
        dis.setOnClickListener {
            val on = dis.tint != Color.parseColor("#4F8BFF")
            dis.tint = if (on) Color.parseColor("#4F8BFF") else Color.parseColor("#E8EAF0")
            like.tint = Color.parseColor("#E8EAF0")
        }
        acts.addView(like)
        acts.addView(dis)
        acts.addView(icon(7, 44) {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            startActivity(Intent.createChooser(i, null))
        })
        acts.addView(icon(8, 44) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AI", text))
            Toast.makeText(this, "Copy ho gaya", Toast.LENGTH_SHORT).show()
        })
        val regen = icon(25, 44) {
            if (lastUserText.isNotEmpty()) {
                if (orbScreen.visibility == View.VISIBLE && !orbChat) toggleOrbChat()
                AssistantService.engine?.sendText(lastUserText, false, null)
            }
        }
        acts.addView(regen, 2)
        val more = icon(9, 44) { }
        more.setOnClickListener { showMsgMenu(more, text, myIdx, regen) }
        acts.addView(more)
        val srcs = AssistantService.engine?.sourcesFor?.get(text)
        if (srcs != null && srcs.isNotEmpty()) {
            val pill = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(6), dp(12), dp(6))
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.parseColor("#2A2A2A")) }
                setOnClickListener { showSources(srcs) }
            }
            pill.addView(IconView(this).apply { kind = 27; tint = Color.parseColor("#E8EAF0") }, LinearLayout.LayoutParams(dp(30), dp(24)))
            pill.addView(TextView(this).apply { this.text = "Sources"; textSize = 13.5f; setTextColor(Color.parseColor("#E8EAF0")) })
            acts.addView(pill, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) })
        }
        acts.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        acts.addView(icon(10, 44) { AssistantService.engine?.replay(text) })
        box.addView(acts, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            leftMargin = -dp(10)
            rightMargin = dp(4)
        })
        val foot = TextView(this).apply {
            this.text = if (who.isNotEmpty()) "$who \u00B7 AI can make mistakes." else "AI can make mistakes."
            textSize = 12.5f
            setTextColor(Color.parseColor("#8A93A6"))
        }
        box.addView(foot, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2); bottomMargin = dp(2) })
        // sirf sabse nayi baat par: dobara jawab ka button aur neeche model ka naam
        lastExtraViews = Pair(regen, foot)
        lastBox = box
        chatList.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            bottomMargin = dp(6)
        })
        scrollDown()
    }

    private fun menuRow(kind: Int, label: String, go: () -> Unit): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(8), dp(14), dp(16), dp(14))
        r.addView(IconView(this).apply { this.kind = kind }, LinearLayout.LayoutParams(dp(44), dp(30)))
        r.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(Color.WHITE) })
        r.setOnClickListener { go() }
        return r
    }

    // "..." dabane par: Branch in new chat / Retry / Search the web
    private fun showMsgMenu(anchor: View, text: String, idx: Int, regen: View) {
        val latest = lastExtraViews?.first === regen
        val e = AssistantService.engine
        val hasWeb = e?.sourcesFor?.containsKey(text) == true
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(Color.parseColor("#2B2B2B")) }
        }
        val pw = android.widget.PopupWindow(col, dp(290), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pw.elevation = dp(8).toFloat()
        pw.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        col.addView(TextView(this).apply {
            this.text = "Today, " + java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(java.util.Date()).lowercase()
            textSize = 15f; setTextColor(Color.parseColor("#9AA3B5")); setPadding(dp(8), dp(8), 0, dp(6))
        })
        col.addView(menuRow(26, "Branch in new chat") { pw.dismiss(); branchFrom(idx) })
        if (latest && e != null) {
            col.addView(View(this).apply { setBackgroundColor(Color.parseColor("#444444")) }, LinearLayout.LayoutParams(-1, dp(1)).apply { leftMargin = dp(8); rightMargin = dp(8) })
            col.addView(menuRow(25, "Retry") { pw.dismiss(); retryLast(null) })
            if (hasWeb) col.addView(menuRow(28, "Remove web results") { pw.dismiss(); retryLast(false) })
            else col.addView(menuRow(27, "Search the web") { pw.dismiss(); retryLast(true) })
        }
        pw.showAsDropDown(anchor, -dp(20), -dp(10))
    }

    private fun retryLast(web: Boolean?) {
        val e = AssistantService.engine ?: return
        lastBox?.let { chatList.removeView(it) }
        lastBox = null
        lastExtraViews = null
        if (aiCount > 0) aiCount--
        addPending()
        e.regen(web)
    }

    private fun showSources(list: List<Triple<String, String, String>>) {
        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(10))
            background = GradientDrawable().apply {
                val r = dp(26).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(Color.parseColor("#2B2B2B"))
            }
        }
        col.addView(View(this).apply { background = GradientDrawable().apply { cornerRadius = dp(3).toFloat(); setColor(Color.parseColor("#666666")) } },
            LinearLayout.LayoutParams(dp(40), dp(5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(12) })
        col.addView(TextView(this).apply { text = "Sources"; textSize = 14f; setTextColor(Color.parseColor("#9AA3B5")); setPadding(0, 0, 0, dp(8)) })
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((title, url, desc) in list) {
            val host = try { android.net.Uri.parse(url).host?.removePrefix("www.") ?: "" } catch (e: Exception) { "" }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (e: Exception) { } }
            }
            row.addView(TextView(this).apply { text = host; textSize = 14f; setTextColor(Color.parseColor("#C8CCD6")) })
            row.addView(TextView(this).apply { text = title; textSize = 17f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            row.addView(TextView(this).apply { text = desc; textSize = 14.5f; setTextColor(Color.parseColor("#9AA3B5")); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            inner.addView(row)
            inner.addView(View(this).apply { setBackgroundColor(Color.parseColor("#3A3A3A")) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        col.addView(ScrollView(this).apply { addView(inner) }, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.6f).toInt()))
        dlg.setContentView(col)
        dlg.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        dlg.show()
    }

    // ---- purani baat-cheet ka sangrah: Branch in new chat aur Chat history ----
    private fun archiveChat(text: String) {
        try {
            val parts = text.split(Regex("\n(?=(?:Tum|AI): )")).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.none { it.startsWith("AI:") }) return
            val title = parts.firstOrNull { it.startsWith("Tum:") }?.removePrefix("Tum:")?.trim()?.take(40) ?: "Chat"
            val sp = getSharedPreferences("p", Context.MODE_PRIVATE)
            val arr = org.json.JSONArray(sp.getString("chats", "[]"))
            val out = org.json.JSONArray()
            out.put(org.json.JSONObject().put("t", title).put("time", System.currentTimeMillis()).put("log", text))
            for (i in 0 until minOf(arr.length(), 29)) out.put(arr.getJSONObject(i))
            sp.edit().putString("chats", out.toString()).apply()
        } catch (e: Exception) { }
    }

    private fun branchFrom(idx: Int) {
        val e = AssistantService.engine ?: return
        val full = e.log.toString()
        val parts = full.split(Regex("\n(?=(?:Tum|AI): )")).map { it.trim() }.filter { it.isNotEmpty() }
        val keep = ArrayList<String>()
        var n = -1
        for (p in parts) {
            keep.add(p)
            if (p.startsWith("AI:")) { n++; if (n == idx) break }
        }
        archiveChat(full)
        val sub = keep.joinToString("") { "\n$it\n" }
        e.loadChat(sub)
        rebuildChat(sub)
        Toast.makeText(this, "New chat started from this message", Toast.LENGTH_SHORT).show()
    }

    // poori screen wala pannaa: peechhe ka header jhalakna band
    private fun fullDlg(): android.app.Dialog {
        val d = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        d.window?.let { w ->
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w.setGravity(Gravity.TOP or Gravity.START)
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            val lp = w.attributes
            lp.x = 0; lp.y = 0
            if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            w.attributes = lp
            if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false)
            w.statusBarColor = Color.BLACK
            w.navigationBarColor = Color.BLACK
        }
        return d
    }

    private fun showChatHistory() {
        val sp = getSharedPreferences("p", Context.MODE_PRIVATE)
        val arr = try { org.json.JSONArray(sp.getString("chats", "[]")) } catch (e: Exception) { org.json.JSONArray() }
        val dlg = fullDlg()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(dp(16), dp(48), dp(16), dp(16)) }
        val top = FrameLayout(this)
        val back = icon(23, 40) { dlg.dismiss() }
        back.background = oval("#303030")
        top.addView(back, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.CENTER_VERTICAL))
        top.addView(TextView(this).apply { text = "Chat history"; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(top, LinearLayout.LayoutParams(-1, dp(56)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (arr.length() == 0) list.addView(TextView(this).apply { text = "No saved chats yet. A chat is saved when you branch it."; textSize = 15f; setTextColor(Color.parseColor("#9AA3B5")); setPadding(dp(4), dp(20), dp(4), 0) })
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(8), dp(14))
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.parseColor("#1E1E1E")) }
            }
            val tx = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            tx.addView(TextView(this).apply { text = o.optString("t"); textSize = 16f; setTextColor(Color.WHITE); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            tx.addView(TextView(this).apply {
                text = java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(o.optLong("time")))
                textSize = 13f; setTextColor(Color.parseColor("#9AA3B5"))
            })
            row.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply {
                text = "Delete"; textSize = 14f; setTextColor(Color.parseColor("#FF8A8A")); setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener {
                    android.app.AlertDialog.Builder(this@MainActivity).setTitle("Delete this chat?")
                        .setPositiveButton("Delete") { _, _ ->
                            val keepArr = org.json.JSONArray()
                            for (k in 0 until arr.length()) if (k != i) keepArr.put(arr.getJSONObject(k))
                            sp.edit().putString("chats", keepArr.toString()).apply()
                            dlg.dismiss(); showChatHistory()
                        }.setNegativeButton("Cancel", null).show()
                }
            })
            row.setOnClickListener {
                val e = AssistantService.engine
                if (e != null) {
                    archiveChat(e.log.toString())
                    val lg = o.optString("log")
                    e.loadChat(lg)
                    rebuildChat(lg)
                }
                dlg.dismiss()
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        dlg.setContentView(root)
        dlg.show()
    }

    // jawab ko saaf, line-by-line dikhao: heading mote akshar mein, 1. 2. 3. alag-alag line par
    private fun styled(t: String): CharSequence {
        val sb = android.text.SpannableStringBuilder()
        val lines = t.split("\n")
        for ((i, raw) in lines.withIndex()) {
            val l = raw.trimEnd()
            val st = sb.length
            sb.append(l)
            val head = l.isNotBlank() && l.endsWith(":") && l.length < 70 && !l.startsWith("-") && !l[0].isDigit()
            if (head) {
                sb.setSpan(android.text.style.StyleSpan(Typeface.BOLD), st, sb.length, 0)
                sb.setSpan(android.text.style.RelativeSizeSpan(1.12f), st, sb.length, 0)
            } else if (Regex("^(\\d+[.)]|-)\\s").containsMatchIn(l)) {
                val n = l.indexOf(' ')
                sb.setSpan(android.text.style.StyleSpan(Typeface.BOLD), st, st + n, 0)
                sb.setSpan(android.text.style.LeadingMarginSpan.Standard(0, dp(22)), st, sb.length, 0)
            }
            if (i < lines.size - 1) sb.append("\n")
        }
        return sb
    }

    // Purani baat-cheet dobara dikhao (ऐप wapas kholne par)
    private fun rebuildChat(log: String) {
        chatList.removeAllViews()
        pending = null
        aiCount = 0
        lastBox = null
        lastExtraViews = null
        for (part in log.split(Regex("\n(?=(?:Tum|AI): )"))) {
            val t = part.trim()
            if (t.startsWith("Tum:")) addUser(t.removePrefix("Tum:").trim())
            else if (t.startsWith("AI:")) {
                val body = t.removePrefix("AI:").trim()
                addAi(body.substringBeforeLast('\u2063').trim(), body.substringAfterLast('\u2063', ""))
            }
        }
    }


    // ---------- "+" menu: photo, camera, file, map, image/video/music banana ----------
    private var pendAtt: Pair<String, String>? = null
    private var pendName = ""
    private var pendMode = ""
    private var camUri: android.net.Uri? = null
    private var player: android.media.MediaPlayer? = null

    private fun clearMode() {
        if (pendMode == "learn") AssistantService.engine?.learn = false
        pendMode = ""
    }

    private lateinit var chipRow: LinearLayout
    private lateinit var pillRow: LinearLayout

    private fun refreshHints() {
        val t = when {
            pendMode == "image" -> "Describe the image"
            pendMode == "research" -> "What do you want to research?"
            pendMode == "learn" -> "What do you want to learn?"
            pendMode == "agent" -> "What should the agent do?"
            pendAtt != null -> "\uD83D\uDCCE " + pendName + " - ask a question"
            else -> "Ask AI"
        }
        input.hint = t
        if (::orbInput.isInitialized) orbInput.hint = t
        if (!::chipRow.isInitialized) return
        chipRow.removeAllViews()
        val label = when (pendMode) { "research" -> "Research"; "learn" -> "Learn"; "agent" -> "Agent"; "image" -> "Create image"; else -> "" }
        chipRow.visibility = if (label.isEmpty()) View.GONE else View.VISIBLE
        if (label.isNotEmpty()) {
            val c = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(6), dp(8), dp(6))
                background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.parseColor("#2A2A2A")) }
            }
            c.addView(TextView(this).apply { text = label; textSize = 15f; setTextColor(Color.WHITE) })
            c.addView(TextView(this).apply {
                text = "\u2715"; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                setOnClickListener { clearMode(); refreshHints() }
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { leftMargin = dp(8) })
            chipRow.addView(c)
        }
        pillRow.removeAllViews()
        pillRow.visibility = if (pendMode == "research") View.VISIBLE else View.GONE
        if (pendMode == "research") {
            val p = TextView(this).apply {
                text = "\u2B06  Files"; textSize = 15f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(18), dp(10), dp(18), dp(10))
                background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.parseColor("#444444")); setColor(Color.TRANSPARENT) }
                setOnClickListener { pick(103) }
            }
            pillRow.addView(p)
        }
    }

    private fun showPlus() {
        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(26))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(Color.parseColor("#1E1E1E"))
            }
        }
        col.addView(View(this).apply {
            background = GradientDrawable().apply { cornerRadius = dp(3).toFloat(); setColor(Color.parseColor("#555555")) }
        }, LinearLayout.LayoutParams(dp(40), dp(5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(14) })

        fun chip(kind: Int, label: String, go: () -> Unit): View {
            val b = LinearLayout(this)
            b.orientation = LinearLayout.VERTICAL
            b.gravity = Gravity.CENTER
            b.background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.parseColor("#2A2A2A")) }
            val ic = IconView(this).apply { this.kind = kind }
            b.addView(ic, LinearLayout.LayoutParams(dp(56), dp(44)))
            b.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(Color.parseColor("#E8EAF0")); gravity = Gravity.CENTER })
            b.setOnClickListener { dlg.dismiss(); go() }
            return b
        }
        val row = LinearLayout(this)
        val chips = listOf(
            chip(14, "Camera") { camera() },
            chip(13, "Photos") { pick(101) },
            chip(15, "Files") { pick(103) }
        )
        for (c in chips) row.addView(c, LinearLayout.LayoutParams(0, dp(92), 1f).apply { rightMargin = dp(8) })
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        fun item(kind: Int, title: String, sub: String, state: String? = null, go: () -> Unit): View {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(6), dp(12), dp(6), dp(12))
            r.addView(IconView(this).apply { this.kind = kind }, LinearLayout.LayoutParams(dp(48), dp(40)))
            val tx = LinearLayout(this)
            tx.orientation = LinearLayout.VERTICAL
            tx.addView(TextView(this).apply { text = title; textSize = 17f; setTextColor(Color.WHITE) })
            tx.addView(TextView(this).apply { text = sub; textSize = 14f; setTextColor(Color.parseColor("#9AA3B5")) })
            r.addView(tx, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
            if (state != null) r.addView(TextView(this).apply {
                text = state
                textSize = 13f
                setTextColor(if (state == "ON") Color.WHITE else Color.parseColor("#9AA3B5"))
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor(if (state == "ON") "#2F6BFF" else "#2A2A2A")) }
            })
            r.setOnClickListener { dlg.dismiss(); go() }
            return r
        }
        val eng = AssistantService.engine
        col.addView(item(17, "Create images", "Visualise and edit") { setMode("image") })
        col.addView(item(20, "Deep Research", "Get detailed reports") { setMode("research") })
        col.addView(item(20, "Agent", "Let the agent do multi-step work") { setMode("agent") })
        col.addView(item(21, "Guided Learning", "Get step-by-step help") { setMode("learn") })
        col.addView(item(22, "Personal Intelligence", "Personalise chat if helpful", if (eng?.personal != false) "ON" else "OFF") {
            val e = AssistantService.engine
            if (e != null) {
                e.personal = !e.personal
                Toast.makeText(this, if (e.personal) "Personal Intelligence on" else "Personal Intelligence off", Toast.LENGTH_SHORT).show()
            }
        })
        if (pendAtt != null || pendMode.isNotEmpty()) {
            col.addView(item(1, "Remove", "Remove what is attached") { pendAtt = null; clearMode(); refreshHints() })
        }
        dlg.setContentView(col)
        dlg.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        dlg.show()
    }

    // Google Earth browser mein khulta hai
    private fun showEarth(place: String? = null) {
        val url = if (place.isNullOrBlank()) "https://earth.google.com/web/@10.59,92.55,0a,60000d,35y,0h,0t,0r"
            else "https://earth.google.com/web/search/" + android.net.Uri.encode(place)
        try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        catch (e: Exception) { Toast.makeText(this, "Browser nahi mila", Toast.LENGTH_SHORT).show() }
    }

    private fun openEarthDirect() = showEarth(null)


    private fun setMode(m: String) {
        if (pendMode == "learn" && m != "learn") AssistantService.engine?.learn = false
        pendMode = m
        if (m == "learn") AssistantService.engine?.learn = true
        refreshHints()
        val v = if (orbScreen.visibility == View.VISIBLE) orbInput else input
        v.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(v, 0)
    }

    @Suppress("DEPRECATION")
    private fun pick(code: Int) {
        try {
            val i = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(if (code == 101) "image/*" else "*/*")
            startActivityForResult(i, code)
        } catch (e: Exception) {
            Toast.makeText(this, "Ye khul nahi paaya", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun camera() {
        try {
            val i = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "photo_" + System.currentTimeMillis() + ".jpg")
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/AI Assistant")
                }
                camUri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, camUri)
            } else camUri = null
            startActivityForResult(i, 102)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera khul nahi paaya", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        if (rs != RESULT_OK) {
            if (rq == 102) camUri?.let { try { contentResolver.delete(it, null, null) } catch (e: Exception) { } }
            return
        }
        kotlin.concurrent.thread {
            try {
                when (rq) {
                    101, 103 -> d?.data?.let { readPicked(it) }
                    102 -> {
                        val u = camUri
                        if (u != null) readPicked(u, "image/jpeg")
                        else (d?.extras?.get("data") as? android.graphics.Bitmap)?.let { setAtt(it, "photo") }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Ye file nahi khul paayi", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun setAtt(bm: android.graphics.Bitmap, name: String) {
        var b = bm
        val mx = 1280
        if (maxOf(b.width, b.height) > mx) {
            val s = mx.toFloat() / maxOf(b.width, b.height)
            b = android.graphics.Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
        }
        val bo = java.io.ByteArrayOutputStream()
        b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bo)
        val b64 = android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP)
        val icb = imgAttCb
        if (icb != null) { runOnUiThread { icb(Pair("image/jpeg", b64)) }; return }
        pendAtt = Pair("image/jpeg", b64)
        pendName = name
        runOnUiThread {
            refreshHints()
            if (orbScreen.visibility == View.VISIBLE) closeOrb()
            Toast.makeText(this, "Photo jud gayi, ab sawal likho", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readPicked(u: android.net.Uri, forceMime: String? = null) {
        var name = "file"
        try {
            contentResolver.query(u, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val ix = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (ix >= 0) name = it.getString(ix) ?: name
                }
            }
        } catch (e: Exception) { }
        val mime = forceMime ?: contentResolver.getType(u) ?: "application/octet-stream"
        if (mime.startsWith("image/")) {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / s > 2400) s *= 2
            val o2 = android.graphics.BitmapFactory.Options().apply { inSampleSize = s }
            val bm = contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o2) }
            if (bm != null) { setAtt(bm, name); return }
        }
        val bytes = contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return
        if (bytes.size > 8_000_000) {
            runOnUiThread { Toast.makeText(this, "File bahut badi hai (8 MB se chhoti chahiye)", Toast.LENGTH_LONG).show() }
            return
        }
        val ok = mime == "application/pdf" || mime.startsWith("audio/") || mime.startsWith("video/") || mime.startsWith("text/")
        pendAtt = Pair(if (ok) mime else "text/plain", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        pendName = name
        runOnUiThread {
            refreshHints()
            if (orbScreen.visibility == View.VISIBLE) closeOrb()
            Toast.makeText(this, "File jud gayi, ab sawal likho", Toast.LENGTH_SHORT).show()
        }
    }

    // Map / image / video / music wale mode mein bheja gaya text
    private fun runSpecial(t: String): Boolean {
        val mode = pendMode
        if (mode.isEmpty() || mode == "learn") return false
        if (mode == "map") {
            pendMode = ""
            refreshHints()
            showChat("Tum: $t")
            removePending()
            val msg = "Maps mein khol raha hoon."
            addAi(msg)
            if (::orbList.isInitialized) orbAdd(false, msg)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(t))))
            } catch (e: Exception) {
                Toast.makeText(this, "Maps app nahi mila", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        if (mode == "earth") {
            pendMode = ""
            refreshHints()
            showChat("Tum: $t")
            removePending()
            val msg = "Google Earth browser mein khol raha hoon."
            addAi(msg)
            if (::orbList.isInitialized) orbAdd(false, msg)
            AssistantService.engine?.openEarth(t)
            return true
        }
        if (mode == "agent") {
            pendMode = ""
            refreshHints()
            if (orbScreen.visibility == View.VISIBLE && !orbChat) toggleOrbChat()
            showChat("Tum: $t")
            Toast.makeText(this, "Agent ko kaam mein kuch minute lag sakte hain", Toast.LENGTH_LONG).show()
            val e = AssistantService.engine
            if (e == null) { removePending(); return true }
            e.agent(t) { rep ->
                runOnUiThread {
                    removePending()
                    addAi(rep)
                    if (::orbList.isInitialized) orbAdd(false, rep)
                }
            }
            return true
        }
        if (mode == "research") {
            pendMode = ""
            refreshHints()
            if (orbScreen.visibility == View.VISIBLE && !orbChat) toggleOrbChat()
            showChat("Tum: $t")
            Toast.makeText(this, "Report banne mein 1-2 minute lagte hain", Toast.LENGTH_LONG).show()
            val e = AssistantService.engine
            if (e == null) { removePending(); return true }
            e.research(t) { rep ->
                runOnUiThread {
                    removePending()
                    addAi(rep)
                    if (::orbList.isInitialized) orbAdd(false, rep)
                }
            }
            return true
        }
        val key = Keys.get(getSharedPreferences("p", Context.MODE_PRIVATE))
        val att = pendAtt
        pendAtt = null
        pendMode = ""
        refreshHints()
        if (orbScreen.visibility == View.VISIBLE && !orbChat) toggleOrbChat()
        showChat("Tum: $t")
        if (key.isEmpty()) { finishMedia(null, mode, "API key nahi mili"); return true }
        Toast.makeText(this, if (mode == "image") "Ban rahi hai..." else "Ban raha hai, 1-2 minute lag sakte hain", Toast.LENGTH_LONG).show()
        kotlin.concurrent.thread {
            val out = when (mode) {
                "image" -> Media.image(key, t, att, Models.sel(getSharedPreferences("p", Context.MODE_PRIVATE), "photo"))
                "video" -> Media.video(key, t, Models.sel(getSharedPreferences("p", Context.MODE_PRIVATE), "video"))
                else -> Media.music(key, t, Models.sel(getSharedPreferences("p", Context.MODE_PRIVATE), "music"))
            }
            val err = Media.lastErr
            runOnUiThread { finishMedia(out, mode, err) }
        }
        return true
    }

    private fun finishMedia(out: Media.Out?, kind: String, err: String) {
        removePending()
        if (out == null) {
            val t = "Nahi ban paaya. " + err.ifBlank { "Dobara try karo." }
            addAi(t)
            if (::orbList.isInitialized) orbAdd(false, t)
            return
        }
        val uri = saveMedia(out, kind)
        if (kind == "image") saveGenerated(out)
        chatList.addView(mediaView(out, kind, uri), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
        scrollDown()
        if (::orbList.isInitialized) {
            orbList.addView(mediaView(out, kind, uri), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
            orbScroll.post { orbScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun saveMedia(out: Media.Out, kind: String): android.net.Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val ext = when {
                out.mime.contains("png") -> "png"
                out.mime.contains("jpeg") || out.mime.contains("jpg") -> "jpg"
                out.mime.contains("webp") -> "webp"
                out.mime.contains("wav") -> "wav"
                kind == "video" -> "mp4"
                kind == "music" -> "mp3"
                else -> "jpg"
            }
            val (col, dir) = when (kind) {
                "video" -> Pair(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, android.os.Environment.DIRECTORY_MOVIES)
                "music" -> Pair(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, android.os.Environment.DIRECTORY_MUSIC)
                else -> Pair(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, android.os.Environment.DIRECTORY_PICTURES)
            }
            val cv = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, kind + "_" + System.currentTimeMillis() + "." + ext)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, out.mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, dir + "/AI Assistant")
            }
            val u = contentResolver.insert(col, cv)
            if (u != null) contentResolver.openOutputStream(u)?.use { it.write(out.bytes) }
            u
        } catch (e: Exception) { null }
    }

    private fun mediaView(out: Media.Out, kind: String, uri: android.net.Uri?): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        fun btn(label: String): TextView = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.parseColor("#2A2A2A")) }
        }
        when (kind) {
            "image" -> {
                val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                val bm = android.graphics.BitmapFactory.decodeByteArray(out.bytes, 0, out.bytes.size, o)
                val iv = android.widget.ImageView(this)
                iv.setImageBitmap(bm)
                iv.adjustViewBounds = true
                iv.maxWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
                iv.clipToOutline = true
                iv.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, ol: android.graphics.Outline) { ol.setRoundRect(0, 0, v.width, v.height, dp(18).toFloat()) }
                }
                iv.setOnClickListener {
                    if (uri != null) try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, out.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } catch (e: Exception) { }
                }
                box.addView(iv, LinearLayout.LayoutParams(-2, -2))
                if (uri != null) box.addView(icon(7, 44) {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(out.mime)
                        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                })
            }
            "video" -> {
                val b = btn("\u25B6  Video chalao")
                b.setOnClickListener {
                    if (uri == null) { Toast.makeText(this, "Video save nahi hua", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, out.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    catch (e: Exception) { Toast.makeText(this, "Video player nahi mila", Toast.LENGTH_SHORT).show() }
                }
                box.addView(b)
            }
            else -> {
                val b = btn("\u25B6  Gaana chalao")
                b.setOnClickListener {
                    try {
                        if (player?.isPlaying == true) {
                            player?.stop(); player?.release(); player = null
                            b.text = "\u25B6  Gaana chalao"
                        } else {
                            val f = java.io.File(cacheDir, "song")
                            f.writeBytes(out.bytes)
                            player = android.media.MediaPlayer().apply {
                                setDataSource(f.path)
                                setOnCompletionListener { b.text = "\u25B6  Gaana chalao" }
                                prepare()
                                start()
                            }
                            b.text = "\u23F8  Rok do"
                        }
                    } catch (e: Exception) { Toast.makeText(this, "Gaana nahi chala", Toast.LENGTH_SHORT).show() }
                }
                box.addView(b)
            }
        }
        return box
    }


    // ---------- side menu + Images screen (Trending / Templates / My images) ----------
    private val genDir: java.io.File get() = java.io.File(filesDir, "gen").also { it.mkdirs() }

    private fun saveGenerated(out: Media.Out): java.io.File? {
        return try {
            val ext = if (out.mime.contains("png")) "png" else if (out.mime.contains("webp")) "webp" else "jpg"
            java.io.File(genDir, "img_" + System.currentTimeMillis() + "." + ext).also { it.writeBytes(out.bytes) }
        } catch (e: Exception) { null }
    }

    private fun showDrawer() {
        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(70), dp(20), dp(20))
            setBackgroundColor(Color.parseColor("#212121"))
        }
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(14), 0, dp(14))
        r.addView(IconView(this).apply { kind = 13 }, LinearLayout.LayoutParams(dp(48), dp(40)))
        r.addView(TextView(this).apply { text = "Images"; textSize = 19f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        r.setOnClickListener { dlg.dismiss(); showImages() }
        col.addView(r)
        val r2 = LinearLayout(this)
        r2.gravity = Gravity.CENTER_VERTICAL
        r2.setPadding(0, dp(14), 0, dp(14))
        r2.addView(IconView(this).apply { kind = 20 }, LinearLayout.LayoutParams(dp(48), dp(40)))
        r2.addView(TextView(this).apply { text = "Security log"; textSize = 19f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        r2.setOnClickListener { dlg.dismiss(); showSecurityLog() }
        col.addView(r2)
        val r3 = LinearLayout(this)
        r3.gravity = Gravity.CENTER_VERTICAL
        r3.setPadding(0, dp(14), 0, dp(14))
        r3.addView(IconView(this).apply { kind = 26 }, LinearLayout.LayoutParams(dp(48), dp(40)))
        r3.addView(TextView(this).apply { text = "Chat history"; textSize = 19f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        r3.setOnClickListener { dlg.dismiss(); showChatHistory() }
        col.addView(r3)
        val r4 = LinearLayout(this)
        r4.gravity = Gravity.CENTER_VERTICAL
        r4.setPadding(0, dp(14), 0, dp(14))
        r4.addView(IconView(this).apply { kind = 30 }, LinearLayout.LayoutParams(dp(48), dp(40)))
        r4.addView(TextView(this).apply { text = "App code"; textSize = 19f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        r4.setOnClickListener { dlg.dismiss(); showAppPicker() }
        col.addView(r4)
        dlg.setContentView(col)
        dlg.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.78f).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.START)
        }
        dlg.show()
    }

    // app ka poora report: copy / share karke kisi ko bhejne ke liye
    private fun showAppReport(pkg: String, label: String) {
        val dlg = fullDlg()
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val tv = TextView(this).apply {
            text = "Report ban raha hai, thoda ruko..."; textSize = 11.5f; setTextColor(Color.parseColor("#D0D0D6"))
            typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        val sv = ScrollView(this)
        sv.addView(tv)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, dp(96), 0, 0)
        val btns = LinearLayout(this)
        btns.setPadding(dp(16), 0, dp(16), dp(8))
        fun btn(txt: String, onC: () -> Unit) = TextView(this).apply {
            text = txt; textSize = 15f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(Color.parseColor("#2B3A67")); cornerRadius = dp(14).toFloat() }
            setOnClickListener { onC() }
        }
        btns.addView(btn("Copy") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("report", tv.text))
            Toast.makeText(this, "Copy ho gaya", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(6) })
        btns.addView(btn("Share") {
            try {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tv.text.toString()), "Report bhejo"))
            } catch (e: Exception) { }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(6) })
        btns.addView(btn("Code") { showCodeBrowser(pkg, label) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(6) })
        col.addView(btns, LinearLayout.LayoutParams(-1, -2))
        col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        val topBar = FrameLayout(this)
        topBar.setBackgroundColor(Color.BLACK)
        val back = icon(23, 40) { dlg.dismiss() }
        back.background = oval("#303030")
        topBar.addView(back, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.BOTTOM).apply { leftMargin = dp(16); bottomMargin = dp(10) })
        topBar.addView(TextView(this).apply { text = label; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply { bottomMargin = dp(18) })
        root.addView(topBar, FrameLayout.LayoutParams(-1, dp(88), Gravity.TOP))
        dlg.setContentView(root)
        dlg.show()
        Thread {
            val r = try { AppReport.build(applicationContext, pkg) } catch (e: Throwable) { "Report nahi ban paya: ${e.message}" }
            runOnUiThread { tv.text = r }
        }.start()
    }

    // phone ke app chuno, phir unka code / files dekho
    @Suppress("DEPRECATION")
    private fun showAppPicker() {
        val dlg = fullDlg()
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(96), dp(16), dp(30))
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(content)
        root.addView(sv, FrameLayout.LayoutParams(-1, -1))
        val topBar = FrameLayout(this)
        topBar.setBackgroundColor(Color.BLACK)
        val back = icon(23, 40) { dlg.dismiss() }
        back.background = oval("#303030")
        topBar.addView(back, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.BOTTOM).apply { leftMargin = dp(16); bottomMargin = dp(10) })
        topBar.addView(TextView(this).apply { text = "App code"; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply { bottomMargin = dp(18) })
        root.addView(topBar, FrameLayout.LayoutParams(-1, dp(88), Gravity.TOP))
        val pm = packageManager
        val apps = try {
            pm.getInstalledApplications(0).filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 || it.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 }
                .map { it.loadLabel(pm).toString() to it.packageName }.sortedBy { it.first.lowercase() }
        } catch (e: Exception) { emptyList() }
        for ((lab, pk) in apps) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.VERTICAL
            r.setPadding(dp(14), dp(12), dp(14), dp(12))
            r.background = GradientDrawable().apply { setColor(Color.parseColor("#161B22")); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.parseColor("#30363D")) }
            r.addView(TextView(this).apply { text = lab; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
            r.addView(TextView(this).apply { text = pk; textSize = 12f; setTextColor(Color.parseColor("#8B949E")) })
            r.setOnClickListener { showCodeBrowser(pk, lab) }
            content.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        dlg.setContentView(root)
        dlg.show()
    }

    // GitHub jaisa file browser: folder, file, code. Save karne par Downloads/AppCode mein
    private fun showCodeBrowser(pkg: String, label: String) {
        val apk = try { packageManager.getApplicationInfo(pkg, 0).sourceDir } catch (e: Exception) { null }
        if (apk == null) { Toast.makeText(this, "Ye app phone mein nahi hai", Toast.LENGTH_SHORT).show(); return }
        val dlg = fullDlg()
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(12), dp(96), dp(12), dp(24))
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(body)
        root.addView(sv, FrameLayout.LayoutParams(-1, -1))
        val title = TextView(this).apply { textSize = 15f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.START }
        var cur = ""
        var viewing: String? = null
        var src: ApkSource? = null
        val topBar = FrameLayout(this)
        topBar.setBackgroundColor(Color.BLACK)
        lateinit var render: () -> Unit
        val goBack = {
            if (viewing != null) { viewing = null; render() }
            else if (cur.isEmpty()) dlg.dismiss()
            else { cur = cur.substringBeforeLast("/", ""); render() }
        }
        val back = icon(23, 40) { goBack() }
        back.background = oval("#303030")
        topBar.addView(back, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.BOTTOM).apply { leftMargin = dp(16); bottomMargin = dp(10) })
        topBar.addView(title, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { leftMargin = dp(68); rightMargin = dp(16); bottomMargin = dp(20) })
        root.addView(topBar, FrameLayout.LayoutParams(-1, dp(88), Gravity.TOP))
        dlg.setOnKeyListener { _, k, ev -> if (k == android.view.KeyEvent.KEYCODE_BACK && ev.action == android.view.KeyEvent.ACTION_UP) { goBack(); true } else false }

        fun pill(txt: String, onC: () -> Unit) = TextView(this).apply {
            text = txt; textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply { setColor(Color.parseColor("#2B3A67")); cornerRadius = dp(10).toFloat() }
            setOnClickListener { onC() }
        }
        fun toast(m: String) = runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show() }
        fun card() = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#0D1117")); cornerRadius = dp(10).toFloat(); setStroke(dp(1), Color.parseColor("#30363D")) }
        }

        render = {
            body.removeAllViews()
            sv.scrollTo(0, 0)
            val s = src
            val v = viewing
            title.text = label + (if (cur.isNotEmpty()) " / $cur" else "") + (if (v != null) " / " + v.substringAfterLast("/") else "")
            if (s == null) {
                body.addView(TextView(this).apply { text = "Khul raha hai..."; setTextColor(Color.parseColor("#8B949E")); textSize = 14f })
            } else if (v != null) {
                var full = ""
                val tv = TextView(this).apply {
                    text = "Padh raha hu..."; textSize = 11.5f; setTextColor(Color.parseColor("#D0D0D6")); typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
                }
                val bar = LinearLayout(this)
                bar.addView(pill("Copy") {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("code", full.ifEmpty { tv.text.toString() })); toast("Copy ho gaya")
                }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
                bar.addView(pill("Save") {
                    val txt = full.ifEmpty { tv.text.toString() }; val nm = v.replace('/', '_') + ".txt"
                    Thread { try { toast("Save hua: " + ApkSource.saveToDownloads(this, nm, "text/plain") { it.write(txt.toByteArray()) }) } catch (e: Exception) { toast("Save nahi hua: ${e.message}") } }.start()
                })
                body.addView(bar, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(10) })
                body.addView(tv)
                Thread {
                    var r = try { s.read(v) } catch (e: Throwable) { "Padh nahi paya: ${e.message}" }
                    full = r
                    if (r.length > 200_000) r = r.take(200_000) + "\n...(aage ka chhota kiya, Save mein poora milega)"
                    runOnUiThread { tv.text = r }
                }.start()
            } else {
                val ph = TextView(this).apply { text = "Khul raha hai..."; setTextColor(Color.parseColor("#8B949E")); textSize = 14f }
                body.addView(ph)
                Thread {
                    val items = try { s.list(cur) } catch (e: Throwable) { emptyList() }
                    runOnUiThread {
                        body.removeAllViews()
                        val c = card()
                        val head = LinearLayout(this)
                        head.gravity = Gravity.CENTER_VERTICAL
                        head.setPadding(dp(14), dp(12), dp(10), dp(12))
                        head.setBackgroundColor(Color.parseColor("#161B22"))
                        val hv = LinearLayout(this); hv.orientation = LinearLayout.VERTICAL
                        hv.addView(TextView(this).apply { text = label; textSize = 15f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
                        hv.addView(TextView(this).apply { text = pkg; textSize = 11f; setTextColor(Color.parseColor("#8B949E")) })
                        head.addView(hv, LinearLayout.LayoutParams(0, -2, 1f))
                        head.addView(pill("Save all") {
                            toast("Zip ban rahi hai, thoda ruko...")
                            Thread {
                                try {
                                    val where = ApkSource.saveToDownloads(this, pkg + "_code.zip", "application/zip") { o -> s.exportZip(o) { } }
                                    toast("Save hua: $where")
                                } catch (e: Throwable) { toast("Save nahi hua: ${e.message}") }
                            }.start()
                        })
                        c.addView(head)
                        if (items.isEmpty()) c.addView(TextView(this).apply { text = "Khali"; setTextColor(Color.parseColor("#8B949E")); setPadding(dp(14), dp(14), dp(14), dp(14)) })
                        for (n in items) {
                            val line = View(this); line.setBackgroundColor(Color.parseColor("#30363D"))
                            c.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
                            val r = LinearLayout(this)
                            r.gravity = Gravity.CENTER_VERTICAL
                            r.setPadding(dp(14), dp(14), dp(14), dp(14))
                            r.addView(IconView(this).apply { kind = if (n.dir) 29 else 30; tint = Color.parseColor(if (n.dir) "#8B98A9" else "#B8C0CC") }, LinearLayout.LayoutParams(dp(34), dp(24)))
                            r.addView(TextView(this).apply { text = n.name; textSize = 15.5f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
                            if (!n.dir && n.size > 0) r.addView(TextView(this).apply { text = if (n.size > 1024) "${n.size / 1024} KB" else "${n.size} B"; textSize = 12f; setTextColor(Color.parseColor("#8B949E")) })
                            val full = if (cur.isEmpty()) n.name else cur + "/" + n.name
                            r.setOnClickListener { if (n.dir) { cur = full; render() } else { viewing = full; render() } }
                            c.addView(r)
                        }
                        body.addView(c, LinearLayout.LayoutParams(-1, -2))
                    }
                }.start()
            }
        }
        dlg.setContentView(root)
        dlg.show()
        render()
        Thread {
            val s = try { ApkSource(apk) } catch (e: Throwable) { null }
            runOnUiThread {
                if (s == null) { Toast.makeText(this, "APK nahi khul paya", Toast.LENGTH_SHORT).show(); dlg.dismiss() }
                else { src = s; render() }
            }
        }.start()
    }

    // Security log: din ke hisaab se wo app jinpar shak hua tha, aur sunne ke liye speaker
    private fun showSecurityLog() {
        val dlg = fullDlg()
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(96), dp(16), dp(30))
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(content)
        root.addView(sv, FrameLayout.LayoutParams(-1, -1))
        val topBar = FrameLayout(this)
        topBar.setBackgroundColor(Color.BLACK)
        val back = icon(23, 40) { dlg.dismiss() }
        back.background = oval("#303030")
        topBar.addView(back, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.BOTTOM).apply { leftMargin = dp(16); bottomMargin = dp(10) })
        topBar.addView(TextView(this).apply { text = "Security log"; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply { bottomMargin = dp(18) })
        root.addView(topBar, FrameLayout.LayoutParams(-1, dp(88), Gravity.TOP))

        val list = Security.logs(getSharedPreferences("p", Context.MODE_PRIVATE))
        if (list.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "Abhi tak koi khatra nahi mila.\nAssistant har baar khulte hi phone ki jaanch karta hai, shak wala app milte hi yahan din ke hisaab se likh dega."
                textSize = 15f; setTextColor(Color.parseColor("#B0B0B8")); setPadding(0, dp(20), 0, 0)
            })
        }
        val dayFmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        val nice = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ENGLISH)
        val timeFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH)
        val today = dayFmt.format(java.util.Date())
                var last = ""
        for (e in list) {
            val d = dayFmt.format(java.util.Date(e.t))
            if (d != last) {
                last = d
                val head = nice.format(java.util.Date(e.t))
                content.addView(TextView(this).apply {
                    text = head; textSize = 15f; setTextColor(Color.parseColor("#8D8D95")); typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(18), 0, dp(8))
                })
            }
            val high = e.score >= 5
            val card = LinearLayout(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.setPadding(dp(14), dp(12), dp(8), dp(12))
            card.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1E1E22")); cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor(if (high) "#7A2E2E" else "#6B5A2A"))
            }
            val tv = LinearLayout(this)
            tv.orientation = LinearLayout.VERTICAL
            val nameRow = LinearLayout(this)
            nameRow.gravity = Gravity.CENTER_VERTICAL
            nameRow.addView(TextView(this).apply {
                text = e.label + (if (e.fresh) "  \u2022 naya app" else "")
                textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                setOnClickListener { showAppReport(e.pkg, e.label) }
            })
            nameRow.addView(TextView(this).apply {
                text = "  " + timeFmt.format(java.util.Date(e.t)); textSize = 12f; setTextColor(Color.parseColor(if (high) "#FF8A8A" else "#E5C06A"))
            })
            tv.addView(nameRow)
            tv.addView(TextView(this).apply {
                text = e.pkg; textSize = 12f; setTextColor(Color.parseColor(if (high) "#FF8A8A" else "#E5C06A"))
            })
            tv.addView(TextView(this).apply {
                text = e.text.substringAfter(" \u2014 "); textSize = 14f; setTextColor(Color.parseColor("#D0D0D6")); setPadding(0, dp(6), 0, 0)
            })
            card.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(icon(10, 44) { AssistantService.engine?.replay(e.text) },
                LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(6) })
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        dlg.setContentView(root)
        dlg.show()
    }

    private val builtinTrending = listOf(
        Tpl("Anime reaction GIF", true,
            "anime style girl with orange bob hair and heart shaped eyes, excited happy face, hands clasped, pink hearts and sparkles, cream background, clean cel shading",
            "Use my uploaded photo to create a high quality anime style reaction sticker of me. Keep my face shape, hairstyle, skin tone, glasses and clothing recognisable. Show an exaggerated joyful reaction: heart shaped eyes, a wide open smile and both hands pressed to my cheeks, with small sparkles and hearts floating around. Use clean confident line art, cel shading, a soft cream background and a thin white outline so it works as a chat reaction. No text, logos or watermarks."),
        Tpl("Mithai Mini-Me", true,
            "photorealistic young woman in a rose coloured kurta sitting inside a large open gift box of Indian sweets, laddoo, kaju katli, marigold flowers, warm Diwali light",
            "Use my uploaded photo to create a photoreal Diwali mithai scene with miniature versions of me. Keep my recognisable face, skin tone, hair and clothing. Show one to four tiny mini-mes in natural, different poses interacting with a big open gift box of Indian sweets such as motichoor laddoo, kaju katli or pista barfi. Pick a distinct box shape and colour palette, a festive setting with marigold flowers, diyas and rich fabric, a flattering camera angle and one playful action. Keep one clear focal point, believable scale, realistic food texture and soft contact shadows. Warm, rich festive light. No invented people, text, logos or watermarks."),
        Tpl("Diwali Décor", true,
            "cosy Indian living room decorated for Diwali with marigold garlands, fairy lights, brass diya and festive cushions, warm light, realistic photo",
            "Use my uploaded photo of a room or entrance and decorate it for Diwali while keeping the layout, walls, windows and furniture exactly as they are. Add marigold and mango leaf toran garlands, strings of warm fairy lights, brass diyas, a floor rangoli, a tray of flowers, and festive cushions and throws in red, teal and gold. Natural daylight with warm accents, realistic scale and shadows, elegant and tidy rather than cluttered. No people, text or logos."),
        Tpl("Diwali Wishes", false,
            "Happy Diwali greeting card, glowing diya on colourful rangoli, deep maroon background, gold lettering, warm bokeh lights",
            "Create a premium square Diwali greeting card. Deep maroon background with soft bokeh lights across the top, a large glowing clay diya in the centre sitting on a colourful floor rangoli with marigold petals. Elegant gold serif lettering reading 'Happy Diwali' and a smaller line 'Wishing you and your family light, love and happiness', separated by a thin gold divider with a small lotus ornament. Rich, warm, celebratory and sharp. Spell all text exactly."),
        Tpl("80s flashback", true,
            "1980s style portrait of an Indian man with voluminous hair, aviator sunglasses, colour blocked jacket, chin resting on hand, vintage film photo grain",
            "Use my uploaded photo to create an authentic 1980s family album portrait of me. Keep my face, skin tone and hair recognisable but restyle me for the era: a bold colour blocked jacket, aviator sunglasses and a voluminous period hairstyle. Pose me resting my chin on my hand in a living room with a patterned wall, a vase of flowers and a framed picture. Soft on-camera flash, slightly faded film colours, fine grain and a hint of print wear. No text, logos or watermarks."),
        Tpl("Childhood Portrait", true,
            "two Indian children, a boy and a girl, laughing and playing with diyas indoors on Diwali evening, warm window light, candid family photo",
            "Use my uploaded photo to imagine my childhood self at about seven years old. Keep the family resemblance: the same eyes, smile, skin tone and hair colour, with a hairstyle suited to a child. Show that child playing happily at home with a sibling during a festival evening, with sparklers and diyas nearby, wearing simple festive clothes. Warm natural window light, shallow depth of field and a candid family photo feel. No text, logos or watermarks."),
        Tpl("Rickshaw selfie", true,
            "friends laughing in a colourful auto rickshaw taking a selfie, busy Indian street, golden hour, realistic photo",
            "Use my uploaded photo to place me in a colourful auto rickshaw on a busy Indian street at golden hour, laughing and taking a selfie with two friends squeezed in beside me. Keep my face, hair and clothing recognisable and make the friends look clearly distinct. Soft motion blur in the background, warm evening light, bright seat covers and shopping bags. Natural smartphone selfie framing. No text, logos or watermarks."),
        Tpl("Movie poster", true,
            "colourful Bollywood style movie poster, smiling hero in front of a yellow taxi at sunset, bold title, cinematic lighting",
            "Use my uploaded photo to make a cinematic Bollywood style movie poster with me as the hero. Keep my face and look recognisable. Show me striding in front of a bright yellow taxi at sunset with a city skyline behind, confident and smiling. Add the bold original title 'ROAD TRIP' at the top, a short tagline, and a tidy billing block at the bottom. Rich colour grading, dramatic lighting and proper poster typography. No real actors' names or logos."),
        Tpl("Comic hero", true,
            "vintage comic book cover, hero with glasses flying kites above rooftops, kids cheering below, bold ink outlines, halftone dots, bright colours",
            "Use my uploaded photo to create a vintage style comic book cover with me as the hero. Keep my face, hair and glasses recognisable, drawn with bold ink outlines and halftone shading. Show me on a kite rescue mission above a rooftop town while kids cheer below. Add the original title 'KITE GUARDIAN', an issue number and a price box. Bright flat colours and a dynamic angle. No existing characters, studio logos or copyrighted marks."),
        Tpl("Indian mixtape", false,
            "cassette tape with a handwritten tracklist on a wooden table at dusk, old headphones, cup of chai, warm moody light, realistic photo",
            "A realistic top down photo of a cassette tape, a handwritten tracklist on lined paper and a pair of old headphones lying on a dark wooden table at dusk, with a cup of chai and a few marigold petals. Warm, moody lamp light, shallow depth of field, tactile detail on the plastic and the paper. The handwritten song titles should be legible but invented. No logos."),
        Tpl("Good morning", false,
            "steaming glass of masala chai on a wooden table with notebook and flowers, sunrise over green hills, golden light, good morning photo",
            "A cheerful good morning photo: a steaming glass of masala chai on a rustic wooden table beside a notebook and fresh flowers, with a soft sunrise over green hills in the background, gentle mist and warm golden light, crisp focus on the cup. Leave a little clear space at the top for a greeting. Natural, inviting, no logos."),
        Tpl("Caricature", true,
            "fun cartoon caricature of a smiling man with a big head, books and a paint palette, bright blue background, bold outlines",
            "Use my uploaded photo to draw a fun, flattering caricature of me. Keep my recognisable features but exaggerate them playfully: a slightly bigger head, expressive eyes and a big smile. Surround me with items that suit my work or hobbies, such as books, a paint palette or headphones. Clean digital illustration, bold outlines, vibrant colours and a plain bright sky blue background. No text or watermarks.")
    )
    private val builtinTemplates = listOf(
        Tpl("Poster", false,
            "modern poster, big red poppy flower, bold red title BLOOM, cream paper on terracotta background, editorial illustration",
            "Design a striking modern poster. One large red poppy with a thin dark green stem and leaves, in a graphic editorial illustration style, centred on a cream rectangle that sits on a warm terracotta background. A huge condensed red serif title 'BLOOM' at the top. Clean margins, balanced composition and subtle paper grain. Spell the text exactly."),
        Tpl("Interior design", false,
            "isometric cutaway miniature living room, big window, cream sofa, green armchair, plants, soft lilac background, clean 3D render",
            "A charming isometric cutaway of a small living room, like a miniature diorama: a big gridded window, a cream sofa, a green armchair, a floor lamp, house plants, a rug and framed art on the wall. Soft pastel lilac background with gentle light, a clean 3D render with tidy details. No people, no text."),
        Tpl("Logo", false,
            "round navy badge logo with a simple white fish symbol, flat vector style, light grey background",
            "A minimal circular badge logo for a small seafood cafe. A bold navy ring with a simple stylised fish drawn in clean white line art inside, and two small dots on the sides. Flat vector style, strong silhouette, shown on a light grey background. No extra text."),
        Tpl("Illustration", false,
            "warm storybook illustration of a girl with a bun hugging a large black and white cat, yellow cardigan, lavender background, soft gouache texture",
            "A warm, hand painted storybook illustration of a girl with a bun sitting cross legged on the floor and hugging a large black and white cat. She wears a yellow cardigan and green trousers, drawn in soft gouache texture, against a calm lavender background with gentle shading. No text."),
        Tpl("Headshot", true,
            "professional studio headshot of a smiling woman, cream sweater, soft grey backdrop, soft light",
            "Use my uploaded photo to make a professional studio headshot. Keep my face, skin tone, hairstyle and expression natural and recognisable, with no heavy retouching. Dress me in a clean cream sweater or a neat shirt, with a soft key light and gentle fill, a neutral light grey backdrop, shallow depth of field and sharp eyes. Shoulders up framing. No text or watermarks."),
        Tpl("Icon", false,
            "four flat icons, peace sign hand, shooting star, smiling frog, ringed planet, white and mint on deep teal background",
            "A set of four friendly flat icons arranged in a 2 by 2 grid: a peace sign hand, a shooting star, a smiling frog and a ringed planet. Chunky rounded shapes in white and soft mint on a deep teal background, consistent line weight, minimal and clean. No text."),
        Tpl("Product photo", true,
            "pink scented candle in a glass jar on a pink studio background, soft shadow, commercial product photo",
            "Use my uploaded photo of a product and turn it into a polished studio product shot. Keep the product's shape, colours and label exactly as they are. Place it on a seamless soft pink backdrop with a gentle shadow and a subtle reflection, lit by soft diffused light from the left. Crisp, commercial and uncluttered. No added text or logos."),
        Tpl("Merch", false,
            "white t-shirt hanging on a clothesline against a blue sky with a cartoon frog DJ print, natural sunlight",
            "A realistic mockup of a plain white t-shirt hanging on a thin clothesline against a clear blue sky, printed with a cool cartoon frog wearing headphones and DJing. A slight breeze moves the fabric, natural sunlight, crisp detail. No extra text."),
        Tpl("Infographic", false,
            "clean flat infographic of the water cycle, sun, sea, clouds, rain, river, pastel colours, numbered steps",
            "A clean, easy infographic explaining how rain forms: the sun heating the sea, evaporation arrows, a cloud with condensation droplets, rain falling on hills and a river flowing back to the sea. Flat pastel colours, steps numbered 1 to 4 and labelled in simple English, with a tidy layout."),
        Tpl("Flyer", false,
            "black and white photocopied lost dog flyer with big title, grainy dog photo and tear off phone number strips",
            "A black and white photocopied 'LOST DOG' flyer: a huge bold title, a grainy photo of a friendly dog, a short description and a row of tear-off strips with a phone number at the bottom (use 'XXXXX XXXXX'). An authentic xerox look."),
        Tpl("Book cover", false,
            "calm book cover, sunset over a teal sea with a striped deck chair on the sand, elegant serif title, muted colours",
            "A calm, premium book cover: a quiet sunset over a teal sea with a single striped deck chair on the sand, an elegant serif title 'THE LONG EVENING' and a smaller author line 'A. Rao'. Muted colours with lots of negative space."),
        Tpl("App mockup", false,
            "music player app screen on a modern phone, colourful album art, play controls, soft gradient background",
            "A clean music player app screen on a modern phone, shown straight on against a soft gradient background: large colourful album art, track title and artist name, a progress bar, and play, next and previous controls. Rounded UI with tidy spacing and sample text in English.")
    )
    private var trending: List<Tpl> = builtinTrending
    private var templates: List<Tpl> = builtinTemplates
    private val palette = listOf(
        "#5B247A" to "#1BCEDF", "#B24592" to "#F15F79", "#0F2027" to "#2C5364", "#134E5E" to "#71B280",
        "#8E2DE2" to "#4A00E0", "#C33764" to "#1D2671", "#CC5333" to "#23074D", "#1F4037" to "#4CA58A"
    )

    private class Turn(var photo: String?, val prompt: String) {
        var file: java.io.File? = null
        var err: String? = null
        var busy = true
    }

    private var imgAttCb: ((Pair<String, String>) -> Unit)? = null

    private fun showImages() {
        val dlg = fullDlg()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(96), dp(16), dp(170))
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(content)
        root.addView(sv, FrameLayout.LayoutParams(-1, -1))

        var tab = 0
        var busyImg = false
        var imgAtt: Pair<String, String>? = null
        val turns = ArrayList<Turn>()
        lateinit var render: () -> Unit
        lateinit var refreshAtt: () -> Unit

        val topBar = FrameLayout(this)
        topBar.setBackgroundColor(Color.BLACK)
        val back = icon(23, 40) { if (tab == 3) { tab = 0; render() } else dlg.dismiss() }
        back.background = oval("#303030")
        topBar.addView(back, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.BOTTOM).apply { leftMargin = dp(16); bottomMargin = dp(10) })
        topBar.addView(TextView(this).apply { text = "Images"; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply { bottomMargin = dp(18) })
        root.addView(topBar, FrameLayout.LayoutParams(-1, dp(88), Gravity.TOP))
        dlg.setOnKeyListener { _, k, ev ->
            if (k == android.view.KeyEvent.KEYCODE_BACK && ev.action == android.view.KeyEvent.ACTION_UP && tab == 3) { tab = 0; render(); true } else false
        }

        val et = EditText(this).apply {
            hint = "Describe an image"
            setHintTextColor(Color.parseColor("#8D8D95"))
            setTextColor(Color.WHITE)
            textSize = 16f
            background = null
            maxLines = 5
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        fun b64bitmap(b64: String, sample: Int = 2): android.graphics.Bitmap? = try {
            val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) { null }

        fun run(t: Turn) {
            val key = Keys.get(getSharedPreferences("p", Context.MODE_PRIVATE))
            if (key.isEmpty()) { Toast.makeText(this, "API key nahi mili", Toast.LENGTH_SHORT).show(); t.busy = false; t.err = "API key nahi mili"; render(); return }
            t.busy = true; t.err = null; t.file = null
            busyImg = true
            val att = t.photo?.let { Pair("image/jpeg", it) }
            render()
            kotlin.concurrent.thread {
                val out = Media.image(key, t.prompt, att, Models.sel(getSharedPreferences("p", Context.MODE_PRIVATE), "photo"))
                val err = Media.lastErr
                runOnUiThread {
                    busyImg = false
                    t.busy = false
                    if (out != null) t.file = saveGenerated(out)
                    else t.err = "Could not create the image. " + err.ifBlank { "Please try again." }
                    render()
                    sv.post { sv.fullScroll(View.FOCUS_DOWN) }
                }
            }
        }

        val send = {
            val t = et.text.toString().trim()
            if (t.isNotEmpty() && !busyImg) {
                val turn = Turn(imgAtt?.second, t)
                turns.add(turn)
                imgAtt = null
                refreshAtt()
                et.text?.clear()
                tab = 3
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(et.windowToken, 0)
                run(turn)
            }
        }
        val sendB = icon(4, 38) { send() }
        sendB.tint = Color.WHITE
        sendB.background = oval("#2F6BFF")
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(Color.parseColor("#303030")); setStroke(dp(1), Color.parseColor("#3C3C3C")) }
            addView(IconView(this@MainActivity).apply { kind = 13 }, LinearLayout.LayoutParams(dp(34), dp(34)))
            addView(et, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
            addView(sendB, LinearLayout.LayoutParams(dp(38), dp(38)))
        }
        // chuni hui photo ka chhota dikhawa (bar ke upar)
        val attRow = FrameLayout(this)
        attRow.visibility = View.GONE
        val attThumb = android.widget.ImageView(this)
        attThumb.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        attThumb.clipToOutline = true
        attThumb.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, ol: android.graphics.Outline) { ol.setRoundRect(0, 0, v.width, v.height, dp(14).toFloat()) }
        }
        attRow.addView(attThumb, FrameLayout.LayoutParams(dp(64), dp(64)).apply { topMargin = dp(8); rightMargin = dp(8) })
        attRow.addView(TextView(this).apply {
            text = "✕"; textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.BLACK)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            setOnClickListener { imgAtt = null; refreshAtt() }
        }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.START).apply { leftMargin = dp(52) })
        refreshAtt = {
            val a = imgAtt
            attRow.visibility = if (a == null) View.GONE else View.VISIBLE
            if (a != null) attThumb.setImageBitmap(b64bitmap(a.second, 4))
        }
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bottom.addView(attRow, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        bottom.addView(bar, LinearLayout.LayoutParams(-1, -2))
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(18) })

        // photo wali template par: "Choose photo / Take photo"
        fun askPhoto(tpl: Tpl) {
            val d = android.app.Dialog(this)
            d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(26), dp(26), dp(26), dp(18))
                background = GradientDrawable().apply { cornerRadius = dp(30).toFloat(); setColor(Color.parseColor("#1E1E1E")) }
            }
            col.addView(TextView(this).apply { text = tpl.name; textSize = 25f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
            col.addView(TextView(this).apply { text = "Choose how you’d like to add a photo."; textSize = 17f; setTextColor(Color.parseColor("#C8CCD6")); setPadding(0, dp(8), 0, dp(18)) })
            fun pill(label: String, go: () -> Unit) {
                col.addView(TextView(this).apply {
                    text = label; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                    setTextColor(Color.BLACK)
                    background = GradientDrawable().apply { cornerRadius = dp(30).toFloat(); setColor(Color.WHITE) }
                    setOnClickListener { go() }
                }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(12) })
            }
            val got: (Pair<String, String>) -> Unit = { a ->
                imgAtt = a
                refreshAtt()
                et.setText(tpl.prompt)
                et.setSelection(et.text.length)
            }
            pill("Choose photo") { d.dismiss(); imgAttCb = got; pick(101) }
            pill("Take photo") { d.dismiss(); imgAttCb = got; camera() }
            col.addView(TextView(this).apply {
                text = "Cancel"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setOnClickListener { d.dismiss() }
            }, LinearLayout.LayoutParams(-1, dp(48)))
            d.setContentView(col)
            d.window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            d.show()
        }

        val thumbDir = java.io.File(filesDir, "thumbs").also { it.mkdirs() }
        val thumbPool = java.util.concurrent.Executors.newSingleThreadExecutor()
        dlg.setOnDismissListener { thumbPool.shutdownNow(); imgAttCb = null }
        fun loadThumb(prompt: String, target: android.widget.ImageView) {
            val f = java.io.File(thumbDir, "p" + prompt.hashCode() + ".jpg")
            fun show() {
                val bm = try { android.graphics.BitmapFactory.decodeFile(f.path) } catch (e: Exception) { null }
                if (bm != null) {
                    // neeche ka chhota watermark kaat do
                    val c = try { android.graphics.Bitmap.createBitmap(bm, 0, 0, bm.width, (bm.height * 0.92f).toInt()) } catch (e: Exception) { bm }
                    runOnUiThread { target.setImageBitmap(c) }
                }
            }
            if (f.exists() && f.length() > 2000) { show(); return }
            try {
                thumbPool.execute {
                    for (attempt in 0..2) {
                        try {
                            val u = "https://image.pollinations.ai/prompt/" + java.net.URLEncoder.encode(prompt.take(260), "UTF-8") + "?width=512&height=640&nologo=true&seed=7"
                            val c = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                            c.connectTimeout = 10000
                            c.readTimeout = 70000
                            if (c.responseCode == 200) {
                                val b = c.inputStream.readBytes()
                                if (b.size > 2000) { f.writeBytes(b); show(); break }
                            }
                        } catch (e: Exception) { }
                        try { Thread.sleep(4000) } catch (e: InterruptedException) { break }
                    }
                }
            } catch (e: Exception) { }
        }

        fun viewer(f: java.io.File) {
            val v = fullDlg()
            val iv = android.widget.ImageView(this)
            iv.setBackgroundColor(Color.BLACK)
            iv.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            iv.setImageBitmap(android.graphics.BitmapFactory.decodeFile(f.path))
            iv.setOnClickListener { v.dismiss() }
            val frame = FrameLayout(this)
            frame.setBackgroundColor(Color.BLACK)
            frame.addView(iv, FrameLayout.LayoutParams(-1, -1))
            val back2 = icon(23, 40) { v.dismiss() }
            back2.background = oval("#303030")
            frame.addView(back2, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.TOP).apply { leftMargin = dp(16); topMargin = dp(44) })
            val more = icon(9, 40) { }
            more.background = oval("#303030")
            more.setOnClickListener {
                val pm = android.widget.PopupMenu(this, more)
                pm.menu.add("Delete")
                pm.setOnMenuItemClickListener {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Delete this image?")
                        .setPositiveButton("Delete") { _, _ -> f.delete(); v.dismiss(); Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show(); render() }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
                pm.show()
            }
            frame.addView(more, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END or Gravity.TOP).apply { rightMargin = dp(16); topMargin = dp(44) })
            v.setContentView(frame)
            v.show()
        }

        fun saveToGallery(f: java.io.File) {
            try {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, f.name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, if (f.name.endsWith("png")) "image/png" else "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/AI Assistant")
                }
                val u = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                if (u != null) contentResolver.openOutputStream(u)?.use { it.write(f.readBytes()) }
                Toast.makeText(this, "Saved to gallery", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this, "Could not save", Toast.LENGTH_SHORT).show() }
        }

        fun roundOutline(iv: View, r: Int) {
            iv.clipToOutline = true
            iv.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, ol: android.graphics.Outline) { ol.setRoundRect(0, 0, v.width, v.height, dp(r).toFloat()) }
            }
        }

        render = {
            content.removeAllViews()
            if (tab != 3) {
                val tabs = LinearLayout(this)
                fun tabV(i: Int, name: String): TextView = TextView(this).apply {
                    text = name
                    textSize = 16f
                    setTextColor(if (tab == i) Color.WHITE else Color.parseColor("#9AA3B5"))
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(if (tab == i) Color.parseColor("#3A3A3A") else Color.TRANSPARENT) }
                    setOnClickListener { tab = i; render() }
                }
                tabs.addView(tabV(0, "Trending"))
                tabs.addView(tabV(1, "Templates"), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) })
                tabs.addView(tabV(2, "My images"), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) })
                content.addView(tabs, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(16) })
            }

            if (tab == 0 || tab == 1) {
                val list = if (tab == 0) trending else templates
                var i = 0
                while (i < list.size) {
                    val row = LinearLayout(this)
                    for (j in 0..1) {
                        val idx = i + j
                        if (idx < list.size) {
                            val tpl = list[idx]
                            val (c1, c2) = palette[idx % palette.size]
                            val card = FrameLayout(this)
                            card.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(c1), Color.parseColor(c2))).apply { cornerRadius = dp(24).toFloat() }
                            roundOutline(card, 24)
                            val pic = android.widget.ImageView(this)
                            pic.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            card.addView(pic, FrameLayout.LayoutParams(-1, -1))
                            loadThumb(tpl.thumb, pic)
                            card.addView(View(this).apply {
                                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.parseColor("#B3000000")))
                            }, FrameLayout.LayoutParams(-1, dp(70), Gravity.BOTTOM))
                            card.addView(TextView(this).apply {
                                text = tpl.name
                                textSize = 17f
                                setTextColor(Color.WHITE)
                                typeface = Typeface.DEFAULT_BOLD
                                setPadding(dp(16), dp(14), dp(16), dp(14))
                            }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
                            card.setOnClickListener {
                                if (tpl.photo) askPhoto(tpl)
                                else {
                                    imgAtt = null
                                    refreshAtt()
                                    et.setText(tpl.prompt)
                                    et.setSelection(et.text.length)
                                    et.requestFocus()
                                }
                            }
                            row.addView(card, LinearLayout.LayoutParams(0, dp(205), 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
                        } else row.addView(View(this), LinearLayout.LayoutParams(0, dp(205), 1f))
                    }
                    content.addView(row)
                    i += 2
                }
            }

            if (tab == 3) {
                val sw = resources.displayMetrics.widthPixels - dp(32)
                for (t in turns) {
                    val ph = t.photo?.let { b64bitmap(it, 2) }
                    if (ph != null) {
                        val iv = android.widget.ImageView(this)
                        iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        iv.setImageBitmap(ph)
                        roundOutline(iv, 22)
                        content.addView(iv, LinearLayout.LayoutParams((sw * 0.66f).toInt(), (sw * 0.66f * 0.9f).toInt()).apply { gravity = Gravity.END; bottomMargin = dp(8) })
                    }
                    content.addView(TextView(this).apply {
                        text = t.prompt; textSize = 16f; setTextColor(Color.WHITE); setLineSpacing(0f, 1.2f)
                        setPadding(dp(18), dp(14), dp(18), dp(14))
                        background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(Color.parseColor("#10106A")) }
                    }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; leftMargin = dp(48); bottomMargin = dp(18) })
                    val f = t.file
                    if (t.busy) {
                        content.addView(TextView(this).apply { text = "Creating image..."; textSize = 16f; setTextColor(Color.parseColor("#9AA3B5")); setPadding(dp(4), dp(8), 0, dp(24)) })
                    } else if (f != null) {
                        val iv = android.widget.ImageView(this)
                        iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        iv.setImageBitmap(android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }))
                        roundOutline(iv, 24)
                        iv.setOnClickListener { viewer(f) }
                        content.addView(iv, LinearLayout.LayoutParams(sw, sw).apply { bottomMargin = dp(10) })
                        content.addView(TextView(this).apply {
                            text = "Created successfully"; textSize = 14f; setTextColor(Color.parseColor("#2FD06B"))
                            setPadding(dp(14), dp(6), dp(14), dp(6))
                            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#123B22")) }
                        }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) })
                        content.addView(TextView(this).apply { text = "Your image is ready."; textSize = 17f; setTextColor(Color.WHITE) })
                        val acts = LinearLayout(this)
                        val more = icon(9, 44) { }
                        more.setOnClickListener {
                            val pm = android.widget.PopupMenu(this, more)
                            pm.menu.add("Retry"); pm.menu.add("Save to gallery"); pm.menu.add("Delete")
                            pm.setOnMenuItemClickListener { mi ->
                                when (mi.title.toString()) {
                                    "Retry" -> { if (!busyImg) run(t) }
                                    "Save to gallery" -> saveToGallery(f)
                                    else -> android.app.AlertDialog.Builder(this)
                                        .setTitle("Delete this image?")
                                        .setPositiveButton("Delete") { _, _ -> f.delete(); turns.remove(t); if (turns.isEmpty()) tab = 0; render() }
                                        .setNegativeButton("Cancel", null).show()
                                }
                                true
                            }
                            pm.show()
                        }
                        acts.addView(more)
                        content.addView(acts, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = -dp(10); bottomMargin = dp(20) })
                    } else if (t.err != null) {
                        content.addView(TextView(this).apply { text = t.err; textSize = 15f; setTextColor(Color.parseColor("#FF8A8A")) })
                        content.addView(TextView(this).apply {
                            text = "Retry"; textSize = 15f; setTextColor(Color.WHITE)
                            setPadding(dp(18), dp(8), dp(18), dp(8))
                            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.parseColor("#2A2A2A")) }
                            setOnClickListener { if (!busyImg) run(t) }
                        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8); bottomMargin = dp(20) })
                    }
                }
            }

            if (tab == 2) {
                val files = (genDir.listFiles() ?: emptyArray()).filter { it.isFile }.sortedByDescending { it.lastModified() }.take(60)
                if (files.isEmpty()) {
                    content.addView(TextView(this).apply {
                        text = "No images yet. Describe one below."
                        textSize = 15f
                        setTextColor(Color.parseColor("#9AA3B5"))
                    })
                }
                val side = (resources.displayMetrics.widthPixels - dp(32) - dp(12)) / 3
                var k = 0
                while (k < files.size) {
                    val row = LinearLayout(this)
                    for (j in 0..2) {
                        val idx = k + j
                        if (idx < files.size) {
                            val f = files[idx]
                            val iv = android.widget.ImageView(this)
                            iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            iv.setImageBitmap(android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }))
                            roundOutline(iv, 14)
                            iv.setOnClickListener { viewer(f) }
                            row.addView(iv, LinearLayout.LayoutParams(side, side).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                        }
                    }
                    content.addView(row)
                    k += 3
                }
            }
        }
        // Gemini se roz apne aap nayi Trending / Templates (naya mile to yahin badal jaata hai)
        val tsp = getSharedPreferences("p", Context.MODE_PRIVATE)
        Trends.load(tsp)?.let { trending = it.first; templates = it.second }
        if (Trends.due(tsp)) {
            val tkey = Keys.get(tsp)
            if (tkey.isNotEmpty()) kotlin.concurrent.thread {
                val r = Trends.refresh(tkey, tsp)
                if (r != null) runOnUiThread {
                    trending = r.first
                    templates = r.second
                    if (dlg.isShowing && (tab == 0 || tab == 1)) render()
                }
            }
        }
        render()
        dlg.setContentView(root)
        dlg.show()
    }


    // ---------- model chunna (Auto / Chat / Coding / Web search / Photo / Video / Music) ----------
    private lateinit var titleV: TextView

    private fun askLimit(m: Models.M, done: () -> Unit) {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.hint = "Roz ki requests, jaise 250"
        val cur = Usage.limitOf(m)
        if (cur > 0) et.setText(cur.toString())
        android.app.AlertDialog.Builder(this)
            .setTitle(m.name + " ki roz ki limit")
            .setView(et)
            .setPositiveButton("Save") { _, _ -> Usage.setLimit(m, et.text.toString().toIntOrNull() ?: 0); done() }
            .setNegativeButton("Hatao") { _, _ -> Usage.setLimit(m, 0); done() }
            .show()
    }

    private fun refreshTitle() {
        val p = getSharedPreferences("p", Context.MODE_PRIVATE)
        var c = Models.sel(p, "chat")
        if (c != null && Usage.paidOnly(c)) { Models.setSel(p, "chat", null); c = null }
        val base = if (c == null) "AI Auto" else "AI " + c.name
        val s = SpannableString(base)
        s.setSpan(ForegroundColorSpan(Color.parseColor("#9AA3B5")), 3, base.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        titleV.text = s
    }

    private fun showModels() {
        val p = getSharedPreferences("p", Context.MODE_PRIVATE)
        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(6), dp(10), dp(6), dp(10))
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(col)
        sv.background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(Color.parseColor("#1E1E1E")) }
        var thinkOpen = true
        val openProv = mutableSetOf<String>()
        Models.CATS.mapNotNull { Models.sel(p, it.first) }.forEach { openProv.add(it.prov) }
        if (openProv.isEmpty()) openProv.add("gemini")
        fun provName(x: String) = when (x) { "gemini" -> "Gemini"; "openai" -> "ChatGPT (OpenAI)"; "groq" -> "Groq"; "openrouter" -> "OpenRouter"; else -> x }
        fun provColor(x: String) = when (x) { "gemini" -> "#4F8BFF"; "openai" -> "#10A37F"; "groq" -> "#F55036"; "openrouter" -> "#7C5CFF"; else -> "#6B7280" }
        fun provLetter(x: String) = when (x) { "gemini" -> "G"; "openai" -> "O"; "groq" -> "Gq"; "openrouter" -> "OR"; else -> x.take(1).uppercase() }
        fun badge(x: String, size: Int): View = TextView(this).apply {
            text = provLetter(x)
            textSize = if (size >= 30) 13f else 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(provColor(x))) }
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }

        lateinit var render: () -> Unit
        fun row(title: String, sub: String?, prov: String?, checked: Boolean, usage: Usage.Info? = null, hold: (() -> Unit)? = null, go: () -> Unit): View {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(14), dp(11), dp(16), dp(11))
            r.addView(TextView(this).apply {
                text = if (checked) "\u2713" else ""
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(34), -2))
            if (prov != null) r.addView(badge(prov, 30), LinearLayout.LayoutParams(dp(30), dp(30)).apply { leftMargin = dp(4) })
            val tx = LinearLayout(this)
            tx.orientation = LinearLayout.VERTICAL
            tx.addView(TextView(this).apply { text = title; textSize = 17f; setTextColor(Color.WHITE) })
            if (sub != null) tx.addView(TextView(this).apply { text = sub; textSize = 13.5f; setTextColor(Color.parseColor("#9AA3B5")) })
            if (usage != null) {
                if (usage.frac != null) {
                    val bar = LinearLayout(this)
                    bar.background = GradientDrawable().apply { cornerRadius = dp(3).toFloat(); setColor(Color.parseColor("#333333")) }
                    val left = (1f - usage.frac).coerceIn(0f, 1f)
                    val fill = View(this)
                    fill.background = GradientDrawable().apply {
                        cornerRadius = dp(3).toFloat()
                        setColor(Color.parseColor(if (left > 0.4f) "#3DDC84" else if (left > 0.15f) "#FFB300" else "#FF5252"))
                    }
                    bar.addView(fill, LinearLayout.LayoutParams(0, dp(5), left))
                    bar.addView(View(this), LinearLayout.LayoutParams(0, dp(5), 1f - left))
                    tx.addView(bar, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(5) })
                }
                tx.addView(TextView(this).apply { text = usage.text; textSize = 12f; setTextColor(Color.parseColor("#7C8499")); setPadding(0, dp(3), 0, 0) })
            }
            r.addView(tx, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
            r.setOnClickListener { go() }
            if (hold != null) r.setOnLongClickListener { hold(); true }
            return r
        }
        fun head(t: String): View = TextView(this).apply {
            text = t
            textSize = 14f
            setTextColor(Color.parseColor("#9AA3B5"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(20), dp(14), dp(16), dp(4))
        }
        fun line(): View = View(this).apply { setBackgroundColor(Color.parseColor("#333333")) }

        render = {
            col.removeAllViews()
            val none = Models.CATS.all { Models.sel(p, it.first) == null }
            col.addView(row("Auto", null, null, none) {
                for ((k, _) in Models.CATS) Models.setSel(p, k, null)
                refreshTitle(); render()
            })
            col.addView(line(), LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(14), dp(6), dp(14), dp(6)) })
            val th = LinearLayout(this)
            th.gravity = Gravity.CENTER_VERTICAL
            th.setPadding(dp(54), dp(10), dp(20), dp(10))
            th.addView(TextView(this).apply { text = "Thinking level"; textSize = 17f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, -2, 1f))
            th.addView(TextView(this).apply { text = if (thinkOpen) "\u2303" else "\u2304"; textSize = 20f; setTextColor(Color.WHITE) })
            th.setOnClickListener { thinkOpen = !thinkOpen; render() }
            col.addView(th)
            if (thinkOpen) {
                val lv = Models.think(p)
                col.addView(row("Medium", "Balanced depth", null, lv == "medium") { p.edit().putString("think", "medium").apply(); render() })
                col.addView(row("High", "Extra thorough", null, lv == "high") { p.edit().putString("think", "high").apply(); render() })
            }
            // sirf free models dikhte hain, paid sab hata diye
            for ((k, _) in Models.CATS) Models.sel(p, k)?.let { if (!it.free || Usage.paidOnly(it)) Models.setSel(p, k, null) }
            val all = Models.list(p).filter { it.free && !Usage.paidOnly(it) }
            for (prov in listOf("gemini", "openai", "groq", "openrouter")) {
                val mine = all.filter { it.prov == prov }
                if (mine.isEmpty()) continue
                col.addView(line(), LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(14), dp(6), dp(14), dp(2)) })
                val open = prov in openProv
                val ph = LinearLayout(this)
                ph.gravity = Gravity.CENTER_VERTICAL
                ph.setPadding(dp(18), dp(10), dp(20), dp(10))
                ph.addView(badge(prov, 30), LinearLayout.LayoutParams(dp(30), dp(30)))
                ph.addView(TextView(this).apply { text = provName(prov); textSize = 17f; setTextColor(Color.WHITE) },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
                ph.addView(TextView(this).apply { text = if (open) "\u2303" else "\u2304"; textSize = 20f; setTextColor(Color.WHITE) })
                ph.setOnClickListener { if (open) openProv.remove(prov) else openProv.add(prov); render() }
                col.addView(ph)
                if (!open) continue
                for ((cat, label) in Models.CATS) {
                    val items = mine.filter { it.cat == cat }.sortedByDescending { it.free }
                    if (items.isEmpty()) continue
                    col.addView(head(label))
                    val cur = Models.sel(p, cat)
                    for (mm in items) {
                        val on = cur != null && cur.key == mm.key
                        col.addView(row(mm.name, null, mm.prov, on, Usage.info(mm), if (mm.prov == "gemini" || mm.prov == "openai") ({ askLimit(mm) { render() } }) else null) {
                            if (Usage.paidOnly(mm)) {
                                Toast.makeText(this, "Ye model free plan mein nahi chalta. Paid key ho to isko lamba dabakar apni limit daalo.", Toast.LENGTH_LONG).show()
                            } else {
                                Models.setSel(p, cat, if (on) null else mm)
                                refreshTitle(); render()
                            }
                        })
                    }
                }
                if (prov != "gemini") col.addView(TextView(this).apply {
                    text = "Only gives answers, no phone actions."
                    textSize = 12.5f
                    setTextColor(Color.parseColor("#7C8499"))
                    setPadding(dp(20), dp(2), dp(16), dp(6))
                })
            }
        }
        render()
        dlg.setContentView(sv)
        dlg.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.86f).toInt(), (resources.displayMetrics.heightPixels * 0.78f).toInt())
            setGravity(Gravity.TOP or Gravity.START)
            attributes = attributes.apply { x = dp(10); y = dp(80) }
        }
        dlg.show()
        if (Models.stale(p)) {
            val key = Keys.get(p)
            kotlin.concurrent.thread {
                Models.refresh(p, key)
                runOnUiThread { if (dlg.isShowing) render() }
            }
        }
        kotlin.concurrent.thread {
            Usage.refreshOpenRouter()
            runOnUiThread { if (dlg.isShowing) render() }
        }
    }

    private fun soon() {
        Toast.makeText(this, "Ye jald aayega", Toast.LENGTH_SHORT).show()
    }

    private fun icon(kind: Int, sizeDp: Int, onClick: () -> Unit): IconView = IconView(this).apply {
        this.kind = kind
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    @Suppress("DEPRECATION")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Usage.p = getSharedPreferences("p", Context.MODE_PRIVATE)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.setSoftInputMode(
            if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#000000"),
                    Color.parseColor("#02051A"),
                    Color.parseColor("#02051A"),
                    Color.parseColor("#0A1F8F"),
                    Color.parseColor("#1D3FD6")))
        }

        // ---------- upar ki patti ----------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(icon(0, 44) { showDrawer() })
        val title = TextView(this).apply {
            textSize = 19f
            setTextColor(Color.parseColor("#E8EAF0"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { showModels() }
        }
        titleV = title
        refreshTitle()
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12) })
        val avatar = TextView(this).apply {
            text = "S"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#4F8BFF"), Color.parseColor("#7C5CFF"))).apply {
                shape = GradientDrawable.OVAL
            }
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(36), dp(36)))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))

        // ---------- beech ka hissa: baat-cheet ----------
        chatList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll = ScrollView(this).apply {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isVerticalScrollBarEnabled = false
            addView(chatList, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(chatScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---------- neeche ka message bar ----------
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chipRow = LinearLayout(this).apply { visibility = View.GONE }
        pillRow = LinearLayout(this).apply { visibility = View.GONE }
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(30).toFloat()
                setColor(Color.parseColor("#15181F"))
                setStroke(dp(1), Color.parseColor("#1F2633"))
            }
            addView(chipRow, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(4); topMargin = dp(4) })
            addView(bar, LinearLayout.LayoutParams(-1, -2))
            addView(pillRow, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(4); bottomMargin = dp(4) })
        }
        bar.addView(icon(1, 44) { showPlus() })
        input = EditText(this).apply {
            hint = "Ask AI"
            setHintTextColor(Color.parseColor("#7D8597"))
            setTextColor(Color.parseColor("#E8EAF0"))
            textSize = 16f
            background = null
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(dp(6), dp(12), dp(6), dp(12))
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_SEND) { sendInput(); true } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    voiceBtn.kind = if (s.isNullOrBlank()) 3 else 4
                }
            })
        }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(icon(2, 44) { talk() })
        voiceBtn = icon(3, 48) { sendInput() }
        voiceBtn.tint = Color.WHITE
        voiceBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#4F8BFF"))
        }
        bar.addView(voiceBtn)
        root.addView(composer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
            topMargin = dp(10)
        })

        // status bar, navigation bar aur keyboard ke hisaab se jagah chhodo
        root.setOnApplyWindowInsetsListener { v, ins ->
            val top: Int
            val bottom: Int
            val left: Int
            val right: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val sb = ins.getInsets(WindowInsets.Type.systemBars())
                val ime = ins.getInsets(WindowInsets.Type.ime())
                top = sb.top
                bottom = maxOf(sb.bottom, ime.bottom)
                left = sb.left
                right = sb.right
            } else {
                top = ins.systemWindowInsetTop
                bottom = ins.systemWindowInsetBottom
                left = ins.systemWindowInsetLeft
                right = ins.systemWindowInsetRight
            }
            v.setPadding(left + dp(16), top, right + dp(16), bottom)
            ins
        }

        setContentView(FrameLayout(this).apply {
            addView(root, FrameLayout.LayoutParams(-1, -1))
            addView(buildOrbScreen(), FrameLayout.LayoutParams(-1, -1))
        })
        root.requestApplyInsets()

        // zaroori permissions, phir assistant shuru
        val need = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE) +
            (if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList())
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 1)
        } else {
            startSvc()
        }
    }

    // ---------- bataon ke kaam ----------
    private fun sendInput() {
        var t = input.text.toString().trim()
        if (t.isEmpty() && pendAtt == null && (pendMode.isEmpty() || pendMode == "learn")) {
            openOrb()
            return
        }
        if (t.isEmpty()) t = "Isme kya hai? Aasaan shabdon mein batao."
        if (runSpecial(t)) { input.text?.clear(); return }
        val e = AssistantService.engine
        if (e == null) {
            Toast.makeText(this, "Thoda ruko, assistant shuru ho raha hai", Toast.LENGTH_SHORT).show()
            return
        }
        val att = pendAtt
        pendAtt = null
        refreshHints()
        e.sendText(t, false, att)
        input.text?.clear()
        input.requestFocus()
        input.post {
            input.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, 0)
        }
    }

    private fun startSvc() {
        AssistantService.ensure(this)
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startSvc()
            attach(15)
        } else {
            Toast.makeText(this, "Mic ki permission ke bina assistant nahi chalega", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        attach(15)
        autoSecurity()
    }

    // App khulte hi phone ki jaanch (peeche chalti hai), shak wala kuch mile tabhi dikhata hai
    private fun autoSecurity() {
        val p = getSharedPreferences("p", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("sec_last", 0L) < 5 * 60 * 1000L) return
        Thread {
            val list = try { Security.autoCheck(applicationContext, p) } catch (e: Exception) { null }
            if (!list.isNullOrEmpty()) runOnUiThread {
                if (isFinishing) return@runOnUiThread
                showWarnings(list, 0)
            }
        }.start()
    }

    // ek app ki chetavni ek baar mein; band karo to agli dikhti hai
    private fun showWarnings(list: List<Security.Finding>, i: Int) {
        if (i >= list.size || isFinishing) return
        val f = list[i]
        try {
            val d = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
            d.setCancelable(false)
            val root = FrameLayout(this)
            root.setBackgroundColor(Color.parseColor("#B3000000"))
            val card = FrameLayout(this)
            card.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#4A1B3A"), Color.parseColor("#2A1646"), Color.parseColor("#13263F"))).apply {
                cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.parseColor("#80FFFFFF"))
            }
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.setPadding(dp(22), dp(34), dp(22), dp(26))
            col.addView(WarnIcon(this), LinearLayout.LayoutParams(dp(64), dp(58)))
            col.addView(TextView(this).apply { text = "Warning!"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(2)) })
            col.addView(TextView(this).apply { text = f.label; textSize = 18f; setTextColor(Color.parseColor("#FFB3B3")); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(10)) })
            col.addView(TextView(this).apply { text = f.reasons.joinToString("\n"); textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setLineSpacing(0f, 1.15f) })
            card.addView(col, FrameLayout.LayoutParams(-1, -2))
            card.addView(TextView(this).apply {
                text = "\u2715"; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setOnClickListener { d.dismiss(); showWarnings(list, i + 1) }
            }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.START).apply { leftMargin = dp(6); topMargin = dp(6) })
            root.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { leftMargin = dp(22); rightMargin = dp(22) })
            d.setContentView(root)
            d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            d.show()
        } catch (e: Exception) { }
    }

    override fun onStop() {
        AssistantService.engine?.listeners?.remove(cb)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) AssistantService.stop()
        super.onDestroy()
    }

    // assistant thodi der mein tayyar hota hai, isliye kuch baar dobara koshish
    private fun attach(n: Int) {
        val e = AssistantService.engine
        if (e != null) {
            if (!e.listeners.contains(cb)) {
                rebuildChat(e.log.toString())
                e.listeners.add(cb)
            }
        } else if (n > 0) {
            h.postDelayed({ attach(n - 1) }, 300)
        }
    }

    // ---------- voice orb screen ----------
    private lateinit var orb: OrbView
    private lateinit var orbStatus: TextView
    private lateinit var orbScreen: FrameLayout
    private lateinit var micBtn: IconView
    private lateinit var orbInput: EditText
    private var micMuted = false
    private lateinit var orbList: LinearLayout
    private lateinit var orbScroll: ScrollView
    private lateinit var sendBtn: IconView
    private lateinit var closeBtn: IconView
    private lateinit var bottomRowV: LinearLayout
    private lateinit var topRowV: FrameLayout
    private var orbChat = false
    private var orbMoved = false
    private var orbHomeTy = 0f
    private var orbBase = 1f
    private var insTop = 0
    private var insBottom = 0

    // orb ko sahi jagah par rakho: beech mein ya chhota hokar neeche (baat-cheet dikhne par)
    private fun layoutOrb(animate: Boolean) {
        if (!::orbScreen.isInitialized) return
        val h = orbScreen.height.toFloat()
        val w = orbScreen.width.toFloat()
        if (h <= 0f) return
        val rowTop = h - insBottom - dp(16) - dp(43)
        val topBottom = insTop + dp(20) + dp(40)
        val scale = if (orbChat) 0.44f else 1f
        val smallR = minOf(w, h) * 0.253f * 0.44f
        val centerY = if (orbChat) rowTop - dp(26) - smallR else (topBottom + rowTop) / 2f
        orb.pivotX = w / 2f
        orb.pivotY = h * 0.51f
        val ty = centerY - h * 0.51f
        orbHomeTy = ty
        orbBase = scale
        if (orbMoved) {
            // orb ko tumne kheencha hai: wahin rehne do, sirf list ki jagah theek karo
        } else if (animate) {
            orb.animate().scaleX(scale).scaleY(scale).translationX(0f).translationY(ty).setDuration(340)
                .setInterpolator(android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)).start()
        } else {
            orb.animate().cancel()
            orb.scaleX = scale; orb.scaleY = scale; orb.translationX = 0f; orb.translationY = ty
        }
        val lp = orbScroll.layoutParams as FrameLayout.LayoutParams
        val nt = insTop + dp(76)
        val nb = (h - rowTop + dp(8)).toInt()
        if (lp.topMargin != nt || lp.bottomMargin != nb) {
            lp.topMargin = nt
            lp.bottomMargin = nb
            orbScroll.layoutParams = lp
        }
        if (animate) {
            if (orbChat) {
                orbScroll.visibility = View.VISIBLE
                orbScroll.animate().alpha(1f).setDuration(300).start()
            } else {
                orbScroll.animate().alpha(0f).setDuration(200).withEndAction { if (!orbChat) orbScroll.visibility = View.GONE }.start()
            }
        } else {
            orbScroll.animate().cancel()
            orbScroll.alpha = if (orbChat) 1f else 0f
            orbScroll.visibility = if (orbChat) View.VISIBLE else View.GONE
        }
    }

    private fun toggleOrbChat() {
        orbChat = !orbChat
        orbMoved = false
        layoutOrb(true)
    }

    private fun orbLabel(m: Int) = when (m) {
        1 -> "Sun raha hoon..."
        2 -> "Soch raha hoon..."
        3 -> "Bol raha hoon..."
        else -> if (micMuted) "Mic band hai" else "Bolo, main sun raha hoon..."
    }

    private fun oval(color: String) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(color))
    }

    private fun buildOrbScreen(): FrameLayout {
        orb = OrbView(this)
        orbStatus = TextView(this).apply {
            text = orbLabel(0)
            setTextColor(Color.parseColor("#9AA3B5"))
            textSize = 14f
            visibility = View.GONE
            gravity = Gravity.CENTER
        }
        micBtn = icon(12, 43) { toggleMute() }
        micBtn.tint = Color.WHITE
        micBtn.background = oval("#303030")
        val close = icon(1, 43) { closeOrb() }
        closeBtn = close
        close.rotation = 45f
        close.tint = Color.BLACK
        close.background = oval("#FFFFFF")
        val plus = icon(1, 30) { showPlus() }

        orbInput = EditText(this).apply {
            hint = "Ask AI"
            setHintTextColor(Color.parseColor("#8D8D95"))
            setTextColor(Color.WHITE)
            textSize = 16f
            background = null
            // enter dabane par nayi line (ChatGPT jaisa); bhejne ke liye upar wala teer
            maxLines = 4
            imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) {
                    val has = !e.isNullOrBlank()
                    sendBtn.visibility = if (has) View.VISIBLE else View.GONE
                    micBtn.visibility = if (has) View.GONE else View.VISIBLE
                    closeBtn.visibility = if (has) View.GONE else View.VISIBLE
                }
            })
        }
        sendBtn = icon(4, 36) { sendOrbText() }
        sendBtn.layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        sendBtn.tint = Color.WHITE
        sendBtn.background = oval("#2F6BFF")
        sendBtn.visibility = View.GONE
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#303030"))
                setStroke(dp(1), Color.parseColor("#3C3C3C"))
            }
            setPadding(dp(8), 0, dp(9), 0)
            addView(plus, LinearLayout.LayoutParams(dp(30), dp(30)))
            addView(orbInput, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
            addView(sendBtn, LinearLayout.LayoutParams(dp(36), dp(36)).apply { leftMargin = dp(6) })
        }
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(pill, LinearLayout.LayoutParams(0, dp(43), 1f))
            addView(micBtn, LinearLayout.LayoutParams(dp(43), dp(43)).apply { leftMargin = dp(8) })
            addView(close, LinearLayout.LayoutParams(dp(43), dp(43)).apply { leftMargin = dp(8) })
        }

        val menuBtn = icon(0, 40) { showDrawer() }
        menuBtn.background = oval("#303030")
        val setBtn = icon(11, 40) { Toast.makeText(this, "Jaldi aayega", Toast.LENGTH_SHORT).show() }
        setBtn.background = oval("#303030")
        val topRow = FrameLayout(this).apply {
            addView(menuBtn, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START))
            addView(setBtn, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END))
        }

        var dragging = false
        var dX = 0f; var dY = 0f; var sTx = 0f; var sTy = 0f; var inside = false
        orb.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    inside = orb.hit(e.x, e.y)
                    if (inside) {
                        orb.animate().cancel()
                        dragging = false
                        dX = e.rawX; dY = e.rawY
                        sTx = orb.translationX; sTy = orb.translationY
                    }
                    inside
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!inside) return@setOnTouchListener false
                    val mx = e.rawX - dX
                    val my = e.rawY - dY
                    if (!dragging && (mx * mx + my * my) > dp(10) * dp(10)) dragging = true
                    if (dragging) {
                        // rubber jaisa: ungli ke saath thoda hi khinchta hai, jitna zyada kheencho utna kadak
                        // orb apni jagah par fix rehta hai; bas ungli wali taraf se halka sa khinchta hai
                        val mxs = dp(90).toFloat()
                        fun rub(d: Float) = (if (d < 0) -1f else 1f) * (1f - Math.exp((-Math.abs(d) / mxs).toDouble()).toFloat())
                        val nx = rub(mx)
                        val ny = rub(my)
                        val ax = Math.abs(nx)
                        val ay = Math.abs(ny)
                        val big = 0.13f
                        val r0 = minOf(orbScreen.width, orbScreen.height) * 0.253f * orbBase
                        orb.scaleX = orbBase * (1f + big * ax) * (1f - 0.05f * ay)
                        orb.scaleY = orbBase * (1f + big * ay) * (1f - 0.05f * ax)
                        orb.translationX = nx * big * r0 * ax.coerceAtLeast(0.01f) / ax.coerceAtLeast(0.01f) * ax
                        orb.translationY = orbHomeTy + ny * big * r0 * ay / ay.coerceAtLeast(0.01f) * ay
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (inside && !dragging) toggleOrbChat()
                    else if (inside) {
                        orb.animate().scaleX(orbBase).scaleY(orbBase).translationX(0f).translationY(orbHomeTy).setDuration(600)
                            .setInterpolator(android.view.animation.OvershootInterpolator(3.2f)).start()
                    }
                    inside = false
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    if (inside && dragging) orb.animate().scaleX(orbBase).scaleY(orbBase).translationX(0f).translationY(orbHomeTy).setDuration(400).start()
                    inside = false; true
                }
                else -> false
            }
        }
        orbList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        orbScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            visibility = View.GONE
            setPadding(dp(16), 0, dp(16), 0)
            clipToPadding = false
            addView(orbList, ViewGroup.LayoutParams(-1, -2))
        }
        orbScreen = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            isClickable = true
            addView(orbScroll, FrameLayout.LayoutParams(-1, -1))
            addView(orb, FrameLayout.LayoutParams(-1, -1))
            addView(orbStatus, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(100) })
            addView(bottomRow, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                leftMargin = dp(32); rightMargin = dp(32); bottomMargin = dp(16)
            })
            addView(topRow, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
                leftMargin = dp(13); rightMargin = dp(13); topMargin = dp(20)
            })
            // status bar / navigation bar / keyboard ke hisaab se upar-neeche ki jagah
            addOnLayoutChangeListener { v, l, t, r, b, ol, ot, orr, ob ->
                if (r - l != orr - ol || b - t != ob - ot) layoutOrb(false)
            }
            setOnApplyWindowInsetsListener { _, ins ->
                val top: Int
                val bottom: Int
                if (Build.VERSION.SDK_INT >= 30) {
                    val sb = ins.getInsets(WindowInsets.Type.systemBars())
                    val ime = ins.getInsets(WindowInsets.Type.ime())
                    top = sb.top
                    bottom = maxOf(sb.bottom, ime.bottom)
                } else {
                    top = ins.systemWindowInsetTop
                    bottom = ins.systemWindowInsetBottom
                }
                topRow.translationY = top.toFloat()
                bottomRow.translationY = -bottom.toFloat()
                orbStatus.translationY = -bottom.toFloat()
                insTop = top
                insBottom = bottom
                layoutOrb(true)
                ins
            }
        }
        return orbScreen
    }

    private fun sendOrbText() {
        var t = orbInput.text.toString().trim()
        if (t.isEmpty()) return
        if (runSpecial(t)) { orbInput.text?.clear(); return }
        val e = AssistantService.engine
        if (e == null) {
            Toast.makeText(this, "Assistant shuru nahi hua", Toast.LENGTH_SHORT).show()
            return
        }
        val att = pendAtt
        pendAtt = null
        refreshHints()
        e.sendText(t, true, att)
        orbInput.text?.clear()
    }

    private fun openOrb() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        volumeControlStream = AudioManager.STREAM_VOICE_CALL
        orbList.removeAllViews()
        orbChat = false
        orbMoved = false
        orbInput.text?.clear()
        orbScreen.visibility = View.VISIBLE
        orbScreen.requestApplyInsets()
        layoutOrb(false)
        orb.invalidate()
        micMuted = false
        styleMic()
        orbStatus.text = orbLabel(0)
        AssistantService.engine?.startConvo()
    }

    private fun closeOrb() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(orbInput.windowToken, 0)
        orbInput.clearFocus()
        volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
        orbScreen.visibility = View.GONE
        AssistantService.engine?.stopConvo()
    }

    private fun styleMic() {
        micBtn.kind = 12
        micBtn.tint = Color.WHITE
        micBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(if (micMuted) "#D32F2F" else "#303030"))
        }
        micBtn.invalidate()
    }

    private fun toggleMute() {
        micMuted = !micMuted
        styleMic()
        orbStatus.text = orbLabel(0)
        AssistantService.engine?.setMuted(micMuted)
    }

    override fun onPause() {
        super.onPause()
        // app peeche jaye to mic band (background mein nahi chalta)
        if (::orbScreen.isInitialized && orbScreen.visibility == View.VISIBLE) closeOrb()
    }

    override fun onBackPressed() {
        if (orbScreen.visibility == View.VISIBLE) closeOrb() else super.onBackPressed()
    }

    private fun talk() {
        val e = AssistantService.engine
        if (e == null) {
            startSvc()
            h.postDelayed({ attach(15) }, 300)
            return
        }
        e.talk()
    }
}
