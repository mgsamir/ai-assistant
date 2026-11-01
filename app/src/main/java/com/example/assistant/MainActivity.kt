package com.example.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val MODELS = listOf("gemini-flash-lite-latest", "gemini-flash-latest")

    private val PERSONA = """
You are a friendly voice assistant who talks like a local friend from the Andaman Islands.
Speak casual, everyday Hindi the way a friend talks. Use short sentences. Say "tum", never "aap". Call the user "bhai", "dost" or "yaar" once in a while, not in every sentence.
Understand the way the user speaks (for example "mera ko" means "mujhe"). If words from Bengali, Tamil, Telugu or another local language appear, work out the meaning and answer in Hindi.
Voice-to-text can be wrong, so work out the meaning instead of taking every word literally.
NEVER start a reply with filler such as "haan", "haan bilkul", "bilkul", "zaroor", "samajh gaya", "theek hai", "sure" or "of course". Start directly with the answer. Keep replies short, 1 to 3 sentences, unless the user asks for detail.
ALWAYS write every reply as exactly two lines:
S: the reply in Hinglish, meaning Hindi written in English (Roman) letters, for the screen
V: the same reply in Hindi Devanagari script, for the voice
If the user speaks English, write both lines in English.
Never use markdown, asterisks, hashes or emojis.
When the user asks for a phone action (call, alarm, timer, open app, message, torch, settings), do not just talk: use the provided functions.
For weather ALWAYS use the get_weather function, even for small places, villages and islands (like Little Andaman, Hut Bay, Diglipur). Never say weather information is unavailable. Write the place name in English. If the user's city is in the remembered facts use it, otherwise ask.
Use web_search only when the user himself asks to search on Google.
For live things you cannot know (news, scores, prices) say plainly that you have no live information and ask if you should open Google.
When the user says "yaad rakhna" or tells you a lasting fact about himself (name, city, family, likes, habits, important dates), save it with the remember function. When he says "bhool jao", use the forget function.
Never save passwords, OTPs, card numbers or bank details.
""".trimIndent()

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var keyBox: EditText
    private lateinit var keyPanel: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var orb: OrbView
    private lateinit var wave: WaveView
    private var listening = false
    private var retried = false
    private val history = mutableListOf<Pair<String, String>>()
    private val prefs by lazy { getSharedPreferences("p", Context.MODE_PRIVATE) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Neon.BG
        window.navigationBarColor = Neon.BG
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        tts = TextToSpeech(this, this)

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

        // key वाला डिब्बा
        keyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Neon.panel(24f, 3)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            visibility = if ((prefs.getString("key", "") ?: "").isEmpty()) View.VISIBLE else View.GONE
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
        keyPanel.addView(keyBox, lp(MATCH, WRAP))
        keyPanel.addView(save, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        root.addView(keyPanel, lp(MATCH, WRAP).apply { topMargin = dp(8) })

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
            setOnClickListener {
                keyPanel.visibility = if (keyPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
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
            setOnClickListener { listen() }
        }
        bar.addView(gear, lp(dp(60), dp(60)).apply { marginEnd = dp(28) })
        bar.addView(mic, lp(dp(84), dp(84)))
        bar.addView(View(this), lp(dp(60), dp(60)).apply { marginStart = dp(28) })
        root.addView(bar, lp(MATCH, WRAP))

        setContentView(root)
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

    override fun onInit(s: Int) {
        if (s == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { runOnUiThread { setMode(3) } }
                override fun onDone(id: String?) { runOnUiThread { setMode(0) } }
                override fun onError(id: String?) { runOnUiThread { setMode(0) } }
            })
        }
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r)
        if (r.isNotEmpty() && r[0] == PackageManager.PERMISSION_GRANTED) {
            if (c == 1) listen() else status.text = "Permission mil gayi, ab dobara bolo"
        }
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
                if (res.second) keyPanel.visibility = View.GONE
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

    private fun apiError(code: Int, body: String): Reply {
        return when {
            code == 400 && body.contains("API key", ignoreCase = true) ->
                Reply("API key galat hai. Neeche gear dabao aur nayi key paste karo.",
                    "एपीआई की गलत है। नीचे गियर दबाओ और नई की पेस्ट करो।")
            code == 400 ->
                Reply("Request mein kuch gadbad hai.", "रिक्वेस्ट में कुछ गड़बड़ है।")
            code == 403 ->
                Reply("Is key ko permission nahi mili. AI Studio mein nayi key banao.",
                    "इस की को परमिशन नहीं मिली। नई की बनाओ।")
            code == 429 ->
                Reply("Free limit ho gayi ya bahut tez ho gaya. Thodi der baad try karo.",
                    "फ्री लिमिट हो गई है। थोड़ी देर बाद कोशिश करो।")
            code == 503 ->
                Reply("Google ka model abhi busy hai. Thodi der baad try karo.",
                    "गूगल का मॉडल अभी व्यस्त है। थोड़ी देर बाद कोशिश करो।")
            else ->
                Reply("Error $code aaya. Thodi der baad try karo.",
                    "कुछ गड़बड़ हो गई। थोड़ी देर बाद कोशिश करो।")
        }
    }

    // ---------- सुनना ----------
    private fun makeRecognizer() {
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { status.text = "Bolo..."; setMode(1) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {
                val l = ((v + 2f) / 12f).coerceIn(0f, 1f)
                orb.level = l
                wave.level = l
            }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(p: Bundle?) {
                val t = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrEmpty()) status.text = "Sun raha hoon: $t"
            }
            override fun onError(e: Int) {
                listening = false
                if ((e == 11 || e == 5 || e == 8) && !retried) {
                    retried = true
                    Handler(Looper.getMainLooper()).postDelayed({
                        makeRecognizer()
                        startListen()
                    }, 400)
                } else if (e == 7 || e == 6) {
                    setMode(0)
                    status.text = "Kuch sunai nahi diya, phir mic dabao"
                } else {
                    setMode(0)
                    status.text = "Sun nahi paaya (error $e), phir mic dabao"
                }
            }
            override fun onResults(res: Bundle?) {
                listening = false
                val t = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t != null) {
                    ask(t)
                } else {
                    setMode(0)
                    status.text = "Kuch sunai nahi diya"
                }
            }
        })
        recognizer = r
    }

    private fun startListen() {
        tts.stop()
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        listening = true
        status.text = "Sun raha hoon..."
        setMode(1)
        recognizer?.startListening(i)
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition nahi mila"; return
        }
        if (listening) {
            recognizer?.stopListening(); return
        }
        retried = false
        if (recognizer == null) makeRecognizer()
        startListen()
    }

    // ---------- हमेशा की याद ----------
    private fun loadMem(): MutableList<String> {
        val out = mutableListOf<String>()
        try {
            val arr = JSONArray(prefs.getString("mem", "[]"))
            for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (e: Exception) {
        }
        return out
    }

    private fun saveMem(list: List<String>) {
        prefs.edit().putString("mem", JSONArray(list).toString()).apply()
    }

    private fun remember(fact: String): Reply {
        val f = fact.trim()
        if (f.isEmpty()) return Reply("Kya yaad rakhna hai, samajh nahi aaya", "क्या याद रखना है, समझ नहीं आया")
        val m = loadMem()
        if (!m.contains(f)) m.add(f)
        while (m.size > 100) m.removeAt(0)
        saveMem(m)
        return Reply("Theek hai, yaad rakh liya", "ठीक है, याद रख लिया")
    }

    private fun forget(word: String): Reply {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return Reply("Kya bhoolna hai, samajh nahi aaya", "क्या भूलना है, समझ नहीं आया")
        val m = loadMem()
        if (w == "all") {
            saveMem(emptyList())
            return Reply("Sab kuch bhool gaya", "सब कुछ भूल गया")
        }
        val keep = m.filter { !it.lowercase().contains(w) }
        saveMem(keep)
        val n = m.size - keep.size
        return if (n > 0) Reply("$n baat bhool gaya", "$n बात भूल गया")
        else Reply("Aisi koi baat yaad nahi thi", "ऐसी कोई बात याद नहीं थी")
    }

    private fun systemText(): String {
        val mem = loadMem()
        if (mem.isEmpty()) return PERSONA
        return PERSONA + "\n\nFacts remembered about the user:\n" + mem.joinToString("\n") { "- $it" }
    }

    // ---------- AI से बात ----------
    private fun str(d: String) = JSONObject().put("type", "STRING").put("description", d)
    private fun num(d: String) = JSONObject().put("type", "INTEGER").put("description", d)
    private fun flag(d: String) = JSONObject().put("type", "BOOLEAN").put("description", d)
    private fun fn(name: String, desc: String, props: JSONObject, req: List<String>) = JSONObject()
        .put("name", name).put("description", desc)
        .put("parameters", JSONObject().put("type", "OBJECT").put("properties", props)
            .put("required", JSONArray(req)))

    private fun tools(): JSONArray {
        val f = JSONArray()
        f.put(fn("open_app", "Open an app on the phone",
            JSONObject().put("app_name", str("App name in English, for example WhatsApp, YouTube, Camera")),
            listOf("app_name")))
        f.put(fn("set_alarm", "Set an alarm (24 hour time)",
            JSONObject().put("hour", num("Hour 0 to 23"))
                .put("minute", num("Minute 0 to 59"))
                .put("label", str("Alarm label")),
            listOf("hour", "minute")))
        f.put(fn("set_timer", "Start a timer",
            JSONObject().put("seconds", num("Total seconds")), listOf("seconds")))
        f.put(fn("call_contact", "Make a phone call",
            JSONObject().put("name_or_number", str("Contact name as the user said it, or a phone number")),
            listOf("name_or_number")))
        f.put(fn("whatsapp_message", "Write a WhatsApp message to someone",
            JSONObject().put("name_or_number", str("Contact name as the user said it, or a phone number"))
                .put("message", str("Message text")),
            listOf("name_or_number", "message")))
        f.put(fn("send_sms", "Write an SMS to someone",
            JSONObject().put("name_or_number", str("Contact name as the user said it, or a phone number"))
                .put("message", str("Message text")),
            listOf("name_or_number", "message")))
        f.put(fn("flashlight", "Turn the torch on or off",
            JSONObject().put("on", flag("true to turn on, false to turn off")), listOf("on")))
        f.put(fn("get_weather", "Get today's weather for any place, even a small village or island",
            JSONObject().put("city", str("Place name in English, for example Little Andaman or Port Blair. If the user did not say, use the city from the remembered facts")),
            listOf("city")))
        f.put(fn("web_search", "Open Google and search. Only when the user himself asks to search on Google",
            JSONObject().put("query", str("What to search")), listOf("query")))
        f.put(fn("youtube_search", "Search or play something on YouTube",
            JSONObject().put("query", str("What to search")), listOf("query")))
        f.put(fn("open_settings", "Open phone settings",
            JSONObject().put("page", str("wifi, bluetooth, location, display, sound or general")),
            listOf("page")))
        f.put(fn("remember", "Remember a lasting fact about the user forever",
            JSONObject().put("fact", str("The fact, as one short clear sentence")),
            listOf("fact")))
        f.put(fn("forget", "Forget a remembered fact",
            JSONObject().put("keyword", str("Main word of the fact to forget. Use ALL to forget everything")),
            listOf("keyword")))
        return JSONArray().put(JSONObject().put("function_declarations", f))
    }

    // मॉडल का जवाब "S: ..." (स्क्रीन) और "V: ..." (आवाज़) में बाँटता है
    private fun parseReply(raw: String): Reply {
        val t = raw.replace("*", "").trim()
        if (t.isEmpty()) return Reply("Ho gaya.", "हो गया।")
        var s = ""
        var v = ""
        var cur = 0
        val tag = Regex("^([SV])\\s*:\\s*(.*)$")
        for (line in t.lines()) {
            val l = line.trim()
            if (l.isEmpty()) continue
            val m = tag.find(l)
            if (m != null) {
                if (m.groupValues[1] == "S") {
                    cur = 1
                    s = m.groupValues[2].trim()
                } else {
                    cur = 2
                    v = m.groupValues[2].trim()
                }
            } else if (cur == 2) {
                v = (v + " " + l).trim()
            } else {
                s = (s + " " + l).trim()
            }
        }
        if (s.isEmpty() && v.isEmpty()) return Reply(t, t)
        if (s.isEmpty()) s = v
        if (v.isEmpty()) v = s
        return Reply(s, v)
    }

    private fun deliver(r: Reply, q: String, save: Boolean) {
        appendOut("\nAI: ${r.text}\n")
        status.text = "Taiyaar"
        if (save) {
            history.add(Pair(q, "S: ${r.text}\nV: ${r.voice}"))
            while (history.size > 12) history.removeAt(0)
        }
        val sr = tts.speak(r.voice, TextToSpeech.QUEUE_FLUSH, null, "r")
        if (sr != TextToSpeech.SUCCESS) setMode(0)
    }

    private fun ask(q: String) {
        val key = prefs.getString("key", "") ?: ""
        if (key.isEmpty()) {
            setMode(0)
            status.text = "Pehle gear dabakar API key save karo"
            keyPanel.visibility = View.VISIBLE
            return
        }
        setMode(2)
        status.text = "Soch raha hoon..."
        appendOut("\nTum: $q\n")
        thread {
            try {
                val contents = JSONArray()
                for ((u, a) in history.takeLast(6)) {
                    contents.put(JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", u))))
                    contents.put(JSONObject().put("role", "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", a))))
                }
                contents.put(JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", q))))
                val body = JSONObject()
                    .put("system_instruction", JSONObject().put("parts",
                        JSONArray().put(JSONObject().put("text", systemText()))))
                    .put("tools", tools())
                    .put("contents", contents)
                var code = 0
                var resp = ""
                for (m in MODELS) {
                    val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent")
                        .openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.connectTimeout = 15000
                    c.readTimeout = 40000
                    c.setRequestProperty("Content-Type", "application/json")
                    c.setRequestProperty("x-goog-api-key", key)
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                    code = c.responseCode
                    resp = (if (code == 200) c.inputStream else c.errorStream).bufferedReader().readText()
                    if (code != 503 && code != 429) break
                }
                if (code != 200) {
                    val er = apiError(code, resp)
                    runOnUiThread {
                        setMode(0)
                        deliver(er, q, false)
                    }
                    return@thread
                }
                val parts = JSONObject(resp).optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")
                val texts = StringBuilder()
                val calls = mutableListOf<JSONObject>()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        if (p.has("text")) texts.append(p.getString("text"))
                        if (p.has("functionCall")) calls.add(p.getJSONObject("functionCall"))
                    }
                }
                // मौसम जैसे इंटरनेट वाले काम यहीं पीछे (background) में होते हैं
                val pre = HashMap<Int, Reply>()
                calls.forEachIndexed { idx, c ->
                    if (c.getString("name") == "get_weather") {
                        pre[idx] = Weather.today(c.optJSONObject("args")?.optString("city", "") ?: "")
                    }
                }
                runOnUiThread {
                    val results = calls.mapIndexed { idx, c ->
                        pre[idx] ?: runAction(c.getString("name"), c.optJSONObject("args") ?: JSONObject())
                    }
                    val r = if (results.isNotEmpty()) {
                        Reply(results.joinToString(". ") { it.text }, results.joinToString(". ") { it.voice })
                    } else {
                        parseReply(texts.toString())
                    }
                    deliver(r, q, true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setMode(0)
                    status.text = "Error: ${e.message}"
                }
            }
        }
    }

    // ---------- फोन के काम ----------
    private fun runAction(name: String, a: JSONObject): Reply {
        return try {
            when (name) {
                "open_app" -> openApp(a.getString("app_name"))
                "set_alarm" -> setAlarm(a.getInt("hour"), a.optInt("minute", 0), a.optString("label", "Alarm"))
                "set_timer" -> setTimer(a.getInt("seconds"))
                "call_contact" -> callContact(a.getString("name_or_number"))
                "whatsapp_message" -> whatsapp(a.getString("name_or_number"), a.optString("message", ""))
                "send_sms" -> sms(a.getString("name_or_number"), a.optString("message", ""))
                "flashlight" -> torch(a.optBoolean("on", true))
                "web_search" -> openUrl("https://www.google.com/search?q=" + Uri.encode(a.getString("query")),
                    Reply("Google par khoj raha hoon", "गूगल पर खोज रहा हूँ"))
                "youtube_search" -> openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(a.getString("query")),
                    Reply("YouTube par khoj raha hoon", "यूट्यूब पर खोज रहा हूँ"))
                "open_settings" -> openSettings(a.optString("page", "general"))
                "remember" -> remember(a.getString("fact"))
                "forget" -> forget(a.getString("keyword"))
                else -> Reply("Ye kaam mujhe abhi nahi aata", "ये काम मुझे अभी नहीं आता")
            }
        } catch (e: Exception) {
            Reply("Nahi ho paaya: ${e.message}", "नहीं हो पाया")
        }
    }

    private fun openApp(name: String): Reply {
        val pm = packageManager
        val q = name.lowercase().trim()
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val m = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return Reply("$name naam ka app nahi mila", "$name नाम का ऐप नहीं मिला")
        val li = pm.getLaunchIntentForPackage(m.activityInfo.packageName)
            ?: return Reply("$name khul nahi paaya", "$name खुल नहीं पाया")
        startActivity(li)
        val label = m.loadLabel(pm).toString()
        return Reply("$label khol raha hoon", "$label खोल रहा हूँ")
    }

    private fun setAlarm(h: Int, m: Int, label: String): Reply {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        startActivity(i)
        val mm = "%02d".format(m)
        return Reply("Alarm $h:$mm ke liye set kar diya", "अलार्म $h:$mm के लिए सेट कर दिया")
    }

    private fun setTimer(sec: Int): Reply {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, sec)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        startActivity(i)
        return Reply("$sec second ka timer laga diya", "$sec सेकंड का टाइमर लगा दिया")
    }

    private fun findNumber(q: String): String? {
        val onlyNumber = q.none { it.isLetter() } && q.count { it.isDigit() } >= 7
        if (onlyNumber) return q.filter { it.isDigit() || it == '+' }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 2)
            return null
        }
        val c = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$q%"), null)
        c?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun noNumber(who: String) = Reply(
        "$who ka number nahi mila. Contacts ki permission dekho ya number bolo",
        "$who का नंबर नहीं मिला। कॉन्टैक्ट्स की परमिशन देखो या नंबर बोलो")

    private fun callContact(who: String): Reply {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 3)
            return Reply("Call ki permission do, phir dobara bolo", "कॉल की परमिशन दो, फिर दोबारा बोलो")
        }
        val num = findNumber(who) ?: return noNumber(who)
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num))))
        return Reply("$who ko call laga raha hoon", "$who को कॉल लगा रहा हूँ")
    }

    private fun whatsapp(who: String, msg: String): Reply {
        val num = findNumber(who) ?: return noNumber(who)
        var d = num.filter { it.isDigit() }
        if (d.length == 10) d = "91$d"
        else if (d.length == 11 && d.startsWith("0")) d = "91" + d.drop(1)
        val url = Uri.parse("https://wa.me/$d?text=" + Uri.encode(msg))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url).setPackage("com.whatsapp"))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, url))
        }
        return Reply("WhatsApp khol diya, message likh diya hai, bhejne ke liye send dabao",
            "व्हाट्सऐप खोल दिया, मैसेज लिख दिया है, भेजने के लिए सेंड दबाओ")
    }

    private fun sms(who: String, msg: String): Reply {
        val num = findNumber(who) ?: return noNumber(who)
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num)))
            .putExtra("sms_body", msg)
        startActivity(i)
        return Reply("SMS taiyaar hai, bhejne ke liye send dabao", "एसएमएस तैयार है, भेजने के लिए सेंड दबाओ")
    }

    private fun torch(on: Boolean): Reply {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return Reply("Is phone mein flash nahi mila", "इस फोन में फ्लैश नहीं मिला")
        cm.setTorchMode(id, on)
        return if (on) Reply("Torch chalu kar di", "टॉर्च चालू कर दी")
        else Reply("Torch band kar di", "टॉर्च बंद कर दी")
    }

    private fun openUrl(url: String, msg: Reply): Reply {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return msg
    }

    private fun openSettings(page: String): Reply {
        val action = when (page.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        startActivity(Intent(action))
        return Reply("Settings khol di", "सेटिंग खोल दी")
    }

    override fun onDestroy() {
        recognizer?.destroy(); tts.shutdown(); super.onDestroy()
    }
}
