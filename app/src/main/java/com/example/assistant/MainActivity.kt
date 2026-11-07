package com.example.assistant

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
            2 -> {
                c.drawRoundRect(RectF(9f, 3f, 15f, 14f), 3f, 3f, p)
                c.drawArc(RectF(5f, 4f, 19f, 18f), 0f, 180f, false, p)
                c.drawLine(12f, 18f, 12f, 21f, p)
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
        } else if (t.startsWith("AI:")) {
            removePending()
            addAi(t.removePrefix("AI:").trim())
        }
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
        header.addView(icon(0, 44) { soon() })
        val title = TextView(this).apply {
            val s = SpannableString("AI Auto")
            s.setSpan(ForegroundColorSpan(Color.parseColor("#9AA3B5")), 3, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = s
            textSize = 19f
            setTextColor(Color.parseColor("#E8EAF0"))
        }
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
        bar.addView(icon(1, 44) { soon() })
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
            Manifest.permission.CALL_PHONE)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 1)
        } else {
            startSvc()
        }
    }

    // ---------- bataon ke kaam ----------
    private fun sendInput() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) {
            openOrb()
            return
        }
        val e = AssistantService.engine
        if (e == null) {
            Toast.makeText(this, "Thoda ruko, assistant shuru ho raha hai", Toast.LENGTH_SHORT).show()
            return
        }
        e.sendText(t)
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
            gravity = Gravity.CENTER
        }
        micBtn = icon(2, 54) { toggleMute() }
        micBtn.tint = Color.WHITE
        micBtn.background = oval("#2B2B2F")
        val close = icon(1, 54) { closeOrb() }
        close.rotation = 45f
        close.tint = Color.BLACK
        close.background = oval("#FFFFFF")
        val plus = icon(1, 40) { Toast.makeText(this, "Jaldi aayega", Toast.LENGTH_SHORT).show() }

        orbInput = EditText(this).apply {
            hint = "Ask AI"
            setHintTextColor(Color.parseColor("#8D8D95"))
            setTextColor(Color.WHITE)
            textSize = 17f
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setOnEditorActionListener { _, a, _ ->
                if (a == EditorInfo.IME_ACTION_SEND) { sendOrbText(); true } else false
            }
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(27).toFloat()
                setColor(Color.parseColor("#2B2B2F"))
            }
            setPadding(dp(8), 0, dp(16), 0)
            addView(plus, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(orbInput, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        }
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(pill, LinearLayout.LayoutParams(0, dp(54), 1f))
            addView(micBtn, LinearLayout.LayoutParams(dp(54), dp(54)).apply { leftMargin = dp(10) })
            addView(close, LinearLayout.LayoutParams(dp(54), dp(54)).apply { leftMargin = dp(10) })
        }

        val menuBtn = icon(0, 50) { Toast.makeText(this, "Jaldi aayega", Toast.LENGTH_SHORT).show() }
        menuBtn.background = oval("#2B2B2F")
        val setBtn = icon(11, 50) { Toast.makeText(this, "Jaldi aayega", Toast.LENGTH_SHORT).show() }
        setBtn.background = oval("#2B2B2F")
        val topRow = FrameLayout(this).apply {
            addView(menuBtn, FrameLayout.LayoutParams(dp(50), dp(50), Gravity.START))
            addView(setBtn, FrameLayout.LayoutParams(dp(50), dp(50), Gravity.END))
        }

        orb.setOnClickListener { talk() }
        orbScreen = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            isClickable = true
            addView(orb, FrameLayout.LayoutParams(-1, -1))
            addView(orbStatus, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(100) })
            addView(bottomRow, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                leftMargin = dp(20); rightMargin = dp(20); bottomMargin = dp(24)
            })
            addView(topRow, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
                leftMargin = dp(20); rightMargin = dp(20); topMargin = dp(12)
            })
            // status bar / navigation bar / keyboard ke hisaab se upar-neeche ki jagah
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
                ins
            }
        }
        return orbScreen
    }

    private fun sendOrbText() {
        val t = orbInput.text.toString().trim()
        if (t.isEmpty()) return
        val e = AssistantService.engine
        if (e == null) {
            Toast.makeText(this, "Assistant shuru nahi hua", Toast.LENGTH_SHORT).show()
            return
        }
        e.sendText(t, true)
        orbInput.text?.clear()
    }

    private fun openOrb() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        orbScreen.visibility = View.VISIBLE
        orbScreen.requestApplyInsets()
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
        orbScreen.visibility = View.GONE
        AssistantService.engine?.stopConvo()
    }

    private fun styleMic() {
        micBtn.tint = if (micMuted) Color.parseColor("#8A8F9A") else Color.WHITE
        micBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(if (micMuted) "#4A1D1D" else "#26272C"))
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
