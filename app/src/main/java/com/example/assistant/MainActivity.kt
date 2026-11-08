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
            addAi(t.removePrefix("AI:").trim())
            if (::orbList.isInitialized) orbAdd(false, t.removePrefix("AI:").trim())
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
    private fun addUser(text: String) {
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
    private fun addAi(text: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tv = TextView(this).apply {
            this.text = text
            textSize = 16.5f
            setTextColor(Color.parseColor("#E8EAF0"))
            setLineSpacing(0f, 1.25f)
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
        acts.addView(icon(9, 44) { soon() })
        acts.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        acts.addView(icon(10, 44) { AssistantService.engine?.replay(text) })
        box.addView(acts, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            leftMargin = -dp(10)
            rightMargin = dp(4)
        })
        chatList.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            bottomMargin = dp(6)
        })
        scrollDown()
    }

    // Purani baat-cheet dobara dikhao (ऐप wapas kholne par)
    private fun rebuildChat(log: String) {
        chatList.removeAllViews()
        pending = null
        for (part in log.split(Regex("\n(?=(?:Tum|AI): )"))) {
            val t = part.trim()
            if (t.startsWith("Tum:")) addUser(t.removePrefix("Tum:").trim())
            else if (t.startsWith("AI:")) addAi(t.removePrefix("AI:").trim())
        }
    }


    // ---------- "+" menu: photo, camera, file, map, image/video/music banana ----------
    private var pendAtt: Pair<String, String>? = null
    private var pendName = ""
    private var pendMode = ""
    private var camUri: android.net.Uri? = null
    private var player: android.media.MediaPlayer? = null

    private fun refreshHints() {
        val t = when {
            pendMode == "image" -> "Kaisi image banani hai?"
            pendMode == "video" -> "Kaisa video banana hai?"
            pendMode == "music" -> "Kaisa gaana banana hai?"
            pendMode == "map" -> "Kaunsi jagah ya raasta?"
            pendMode == "research" -> "Kis baare mein report chahiye?"
            pendMode == "agent" -> "Agent ko kya kaam dena hai?"
            pendAtt != null -> "\uD83D\uDCCE " + pendName + " - sawal likho"
            else -> "Ask AI"
        }
        input.hint = t
        if (::orbInput.isInitialized) orbInput.hint = t
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
            chip(13, "Photos") { pick(101) },
            chip(14, "Camera") { camera() },
            chip(15, "Files") { pick(103) },
            chip(16, "Map") { setMode("map") }
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
        col.addView(item(17, "Create images", "Image banao ya photo badlo") { setMode("image") })
        col.addView(item(18, "Video", "Video banao") { setMode("video") })
        col.addView(item(19, "Create music", "Gaana banao") { setMode("music") })
        val eng = AssistantService.engine
        col.addView(item(20, "Deep Research", "Poori jaanch ke saath lamba report") { setMode("research") })
        col.addView(item(20, "Agent", "Google ka agent kai kadam mein kaam karta hai") { setMode("agent") })
        col.addView(item(21, "Guided Learning", "Kadam-kadam par sikho", if (eng?.learn == true) "ON" else "OFF") {
            val e = AssistantService.engine
            if (e != null) {
                e.learn = !e.learn
                Toast.makeText(this, if (e.learn) "Guided Learning chalu" else "Guided Learning band", Toast.LENGTH_SHORT).show()
            }
        })
        col.addView(item(22, "Personal Intelligence", "Tumhari yaad ki hui baatein use kare", if (eng?.personal != false) "ON" else "OFF") {
            val e = AssistantService.engine
            if (e != null) {
                e.personal = !e.personal
                Toast.makeText(this, if (e.personal) "Personal Intelligence chalu" else "Personal Intelligence band", Toast.LENGTH_SHORT).show()
            }
        })
        if (pendAtt != null || pendMode.isNotEmpty()) {
            col.addView(item(1, "Hatao", "Jo jodha hai use hata do") { pendAtt = null; pendMode = ""; refreshHints() })
        }
        dlg.setContentView(col)
        dlg.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        dlg.show()
    }

    private fun setMode(m: String) {
        pendMode = m
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
        pendAtt = Pair("image/jpeg", android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP))
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
        if (mode.isEmpty()) return false
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

    private fun saveGenerated(out: Media.Out) {
        try {
            val ext = if (out.mime.contains("png")) "png" else if (out.mime.contains("webp")) "webp" else "jpg"
            java.io.File(genDir, "img_" + System.currentTimeMillis() + "." + ext).writeBytes(out.bytes)
        } catch (e: Exception) { }
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
        dlg.setContentView(col)
        dlg.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.78f).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.START)
        }
        dlg.show()
    }

    // Security log: din ke hisaab se wo app jinpar shak hua tha, aur sunne ke liye speaker
    private fun showSecurityLog() {
        val dlg = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
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
        val nice = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH)
        val timeFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH)
        val today = dayFmt.format(java.util.Date())
        val yest = dayFmt.format(java.util.Date(System.currentTimeMillis() - 86400000L))
        var last = ""
        for (e in list) {
            val d = dayFmt.format(java.util.Date(e.t))
            if (d != last) {
                last = d
                val head = when (d) { today -> "Aaj"; yest -> "Kal"; else -> nice.format(java.util.Date(e.t)) }
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
            tv.addView(TextView(this).apply {
                text = e.label + (if (e.fresh) "  \u2022 naya app" else "")
                textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            })
            tv.addView(TextView(this).apply {
                text = (if (high) "Zyada shak" else "Thoda shak") + "  \u2022  " + timeFmt.format(java.util.Date(e.t)) + "  \u2022  " + e.pkg
                textSize = 12f; setTextColor(Color.parseColor(if (high) "#FF8A8A" else "#E5C06A"))
            })
            tv.addView(TextView(this).apply {
                text = e.text; textSize = 14f; setTextColor(Color.parseColor("#D0D0D6")); setPadding(0, dp(6), 0, 0)
            })
            card.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(icon(10, 44) { AssistantService.engine?.replay(e.text) },
                LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(6) })
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        dlg.setContentView(root)
        dlg.show()
    }

    private val trending = listOf(
        "Anime reaction" to "An anime style cartoon character with big heart eyes, excited happy face, sparkles around, clean colourful background",
        "Diwali wishes" to "A beautiful Happy Diwali greeting card with a glowing diya, colourful rangoli, golden text and warm lights",
        "Diwali decor" to "A cosy Indian living room decorated for Diwali with marigold garlands, diyas and cushions, warm light",
        "80s flashback" to "A retro 1980s style portrait of an Indian man with big sunglasses and a colourful shirt, film grain, vintage photo",
        "Movie poster" to "A colourful Bollywood style movie poster of a road trip comedy with a big bold title and a smiling hero beside a yellow taxi",
        "Rickshaw selfie" to "Two friends laughing in a colourful auto rickshaw taking a selfie, warm evening light, realistic photo",
        "Comic hero" to "A comic book cover of a neighbourhood kite rescue adventure with kids, bright colours, bold title",
        "Indian mixtape" to "A cassette tape and handwritten tracklist lying on a wooden table at dusk, warm moody light, realistic photo",
        "Good morning" to "A steaming cup of chai on a wooden table with a notebook, flowers and a sunrise over green hills, good morning photo",
        "Cricket victory" to "A joyful crowd celebrating a cricket victory in a floodlit stadium, a player raising her arms, cinematic",
        "Caricature" to "A fun cartoon caricature of a doctor and artist with a big smile, books and a paint palette, blue sky background",
        "Stickers" to "A set of cute die cut stickers: a black cat, a potted plant and a smiling bearded man, white borders",
        "Sketch" to "A simple hand drawn black line sketch of a flower with a small bee, clean white paper",
        "3D avatar" to "A cute 3D avatar of a young woman with orange sunglasses and flower hairclips, soft clouds background",
        "Tarot card" to "A tarot card style illustration of a woman in a green cloak and cap with a sun and moon, vintage print",
        "Impressionist painting" to "An impressionist oil painting of a woman looking at a sunset sea from a boat, thick brush strokes",
        "16-bit arcade" to "A 16 bit pixel art fighting game hero with silver hair and red jacket on a night city bridge",
        "Wallpaper" to "A hyperreal macro wallpaper of a green fern curling in a spiral, deep forest light"
    )
    private val templates = listOf(
        "Poster" to "A bold modern poster with a big red poppy flower and large title text BLOOM, cream background",
        "Interior design" to "An isometric miniature living room with big window, sofa, plants and wall art, soft pastel background",
        "Logo" to "A clean round blue badge logo with a simple fish symbol, flat vector style",
        "Illustration" to "A warm flat illustration of a girl hugging her cat, soft colours, storybook style",
        "Headshot" to "A professional studio headshot of a smiling person in a cream sweater, soft grey background",
        "Icon" to "A set of four friendly flat icons: peace hand, shooting star, frog and planet, on a teal background",
        "Product photo" to "A product photo of a pink scented candle on a pink background with soft shadow, studio light",
        "Merch" to "A white t-shirt hanging on a clothesline with a cool frog DJ print, blue sky background",
        "Infographic" to "A simple clear infographic explaining how rain forms from a cloud, with arrows and labels",
        "Flyer" to "A black and white lost pet flyer with a big title, a photo and a phone number, photocopy style",
        "Book cover" to "A book cover with a sunset over the sea and a deck chair, calm teal colours, elegant title text",
        "App mockup" to "A music player app screen mockup on a phone with colourful album art and play controls",
        "Packaging" to "A tea box packaging design with green leaf art and the name LEAF, peach background",
        "Advertisement" to "A bright pink advertisement for a skincare bottle held by a hand, with bold script text Shop now",
        "Thumbnail" to "A social media thumbnail of a smiling chef holding a pastry with big white text one day in my life",
        "Website mockup" to "A website homepage mockup for a pottery shop with warm terracotta colours and product photos"
    )
    private val palette = listOf(
        "#5B247A" to "#1BCEDF", "#B24592" to "#F15F79", "#0F2027" to "#2C5364", "#134E5E" to "#71B280",
        "#8E2DE2" to "#4A00E0", "#C33764" to "#1D2671", "#CC5333" to "#23074D", "#1F4037" to "#4CA58A"
    )

    private fun showImages() {
        val dlg = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(96), dp(16), dp(110))
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(content)
        root.addView(sv, FrameLayout.LayoutParams(-1, -1))

        val topBar = FrameLayout(this)
        topBar.setBackgroundColor(Color.BLACK)
        val back = icon(23, 40) { dlg.dismiss() }
        back.background = oval("#303030")
        topBar.addView(back, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.BOTTOM).apply { leftMargin = dp(16); bottomMargin = dp(10) })
        topBar.addView(TextView(this).apply { text = "Images"; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply { bottomMargin = dp(18) })
        root.addView(topBar, FrameLayout.LayoutParams(-1, dp(88), Gravity.TOP))

        val et = EditText(this).apply {
            hint = "Describe an image"
            setHintTextColor(Color.parseColor("#8D8D95"))
            setTextColor(Color.WHITE)
            textSize = 16f
            background = null
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        var tab = 0
        var busyImg = false

        lateinit var render: () -> Unit
        val send = {
            val t = et.text.toString().trim()
            val key = Keys.get(getSharedPreferences("p", Context.MODE_PRIVATE))
            if (t.isNotEmpty() && !busyImg) {
                if (key.isEmpty()) {
                    Toast.makeText(this, "API key nahi mili", Toast.LENGTH_SHORT).show()
                } else {
                    busyImg = true
                    et.text?.clear()
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(et.windowToken, 0)
                    render()
                    kotlin.concurrent.thread {
                        val out = Media.image(key, t, null, Models.sel(getSharedPreferences("p", Context.MODE_PRIVATE), "photo"))
                        val err = Media.lastErr
                        runOnUiThread {
                            busyImg = false
                            if (out != null) saveGenerated(out)
                            else Toast.makeText(this, "Nahi ban paayi. " + err.ifBlank { "Dobara try karo." }, Toast.LENGTH_LONG).show()
                            render()
                        }
                    }
                }
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
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(18) })

        fun viewer(f: java.io.File) {
            val v = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val iv = android.widget.ImageView(this)
            iv.setBackgroundColor(Color.BLACK)
            iv.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            iv.setImageBitmap(android.graphics.BitmapFactory.decodeFile(f.path))
            iv.setOnClickListener { v.dismiss() }
            v.setContentView(iv)
            v.show()
        }

        render = {
            content.removeAllViews()
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

            val list = if (tab == 0) trending else templates
            var i = 0
            while (tab < 2 && i < list.size) {
                val row = LinearLayout(this)
                for (j in 0..1) {
                    val idx = i + j
                    if (idx < list.size) {
                        val (c1, c2) = palette[idx % palette.size]
                        val card = FrameLayout(this)
                        card.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(c1), Color.parseColor(c2))).apply { cornerRadius = dp(24).toFloat() }
                        card.addView(TextView(this).apply {
                            text = list[idx].first
                            textSize = 17f
                            setTextColor(Color.WHITE)
                            typeface = Typeface.DEFAULT_BOLD
                            setPadding(dp(16), dp(14), dp(16), dp(14))
                        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
                        card.setOnClickListener {
                            et.setText(list[idx].second)
                            et.setSelection(et.text.length)
                            et.requestFocus()
                            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(et, 0)
                        }
                        row.addView(card, LinearLayout.LayoutParams(0, dp(120), 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
                    } else row.addView(View(this), LinearLayout.LayoutParams(0, dp(120), 1f))
                }
                content.addView(row)
                i += 2
            }

            if (tab == 2) {
            if (busyImg) content.addView(TextView(this).apply {
                text = "Image ban rahi hai..."
                textSize = 15f
                setTextColor(Color.parseColor("#9AA3B5"))
                setPadding(dp(4), 0, 0, dp(10))
            })
            val files = (genDir.listFiles() ?: emptyArray()).filter { it.isFile }.sortedByDescending { it.lastModified() }.take(60)
            if (files.isEmpty() && !busyImg) {
                content.addView(TextView(this).apply {
                    text = "Abhi koi image nahi hai. Neeche likhkar banao."
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
                        val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                        val iv = android.widget.ImageView(this)
                        iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        iv.setImageBitmap(android.graphics.BitmapFactory.decodeFile(f.path, o))
                        iv.clipToOutline = true
                        iv.outlineProvider = object : android.view.ViewOutlineProvider() {
                            override fun getOutline(v: View, ol: android.graphics.Outline) { ol.setRoundRect(0, 0, v.width, v.height, dp(14).toFloat()) }
                        }
                        iv.setOnClickListener { viewer(f) }
                        iv.setOnLongClickListener {
                            f.delete()
                            Toast.makeText(this, "Hata diya", Toast.LENGTH_SHORT).show()
                            render()
                            true
                        }
                        row.addView(iv, LinearLayout.LayoutParams(side, side).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                    }
                }
                content.addView(row)
                k += 3
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
        val c = Models.sel(p, "chat")
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
        var showFree = true

        lateinit var render: () -> Unit
        fun row(title: String, sub: String?, tag: String?, checked: Boolean, usage: Usage.Info? = null, hold: (() -> Unit)? = null, go: () -> Unit): View {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(14), dp(11), dp(16), dp(11))
            r.addView(TextView(this).apply {
                text = if (checked) "\u2713" else ""
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(34), -2))
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
            if (tag != null) r.addView(TextView(this).apply { text = tag; textSize = 12f; setTextColor(Color.parseColor("#9AA3B5")) })
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
            col.addView(row("Auto", "Kaam ke hisaab se apne aap chunega", null, none) {
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
            col.addView(line(), LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(14), dp(6), dp(14), dp(2)) })
            val sides = LinearLayout(this)
            fun side(isFree: Boolean, name: String): TextView = TextView(this).apply {
                text = name
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(if (showFree == isFree) Color.WHITE else Color.parseColor("#9AA3B5"))
                background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(if (showFree == isFree) Color.parseColor("#2F6BFF") else Color.parseColor("#2A2A2A")) }
                setOnClickListener { showFree = isFree; render() }
            }
            sides.addView(side(true, "Free"), LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(4) })
            sides.addView(side(false, "Paid"), LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(4) })
            col.addView(sides, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), dp(8), dp(14), dp(4)) })
            val all = Models.list(p).filter { it.free == showFree }
            for ((cat, label) in Models.CATS) {
                val items = all.filter { it.cat == cat }
                if (items.isEmpty()) continue
                col.addView(head(label))
                val cur = Models.sel(p, cat)
                for (mm in items) {
                    val on = cur != null && cur.key == mm.key
                    col.addView(row(mm.name, null, mm.tag, on, Usage.info(mm), if (mm.prov == "gemini" || mm.prov == "openai") ({ askLimit(mm) { render() } }) else null) {
                        Models.setSel(p, cat, if (on) null else mm)
                        refreshTitle(); render()
                    })
                }
                if (cat == "chat") col.addView(TextView(this).apply {
                    text = "Groq, OpenAI aur OpenRouter se sirf jawab milta hai, phone ke kaam nahi."
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
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(36).toFloat()
                setColor(Color.parseColor("#15181F"))
                setStroke(dp(1), Color.parseColor("#1F2633"))
            }
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
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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
        if (t.isEmpty() && pendAtt == null && pendMode.isEmpty()) {
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
            val msg = try { Security.autoCheck(applicationContext, p) } catch (e: Exception) { null }
            if (msg != null) runOnUiThread {
                if (isFinishing) return@runOnUiThread
                try {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Suraksha chetavni")
                        .setMessage(msg)
                        .setPositiveButton("Theek hai", null)
                        .setNeutralButton("Apps kholo") { _, _ ->
                            try { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)) } catch (e: Exception) { }
                        }
                        .show()
                } catch (e: Exception) { }
            }
        }.start()
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
