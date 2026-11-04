package com.example.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val TXT = "#E8FBFF"
    private val DIM = "#7FE9FF"

    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var keyBox: EditText
    private lateinit var scroll: ScrollView
    private lateinit var orb: OrbView
    private lateinit var wave: WaveView
    private lateinit var homeBox: View
    private lateinit var chatBox: View
    private lateinit var settingsBox: View
    private lateinit var replyQ: TextView
    private lateinit var replyA: TextView
    private lateinit var netTv: TextView
    private lateinit var ambientSw: Switch
    private val rows = mutableListOf<LinearLayout>()
    private val labels = mutableListOf<TextView>()
    private val menuNames = listOf("Home", "Chat", "AI Tools", "Settings", "History", "Favorites", "About")
    private val menuIcons = listOf("🏠", "💬", "🧩", "⚙", "🕘", "⭐", "ℹ")
    private val h = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("p", Context.MODE_PRIVATE) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    // सर्विस से आने वाली खबरें स्क्रीन पर दिखाता है
    private val cb = object : UiCb {
        override fun onStatus(s: String) { status.text = s }
        override fun onChat(s: String) { handleChat(s) }
        override fun onMode(m: Int) { setMode(m) }
        override fun onLevel(l: Float) { orb.level = l; wave.level = l }
    }

    private fun card(radius: Float): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Neon.panel(radius, 3)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun label(t: String, size: Float, color: String, bold: Boolean): TextView = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(Color.parseColor(color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun iconBtn(sym: String, sizeDp: Int, textSz: Float, onClick: () -> Unit): Button = Button(this).apply {
        text = sym
        textSize = textSz
        background = Neon.circle()
        setTextColor(Neon.CYAN)
        isAllCaps = false
        minWidth = 0
        minHeight = 0
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun soon() {
        Toast.makeText(this, "Ye jald aayega", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.statusBarColor = Neon.BG
        window.navigationBarColor = Neon.BG
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Neon.BG)
            setPadding(dp(10), dp(6), dp(10), dp(8))
        }

        // ---------- ऊपर की पट्टी ----------
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Neon.panel(14f, 3)
            setPadding(dp(14), dp(5), dp(14), dp(5))
        }
        val title = label("◉  AI Assistant", 16f, "#19D8FF", true)
        val clock = TextClock(this).apply {
            format12Hour = "hh:mm a"
            format24Hour = "hh:mm a"
            setTextColor(Color.parseColor(TXT))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        topBar.addView(title, lp(0, WRAP, 1f))
        topBar.addView(clock)
        root.addView(topBar, lp(MATCH, WRAP))

        // ---------- बीच का हिस्सा ----------
        val mid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // बाईं तरफ मेन्यू
        val menu = card(18f)
        menu.setPadding(dp(8), dp(6), dp(8), dp(6))
        for (i in menuNames.indices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { menuClick(i) }
            }
            val ic = TextView(this).apply {
                text = menuIcons[i]
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Neon.CYAN)
            }
            val lb = label(menuNames[i], 13f, TXT, false)
            lb.setPadding(dp(8), 0, 0, 0)
            row.addView(ic, lp(dp(22), WRAP))
            row.addView(lb, lp(0, WRAP, 1f))
            rows.add(row)
            labels.add(lb)
            menu.addView(row, lp(MATCH, 0, 1f).apply { topMargin = dp(2) })
        }
        mid.addView(menu, lp(0, MATCH, 2.1f))

        val pages = FrameLayout(this)

        // ---- होम पेज ----
        val home = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        orb = OrbView(this)
        val shortSide = minOf(resources.displayMetrics.heightPixels, resources.displayMetrics.widthPixels)
        val orbSize = (shortSide - dp(240)).coerceAtLeast(dp(90))
        center.addView(orb, lp(orbSize, orbSize))
        val pill = TextView(this).apply {
            text = "AI ASSISTANT"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            background = Neon.panel(14f, 3)
            setPadding(dp(24), dp(5), dp(24), dp(5))
        }
        center.addView(pill, lp(WRAP, WRAP).apply { topMargin = dp(2) })
                status = label("Taiyaar v44", 13f, DIM, false)
        status.gravity = Gravity.CENTER
        center.addView(status, lp(MATCH, WRAP).apply { topMargin = dp(2) })
        wave = WaveView(this)
        center.addView(wave, lp(MATCH, dp(18)))
        home.addView(center, lp(0, MATCH, 4.6f))

        // दाईं तरफ तीन डिब्बे
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val replyCard = card(18f)
        replyQ = label("", 11f, DIM, false)
        replyQ.maxLines = 2
        replyA = label("Mic dabao aur bolo", 15f, TXT, true)
        val replyScroll = ScrollView(this)
        val replyInner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        replyInner.addView(replyQ, lp(MATCH, WRAP))
        replyInner.addView(replyA, lp(MATCH, WRAP))
        replyScroll.addView(replyInner)
        replyCard.addView(replyScroll, lp(MATCH, MATCH))
        right.addView(replyCard, lp(MATCH, 0, 1.2f))

        val brainCard = card(18f)
        brainCard.orientation = LinearLayout.HORIZONTAL
        brainCard.gravity = Gravity.CENTER_VERTICAL
        val brainIc = TextView(this).apply {
            text = "🧠"
            textSize = 32f
            gravity = Gravity.CENTER
        }
        val bullets = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (t in listOf("Gemini AI", "Hindi awaaz", "Hamesha yaad", "Phone control")) {
            bullets.addView(label("●  $t", 12f, "#19D8FF", true), lp(WRAP, WRAP))
        }
        brainCard.addView(brainIc, lp(dp(56), WRAP))
        brainCard.addView(bullets, lp(0, WRAP, 1f))
        right.addView(brainCard, lp(MATCH, 0, 1f).apply { topMargin = dp(6) })

        val netCard = card(18f)
        netCard.orientation = LinearLayout.HORIZONTAL
        netCard.gravity = Gravity.CENTER_VERTICAL
        val globe = TextView(this).apply {
            text = "🌐"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        netTv = label("", 12f, TXT, true)
        netCard.addView(globe, lp(dp(56), WRAP))
        netCard.addView(netTv, lp(0, WRAP, 1f))
        right.addView(netCard, lp(MATCH, 0, 0.8f).apply { topMargin = dp(6) })
        home.addView(right, lp(0, MATCH, 3.2f).apply { leftMargin = dp(8) })
        homeBox = home
        pages.addView(home, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- चैट पेज ----
        output = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#CFF6FF"))
            setLineSpacing(0f, 1.15f)
        }
        scroll = ScrollView(this).apply {
            background = Neon.panel(18f, 3)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(output)
        }
        chatBox = scroll
        pages.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- सेटिंग पेज ----
        val keyPanel = card(18f)
        keyBox = EditText(this).apply {
            hint = "Gemini API key yahan paste karo"
            setHintTextColor(Color.parseColor("#4A90A4"))
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("key", ""))
        }
        val save = Button(this).apply {
            text = "Key save karo"
            Neon.styleButton(this)
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setOnClickListener { saveKey() }
        }
        ambientSw = Switch(this).apply {
            text = "Hamesha suno (wake word bolo)"
            setTextColor(Color.WHITE)
            textSize = 14f
            isChecked = false
            setOnCheckedChangeListener { _, on -> setAmb(on) }
        }
        val stop = Button(this).apply {
            text = "Assistant band karo"
            Neon.styleButton(this)
            setTextColor(Neon.RED)
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setOnClickListener { quitAll() }
        }
        keyPanel.addView(keyBox, lp(MATCH, WRAP))
        keyPanel.addView(save, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        keyPanel.addView(stop, lp(MATCH, WRAP).apply { topMargin = dp(10) })
        val settingsScroll = ScrollView(this)
        settingsScroll.addView(keyPanel)
        settingsBox = settingsScroll
        pages.addView(settingsScroll, FrameLayout.LayoutParams(MATCH, MATCH))

        mid.addView(pages, lp(0, MATCH, 7.9f).apply { leftMargin = dp(8) })
        root.addView(mid, lp(MATCH, 0, 1f).apply {
            topMargin = dp(6)
            bottomMargin = dp(6)
        })

        // ---------- नीचे की पट्टी ----------
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Neon.panel(40f, 3)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val m = dp(4)
        bar.addView(iconBtn("⌨", 44, 18f) { typeDialog() },
            lp(dp(44), dp(44)).apply { leftMargin = m; rightMargin = m })
        bar.addView(iconBtn("📷", 44, 16f) { soon() },
            lp(dp(44), dp(44)).apply { leftMargin = m; rightMargin = m })
        bar.addView(View(this), lp(0, dp(1), 1f))
        bar.addView(iconBtn("🎤", 56, 26f) { talk() }, lp(dp(56), dp(56)))
        bar.addView(View(this), lp(0, dp(1), 1f))
        bar.addView(iconBtn("🌐", 44, 16f) { soon() },
            lp(dp(44), dp(44)).apply { leftMargin = m; rightMargin = m })
        bar.addView(iconBtn("🔗", 44, 16f) { soon() },
            lp(dp(44), dp(44)).apply { leftMargin = m; rightMargin = m })
        bar.addView(iconBtn("✖", 44, 16f) { quitAll() },
            lp(dp(44), dp(44)).apply { leftMargin = m; rightMargin = m })
        root.addView(bar, lp(MATCH, WRAP))

        setContentView(root)
        netTv.text = ambientText(ambientSw.isChecked)
        showPage(if (Keys.get(prefs).isEmpty()) 2 else 0)

        // ज़रूरी permissions, फिर सर्विस शुरू
        val need = mutableListOf(
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

    // ---------- मेन्यू और पेज ----------
    private fun menuClick(i: Int) {
        when (i) {
            0 -> showPage(0)
            1, 4 -> showPage(1)
            3 -> showPage(2)
            6 -> AlertDialog.Builder(this)
                .setTitle("AI Assistant")
                .setMessage("Gemini se chalne wala tumhara apna voice assistant. Hindi mein baat karta hai aur phone ke kaam karta hai.")
                .setPositiveButton("Theek hai", null)
                .show()
            else -> soon()
        }
    }

    private fun showPage(p: Int) {
        homeBox.visibility = if (p == 0) View.VISIBLE else View.GONE
        chatBox.visibility = if (p == 1) View.VISIBLE else View.GONE
        settingsBox.visibility = if (p == 2) View.VISIBLE else View.GONE
        val active = when (p) {
            0 -> 0
            1 -> 1
            else -> 3
        }
        for (i in rows.indices) {
            val on = (i == active)
            val bg = GradientDrawable()
            bg.cornerRadius = dp(10).toFloat()
            bg.setColor(if (on) Neon.CYAN else Color.TRANSPARENT)
            rows[i].background = bg
            labels[i].setTextColor(if (on) Neon.BG else Color.parseColor(TXT))
            labels[i].typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        if (p == 1) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onBackPressed() {
        if (homeBox.visibility != View.VISIBLE) showPage(0) else super.onBackPressed()
    }

    // ---------- बटन ----------
    private fun ambientText(on: Boolean): String =
        "Mic dabao, tabhi sunta hoon"

    private fun setAmb(on: Boolean) {
        val e = AssistantService.engine
        if (e == null) {
            prefs.edit().putBoolean("ambient", on).apply()
        } else {
            e.setAmbient(on)
        }
        if (ambientSw.isChecked != on) ambientSw.isChecked = on
        netTv.text = ambientText(on)
    }

    private fun typeDialog() {
        val et = EditText(this)
        et.hint = "Yahan likho"
        AlertDialog.Builder(this)
            .setTitle("Likhkar poocho")
            .setView(et)
            .setPositiveButton("Bhejo") { _, _ ->
                val e = AssistantService.engine
                if (e == null) status.text = "Thoda ruko, assistant shuru ho raha hai..."
                else e.sendText(et.text.toString())
            }
            .setNegativeButton("Raddh", null)
            .show()
    }

    private fun quitAll() {
        AssistantService.stop()
        finish()
    }

    private fun startSvc() {
        AssistantService.ensure(this)
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startSvc()
            status.text = "Taiyaar"
            attach(15)
        } else {
            status.text = "Mic ki permission ke bina assistant nahi chalega"
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

    // सर्विस थोड़ी देर में तैयार होती है, इसलिए कुछ बार दोबारा कोशिश
    private fun attach(n: Int) {
        val e = AssistantService.engine
        if (e != null) {
            if (!e.listeners.contains(cb)) e.listeners.add(cb)
            val lg = e.log.toString()
            output.text = lg
            restoreReply(lg)
            netTv.text = ambientText(e.ambient)
            if (ambientSw.isChecked != e.ambient) ambientSw.isChecked = e.ambient
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        } else if (n > 0) {
            h.postDelayed({ attach(n - 1) }, 300)
        }
    }

    private fun talk() {
        val e = AssistantService.engine
        if (e == null) {
            status.text = "Thoda ruko, assistant shuru ho raha hai..."
            startSvc()
            h.postDelayed({ attach(15) }, 300)
            return
        }
        e.talk()
    }

    // ---------- चैट और जवाब का डिब्बा ----------
    private fun handleChat(s: String) {
        appendOut(s)
        val t = s.trim()
        if (t.startsWith("Tum:")) {
            replyQ.text = t
            replyA.text = "..."
        } else if (t.startsWith("AI:")) {
            replyA.text = t.removePrefix("AI:").trim()
        }
    }

    private fun restoreReply(lg: String) {
        val a = lg.lastIndexOf("AI:")
        val q = lg.lastIndexOf("Tum:")
        if (q >= 0) replyQ.text = lg.substring(q, if (a > q) a else lg.length).trim()
        if (a >= 0) replyA.text = lg.substring(a + 3).trim()
    }

    private fun appendOut(t: String) {
        output.append(t)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setMode(m: Int) {
        orb.mode = m
        wave.active = (m == 1 || m == 3)
        if (m == 3) { orb.level = 0.55f; wave.level = 0.6f }
        if (m == 0 || m == 2) { orb.level = 0f; wave.level = 0f }
    }

    // ---------- API key ----------
    private fun cleanKey(s: String): String = s.filter { !it.isWhitespace() && it != '"' && it != '\'' }

    private fun saveKey() {
        val k = cleanKey(keyBox.text.toString())
        keyBox.setText(k)
        prefs.edit().putString("key", k).apply()
        if (k.isEmpty()) {
            status.text = "Key khaali hai, pehle paste karo"
            return
        }
        status.text = "Key jaanch raha hoon..."
        thread {
            val res = checkKey(k)
            runOnUiThread {
                status.text = res.first
                if (res.second) showPage(0)
            }
        }
    }

    private fun checkKey(k: String): Pair<String, Boolean> {
        return try {
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1")
                .openConnection() as HttpURLConnection
            c.setRequestProperty("x-goog-api-key", k)
            c.connectTimeout = 10000
            c.readTimeout = 10000
            when (val code = c.responseCode) {
                200 -> Pair("Key sahi hai, ab mic dabao", true)
                400, 401, 403 -> Pair("Ye key galat hai. AI Studio se dobara copy karke paste karo", false)
                else -> Pair("Key check nahi ho paayi (code $code). Thodi der baad try karo", false)
            }
        } catch (e: Exception) {
            Pair("Internet check karo, key check nahi ho paayi", false)
        }
    }
}
