package com.example.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
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
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var keyBox: EditText
    private lateinit var settings: ScrollView
    private lateinit var scroll: ScrollView
    private lateinit var orb: OrbView
    private lateinit var wave: WaveView
    private val h = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("p", Context.MODE_PRIVATE) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    // सर्विस से आने वाली खबरें स्क्रीन पर दिखाता है
    private val cb = object : UiCb {
        override fun onStatus(s: String) { status.text = s }
        override fun onChat(s: String) { appendOut(s) }
        override fun onMode(m: Int) { setMode(m) }
        override fun onLevel(l: Float) { orb.level = l; wave.level = l }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Neon.BG
        window.navigationBarColor = Neon.BG
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Neon.BG)
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }

        // ऊपर की पट्टी
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Neon.panel(24f, 3)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        val title = TextView(this).apply {
            text = "◉  AI ASSISTANT"
            setTextColor(Neon.CYAN)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        }
        val clock = TextClock(this).apply {
            format12Hour = "hh:mm a"
            format24Hour = "hh:mm a"
            setTextColor(Neon.CYAN)
            textSize = 16f
        }
        topBar.addView(title, lp(0, WRAP, 1f))
        topBar.addView(clock)
        root.addView(topBar, lp(MATCH, WRAP))

        // रोबोट वाला गोला
        orb = OrbView(this)
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        val orbSize = minOf(sw * 0.68f, sh * 0.30f).toInt()
        root.addView(orb, lp(orbSize, orbSize).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(10)
        })

        val pill = TextView(this).apply {
            text = "AI ASSISTANT"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            background = Neon.panel(40f, 3)
            setPadding(dp(28), dp(8), dp(28), dp(8))
        }
        root.addView(pill, lp(WRAP, WRAP).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(4)
        })

        status = TextView(this).apply {
            text = "Taiyaar"
            setTextColor(Color.parseColor("#7FE9FF"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(2))
        }
        root.addView(status, lp(MATCH, WRAP))

        wave = WaveView(this)
        root.addView(wave, lp(MATCH, dp(44)))

        // सेटिंग वाला डिब्बा (key, ambient, stop)
        val keyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Neon.panel(24f, 3)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        keyBox = EditText(this).apply {
            hint = "Gemini API key yahan paste karo"
            setHintTextColor(Color.parseColor("#4A90A4"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("key", ""))
        }
        val save = Button(this).apply {
            text = "Key save karo"
            Neon.styleButton(this)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { saveKey() }
        }
        val ambientSw = Switch(this).apply {
            text = "Hamesha suno (bolo: assistant)"
            setTextColor(Color.WHITE)
            textSize = 15f
            isChecked = prefs.getBoolean("ambient", false)
            setOnCheckedChangeListener { _, on ->
                val e = AssistantService.engine
                if (e == null) {
                    prefs.edit().putBoolean("ambient", on).apply()
                } else {
                    e.setAmbient(on)
                }
            }
        }
        val stop = Button(this).apply {
            text = "Assistant band karo"
            Neon.styleButton(this)
            setTextColor(Neon.RED)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener {
                startService(Intent(this@MainActivity, AssistantService::class.java).setAction("STOP"))
                finish()
            }
        }
        keyPanel.addView(keyBox, lp(MATCH, WRAP))
        keyPanel.addView(save, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        keyPanel.addView(ambientSw, lp(MATCH, WRAP).apply { topMargin = dp(16) })
        keyPanel.addView(stop, lp(MATCH, WRAP).apply { topMargin = dp(16) })
        settings = ScrollView(this).apply { addView(keyPanel) }
        root.addView(settings, lp(MATCH, 0, 1f).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        })

        // चैट का डिब्बा
        output = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#CFF6FF"))
            setLineSpacing(0f, 1.15f)
        }
        scroll = ScrollView(this).apply {
            background = Neon.panel(24f, 3)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(output)
        }
        root.addView(scroll, lp(MATCH, 0, 1f).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        })

        // नीचे की पट्टी
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = Neon.panel(36f, 3)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val gear = Button(this).apply {
            text = "⚙"
            textSize = 22f
            Neon.styleButton(this)
            setOnClickListener { showSettings(settings.visibility != View.VISIBLE) }
        }
        val mic = Button(this).apply {
            text = "🎤"
            textSize = 32f
            background = Neon.circle()
            setTextColor(Neon.CYAN)
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
            setOnClickListener { talk() }
        }
        bar.addView(gear, lp(dp(60), dp(60)).apply { marginEnd = dp(28) })
        bar.addView(mic, lp(dp(84), dp(84)))
        bar.addView(View(this), lp(dp(60), dp(60)).apply { marginStart = dp(28) })
        root.addView(bar, lp(MATCH, WRAP))

        setContentView(root)
        showSettings((prefs.getString("key", "") ?: "").isEmpty())

        // ज़रूरी permissions, फिर सर्विस शुरू
        val need = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) need.add("android.permission.POST_NOTIFICATIONS")
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 1)
        } else {
            startSvc()
        }
    }

    private fun showSettings(on: Boolean) {
        settings.visibility = if (on) View.VISIBLE else View.GONE
        scroll.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun startSvc() {
        val i = Intent(this, AssistantService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
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

    // सर्विस थोड़ी देर में तैयार होती है, इसलिए कुछ बार दोबारा कोशिश
    private fun attach(n: Int) {
        val e = AssistantService.engine
        if (e != null) {
            if (!e.listeners.contains(cb)) e.listeners.add(cb)
            output.text = e.log.toString()
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
                if (res.second) showSettings(false)
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
