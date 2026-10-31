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
    private val PERSONA =
        "तुम अंडमान के एक लोकल दोस्त की तरह बात करो। यूज़र जैसे बोलता है वैसे ही बोलो: आम बोलचाल की सीधी हिंदी, " +
        "अपनापन वाली, छोटे वाक्य, 'आप' की जगह 'तुम'। यूज़र को कभी-कभी 'भाई', 'दोस्त' या 'यार' कहकर बुलाओ, पर हर वाक्य में नहीं। " +
        "यूज़र की बोली समझो (जैसे 'मेरा को' का मतलब 'मुझे')। " +
        "बीच में बंगाली, तमिल, तेलुगु या कोई स्थानीय शब्द आए तो मतलब समझने की कोशिश करो और हिंदी में जवाब दो। " +
        "यूज़र जो भाषा बोले (हिंदी/हिंग्लिश/English) उसी में छोटा जवाब दो। " +
        "आवाज़ से बना टेक्स्ट कभी गलत हो सकता है, इसलिए मतलब समझकर जवाब दो। " +
        "जब यूज़र फोन का कोई काम माँगे (कॉल, अलार्म, टाइमर, ऐप खोलना, मैसेज, टॉर्च, सेटिंग) " +
        "तो सिर्फ बात मत करो, दिए गए function इस्तेमाल करो। " +
        "मौसम पूछे तो हमेशा get_weather function इस्तेमाल करो, चाहे जगह छोटी हो, गाँव हो या द्वीप (जैसे Little Andaman, Hut Bay, Diglipur)। कभी मत कहो कि मौसम की जानकारी नहीं है, function खुद ढूँढ लेगा। Google मत खोलो। शहर का नाम अंग्रेज़ी में लिखो। अगर यूज़र का शहर याद की बातों में है तो वही लो, वरना पूछो। " +
        "web_search सिर्फ तब इस्तेमाल करो जब यूज़र खुद कहे 'Google पर खोजो'। " +
        "जिन चीज़ों की लाइव जानकारी तुम्हारे पास नहीं है (खबर, स्कोर, भाव) उनके बारे में साफ कहो कि लाइव जानकारी नहीं है, और पूछो कि Google पर खोलूँ क्या। " +
        "जब यूज़र कहे 'याद रखना', या अपने बारे में कोई पक्की बात बताए (नाम, शहर, परिवार, पसंद, आदत, ज़रूरी तारीख), " +
        "तो remember function से सेव करो। जब कहे 'भूल जाओ' तो forget function इस्तेमाल करो। " +
        "पासवर्ड, OTP, कार्ड नंबर या बैंक की जानकारी कभी सेव मत करो। कोई * # या markdown मत लगाओ।"
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
            text = "तैयार"
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
            hint = "Gemini API key यहाँ paste करो"
            setHintTextColor(Color.parseColor("#4A90A4"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("key", ""))
        }
        val save = Button(this).apply {
            text = "Key सेव करो"
            Neon.styleButton(this)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener {
                prefs.edit().putString("key", keyBox.text.toString().trim()).apply()
                status.text = "Key सेव हो गई"
                keyPanel.visibility = View.GONE
            }
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
        val brain = Button(this).apply {
            text = "🧠"
            textSize = 22f
            Neon.styleButton(this)
            setOnClickListener {
                val m = loadMem()
                say("\n🧠 याद की बातें:\n" +
                    (if (m.isEmpty()) "अभी कुछ याद नहीं है\n" else m.joinToString("\n") { "- $it" } + "\n"))
            }
        }
        bar.addView(gear, lp(dp(60), dp(60)).apply { marginEnd = dp(28) })
        bar.addView(mic, lp(dp(84), dp(84)))
        bar.addView(brain, lp(dp(60), dp(60)).apply { marginStart = dp(28) })
        root.addView(bar, lp(MATCH, WRAP))

        setContentView(root)
        say("नमस्ते भाई! मैं तैयार हूँ। 🎤 दबाओ और बोलो।\n")
    }

    private fun say(t: String) {
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
            if (c == 1) listen() else status.text = "इजाज़त मिल गई, अब दोबारा बोलो"
        }
    }

    // ---------- सुनना ----------
    private fun makeRecognizer() {
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { status.text = "बोलो..."; setMode(1) }
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
                if (!t.isNullOrEmpty()) status.text = "सुन रहा हूँ: $t"
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
                    status.text = "कुछ सुनाई नहीं दिया, फिर 🎤 दबाओ"
                } else {
                    setMode(0)
                    status.text = "सुन नहीं पाया (error $e), फिर 🎤 दबाओ"
                }
            }
            override fun onResults(res: Bundle?) {
                listening = false
                val t = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t != null) ask(t) else { setMode(0); status.text = "कुछ सुनाई नहीं दिया" }
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
        status.text = "सुन रहा हूँ..."
        setMode(1)
        recognizer?.startListening(i)
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition नहीं मिला"; return
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

    private fun remember(fact: String): String {
        val f = fact.trim()
        if (f.isEmpty()) return "क्या याद रखना है, समझ नहीं आया"
        val m = loadMem()
        if (!m.contains(f)) m.add(f)
        while (m.size > 100) m.removeAt(0)
        saveMem(m)
        return "ठीक है, याद रख लिया"
    }

    private fun forget(word: String): String {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return "क्या भूलना है, समझ नहीं आया"
        val m = loadMem()
        if (w == "all") {
            saveMem(emptyList())
            return "सब कुछ भूल गया"
        }
        val keep = m.filter { !it.lowercase().contains(w) }
        saveMem(keep)
        val n = m.size - keep.size
        return if (n > 0) "$n बात भूल गया" else "ऐसी कोई बात याद नहीं थी"
    }

    private fun systemText(): String {
        val mem = loadMem()
        if (mem.isEmpty()) return PERSONA
        return PERSONA + "\n\nयूज़र के बारे में जो बातें याद हैं:\n" + mem.joinToString("\n") { "- $it" }
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
        f.put(fn("open_app", "फोन में कोई ऐप खोलो",
            JSONObject().put("app_name", str("ऐप का नाम अंग्रेज़ी में, जैसे WhatsApp, YouTube, Camera")),
            listOf("app_name")))
        f.put(fn("set_alarm", "अलार्म लगाओ (24 घंटे वाला समय)",
            JSONObject().put("hour", num("घंटा 0 से 23"))
                .put("minute", num("मिनट 0 से 59"))
                .put("label", str("अलार्म का नाम")),
            listOf("hour", "minute")))
        f.put(fn("set_timer", "टाइमर लगाओ",
            JSONObject().put("seconds", num("कुल सेकंड")), listOf("seconds")))
        f.put(fn("call_contact", "किसी को फोन कॉल करो",
            JSONObject().put("name_or_number", str("कॉन्टैक्ट का नाम जैसा यूज़र ने बोला, या फोन नंबर")),
            listOf("name_or_number")))
        f.put(fn("whatsapp_message", "किसी को WhatsApp मैसेज लिखो",
            JSONObject().put("name_or_number", str("कॉन्टैक्ट का नाम जैसा यूज़र ने बोला, या फोन नंबर"))
                .put("message", str("भेजने वाला मैसेज")),
            listOf("name_or_number", "message")))
        f.put(fn("send_sms", "किसी को SMS लिखो",
            JSONObject().put("name_or_number", str("कॉन्टैक्ट का नाम जैसा यूज़र ने बोला, या फोन नंबर"))
                .put("message", str("भेजने वाला मैसेज")),
            listOf("name_or_number", "message")))
        f.put(fn("flashlight", "टॉर्च चालू या बंद करो",
            JSONObject().put("on", flag("true चालू, false बंद")), listOf("on")))
        f.put(fn("get_weather", "किसी शहर का आज का मौसम बताओ (तापमान, उमस, बारिश)",
            JSONObject().put("city", str("शहर का नाम अंग्रेज़ी में, जैसे Port Blair। यूज़र ने न बताया हो तो याद की बातों से उसका शहर लो")),
            listOf("city")))
        f.put(fn("web_search", "Google खोलकर कुछ खोजो। सिर्फ तब जब यूज़र खुद कहे Google पर खोजो",
            JSONObject().put("query", str("खोजने वाली बात")), listOf("query")))
        f.put(fn("youtube_search", "YouTube पर कुछ खोजो या चलाओ",
            JSONObject().put("query", str("खोजने वाली बात")), listOf("query")))
        f.put(fn("open_settings", "फोन की सेटिंग खोलो",
            JSONObject().put("page", str("wifi, bluetooth, location, display, sound या general")),
            listOf("page")))
        f.put(fn("remember", "यूज़र की कोई बात हमेशा के लिए याद रखो",
            JSONObject().put("fact", str("याद रखने वाली बात, एक छोटे साफ वाक्य में")),
            listOf("fact")))
        f.put(fn("forget", "याद की हुई कोई बात भूल जाओ",
            JSONObject().put("keyword", str("भूलने वाली बात का मुख्य शब्द। सब कुछ भूलना हो तो ALL")),
            listOf("keyword")))
        return JSONArray().put(JSONObject().put("function_declarations", f))
    }

    private fun ask(q: String) {
        val key = prefs.getString("key", "") ?: ""
        if (key.isEmpty()) {
            setMode(0)
            status.text = "पहले ⚙ दबाकर API key सेव करो"
            keyPanel.visibility = View.VISIBLE
            return
        }
        setMode(2)
        status.text = "सोच रहा हूँ..."
        say("\nतुम: $q\n")
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
                    c.setRequestProperty("Content-Type", "application/json")
                    c.setRequestProperty("x-goog-api-key", key)
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                    code = c.responseCode
                    resp = (if (code == 200) c.inputStream else c.errorStream).bufferedReader().readText()
                    if (code != 503 && code != 429) break
                }
                if (code != 200) {
                    runOnUiThread {
                        say("\nError $code: ${resp.take(200)}\n")
                        status.text = "तैयार"
                        setMode(0)
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
                val pre = HashMap<Int, String>()
                calls.forEachIndexed { idx, c ->
                    if (c.getString("name") == "get_weather") {
                        pre[idx] = Weather.today(c.optJSONObject("args")?.optString("city", "") ?: "")
                    }
                }
                runOnUiThread {
                    val results = calls.mapIndexed { idx, c ->
                        pre[idx] ?: runAction(c.getString("name"), c.optJSONObject("args") ?: JSONObject())
                    }
                    val reply = (if (results.isNotEmpty()) results.joinToString(". ")
                        else texts.toString().trim()).ifEmpty { "ठीक है" }
                    say("\nAI: $reply\n")
                    status.text = "तैयार"
                    history.add(Pair(q, reply))
                    val sr = tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "r")
                    if (sr != TextToSpeech.SUCCESS) setMode(0)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Error: ${e.message}"
                    setMode(0)
                }
            }
        }
    }

    // ---------- फोन के काम ----------
    private fun runAction(name: String, a: JSONObject): String {
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
                    "Google पर खोज रहा हूँ")
                "youtube_search" -> openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(a.getString("query")),
                    "YouTube पर खोज रहा हूँ")
                "open_settings" -> openSettings(a.optString("page", "general"))
                "remember" -> remember(a.getString("fact"))
                "forget" -> forget(a.getString("keyword"))
                else -> "ये काम मुझे अभी नहीं आता"
            }
        } catch (e: Exception) {
            "नहीं हो पाया: ${e.message}"
        }
    }

    private fun openApp(name: String): String {
        val pm = packageManager
        val q = name.lowercase().trim()
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val m = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return "$name नाम का ऐप नहीं मिला"
        val li = pm.getLaunchIntentForPackage(m.activityInfo.packageName)
            ?: return "$name खुल नहीं पाया"
        startActivity(li)
        return "${m.loadLabel(pm)} खोल रहा हूँ"
    }

    private fun setAlarm(h: Int, m: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        startActivity(i)
        val mm = "%02d".format(m)
        return "अलार्म $h:$mm के लिए सेट कर दिया"
    }

    private fun setTimer(sec: Int): String {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, sec)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        startActivity(i)
        return "$sec सेकंड का टाइमर लगा दिया"
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

    private fun callContact(who: String): String {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 3)
            return "कॉल की इजाज़त दो, फिर दोबारा बोलो"
        }
        val num = findNumber(who) ?: return "$who का नंबर नहीं मिला। कॉन्टैक्ट्स की इजाज़त देखो या नंबर बोलो"
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num))))
        return "$who को कॉल लगा रहा हूँ"
    }

    private fun whatsapp(who: String, msg: String): String {
        val num = findNumber(who) ?: return "$who का नंबर नहीं मिला। कॉन्टैक्ट्स की इजाज़त देखो या नंबर बोलो"
        var d = num.filter { it.isDigit() }
        if (d.length == 10) d = "91$d"
        else if (d.length == 11 && d.startsWith("0")) d = "91" + d.drop(1)
        val url = Uri.parse("https://wa.me/$d?text=" + Uri.encode(msg))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url).setPackage("com.whatsapp"))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, url))
        }
        return "WhatsApp खोल दिया, मैसेज लिख दिया है, भेजने के लिए send दबाओ"
    }

    private fun sms(who: String, msg: String): String {
        val num = findNumber(who) ?: return "$who का नंबर नहीं मिला। कॉन्टैक्ट्स की इजाज़त देखो या नंबर बोलो"
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num)))
            .putExtra("sms_body", msg)
        startActivity(i)
        return "SMS तैयार है, भेजने के लिए send दबाओ"
    }

    private fun torch(on: Boolean): String {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "इस फोन में फ्लैश नहीं मिला"
        cm.setTorchMode(id, on)
        return if (on) "टॉर्च चालू कर दी" else "टॉर्च बंद कर दी"
    }

    private fun openUrl(url: String, msg: String): String {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return msg
    }

    private fun openSettings(page: String): String {
        val action = when (page.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        startActivity(Intent(action))
        return "सेटिंग खोल दी"
    }

    override fun onDestroy() {
        recognizer?.destroy(); tts.shutdown(); super.onDestroy()
    }
}
