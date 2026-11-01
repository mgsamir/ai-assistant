package com.example.assistant

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

interface UiCb {
    fun onStatus(s: String)
    fun onChat(s: String)
    fun onMode(m: Int)
    fun onLevel(l: Float)
}

    private val WAKE = listOf("jarvis", "hey jarvis", "javis", "jarvish", "जार्विस", "जारविस", "जर्विस", "जार्वीस", "जार्विश", "हे जार्विस")
class Assistant(private val app: Context) : TextToSpeech.OnInitListener {
    private val MODELS = listOf("gemini-flash-lite-latest", "gemini-flash-latest")

    // "assistant" जैसा शब्द बोलने पर ही ambient मोड में जवाब देता है
    private val WAKE = listOf("assistant", "asistant", "jarvis", "असिस्टेंट", "असिस्टैंट", "एसिस्टेंट", "जार्विस", "जारविस", "सुनो", "ओए")

    private val PERSONA = """
You are a friendly voice assistant who talks like a local friend from the Andaman Islands.
Speak casual, everyday Hindi the way a friend talks. Use short sentences. Say "tum", never "aap". Call the user "bhai", "dost" or "yaar" once in a while, not in every sentence.
Understand the way the user speaks (for example "mera ko" means "mujhe"). If words from Bengali, Tamil, Telugu or another local language appear, work out the meaning and answer in Hindi.
Voice-to-text can be wrong, so work out the meaning instead of taking every word literally.
This is a running voice conversation. The user may reply to your last answer without repeating the topic, so use the earlier messages as context.
NEVER start a reply with filler such as "haan", "haan bilkul", "bilkul", "zaroor", "samajh gaya", "theek hai", "sure" or "of course". Start directly with the answer. Keep replies short, 1 to 3 sentences, unless the user asks for detail.
ALWAYS write every reply as exactly two lines:
S: the reply in Hinglish, meaning Hindi written in English (Roman) letters, for the screen
V: the same reply in Hindi Devanagari script, for the voice
If the user speaks English, write both lines in English.
Never use markdown, asterisks, hashes or emojis.
When the user asks for a phone action (call, alarm, timer, open app, close app, message, torch, settings), do not just talk: use the provided functions.
When the user says to stop or quit the assistant (for example "band ho ja", "stop assistant", "bas ab chup"), call stop_assistant.
For weather ALWAYS use the get_weather function, even for small places, villages and islands (like Little Andaman, Hut Bay, Diglipur). Never say weather information is unavailable. Write the place name in English. If the user's city is in the remembered facts use it, otherwise ask.
Use web_search only when the user himself asks to search on Google.
For live things you cannot know (news, scores, prices) say plainly that you have no live information and ask if you should open Google.
When the user says "yaad rakhna" or tells you a lasting fact about himself (name, city, family, likes, habits, important dates), save it with the remember function. When he says "bhool jao", use the forget function.
Never save passwords, OTPs, card numbers or bank details.
""".trimIndent()

    val listeners = CopyOnWriteArrayList<UiCb>()
    val log = StringBuilder()
    var onQuit: (() -> Unit)? = null
    var ambient = false

    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("p", Context.MODE_PRIVATE)
    private val tts: TextToSpeech = TextToSpeech(app, this)
    private var recognizer: SpeechRecognizer? = null
    private var tone: ToneGenerator? = null
    private var running = true
    private var listening = false
    private var listenStart = 0L
    private var busy = false
    private var retried = false
    private var errs = 0
    private var awaiting = 0L
    private val history = mutableListOf<Pair<String, String>>()

    init {
        ambient = prefs.getBoolean("ambient", false)
    }

    // ---------- UI को खबर ----------
    private fun status(s: String) { for (l in listeners) l.onStatus(s) }
    private fun mode(m: Int) { for (l in listeners) l.onMode(m) }
    private fun level(v: Float) { for (l in listeners) l.onLevel(v) }
    private fun chat(s: String) {
        log.append(s)
        if (log.length > 8000) log.delete(0, log.length - 6000)
        for (l in listeners) l.onChat(s)
    }

    private fun beep(kind: Int) {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            val t = when (kind) {
                0 -> ToneGenerator.TONE_PROP_BEEP
                1 -> ToneGenerator.TONE_PROP_ACK
                else -> ToneGenerator.TONE_PROP_NACK
            }
            tone?.startTone(t, 120)
        } catch (e: Exception) {
        }
    }

    // ---------- शुरू / बंद ----------
    fun start() {
        if (ambient) scheduleListen(500)
    }

    fun setAmbient(on: Boolean) {
        ambient = on
        prefs.edit().putBoolean("ambient", on).apply()
        if (on) {
            status("Hamesha sun raha hoon. Bolo: assistant")
            if (!listening && !busy) scheduleListen(200)
        } else {
            try { recognizer?.cancel() } catch (e: Exception) { }
            listening = false
            mode(0)
            status("Taiyaar")
        }
    }

    fun shutdown() {
        running = false
        main.removeCallbacksAndMessages(null)
        try { recognizer?.destroy() } catch (e: Exception) { }
        recognizer = null
        try { tts.stop(); tts.shutdown() } catch (e: Exception) { }
        try { tone?.release() } catch (e: Exception) { }
    }

    // ---------- आवाज़ बोलना ----------
    override fun onInit(s: Int) {
        if (s == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { main.post { mode(3) } }
                override fun onDone(id: String?) { main.post { speakDone() } }
                override fun onError(id: String?) { main.post { speakDone() } }
            })
        }
    }

    private fun speakDone() {
        busy = false
        mode(0)
        if (ambient) {
            // जवाब के बाद 7 सेकंड तक wake word बोले बिना बात आगे बढ़ा सकते हो
            awaiting = SystemClock.uptimeMillis() + 7000
            status("Bolo, main sun raha hoon...")
            scheduleListen(350)
        }
    }

    // ---------- सुनना ----------
    private fun scheduleListen(delay: Long) {
        main.postDelayed({
            if (running && ambient && !listening && !busy) beginListen()
        }, delay)
    }

    // मिक/ऑर्ब बटन दबाने पर
    fun talk() {
        awaiting = SystemClock.uptimeMillis() + 8000
        if (busy) {
            try { tts.stop() } catch (e: Exception) { }
            busy = false
        }
        if (listening) {
            if (SystemClock.uptimeMillis() - listenStart > 20000) {
                // अटका हुआ recognizer साफ करो
                try { recognizer?.destroy() } catch (e: Exception) { }
                recognizer = null
                listening = false
            } else {
                if (!ambient) {
                    try { recognizer?.stopListening() } catch (e: Exception) { }
                }
                status("Bolo...")
                return
            }
        }
        beep(0)
        retried = false
        beginListen()
    }

    private fun makeRecognizer() {
        try { recognizer?.destroy() } catch (e: Exception) { }
        val r = SpeechRecognizer.createSpeechRecognizer(app)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { mode(1) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) { level(((v + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(p: Bundle?) {
                val t = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrEmpty()) status("Sun raha hoon: $t")
            }
            override fun onError(e: Int) { handleError(e) }
            override fun onResults(res: Bundle?) {
                listening = false
                val t = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) {
                    mode(0)
                    if (ambient) scheduleListen(200) else status("Kuch sunai nahi diya")
                } else {
                    handle(t)
                }
            }
        })
        recognizer = r
    }

    private fun beginListen() {
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status("Mic ki permission do (app kholkar)")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            status("Speech recognition nahi mila")
            return
        }
        if (listening) return
        if (recognizer == null) makeRecognizer()
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        listening = true
        listenStart = SystemClock.uptimeMillis()
        mode(1)
        if (!ambient || SystemClock.uptimeMillis() < awaiting) status("Sun raha hoon...")
        else status("Wake word ka intezaar: assistant")
        try {
            recognizer?.startListening(i)
        } catch (e: Exception) {
            listening = false
            status("Mic shuru nahi hua")
            if (ambient) scheduleListen(2000)
        }
    }

    private fun handleError(e: Int) {
        listening = false
        if (e == 9) {
            // मिक की permission नहीं
            setAmbient(false)
            status("Mic ki permission nahi hai")
            return
        }
        if (ambient) {
            // चुप्पी (6) और "समझ नहीं आया" (7) ambient में आम बात है, चुपचाप दोबारा सुनो
            if (e == 6 || e == 7) {
                errs = 0
                scheduleListen(200)
            } else {
                errs++
                if (e == 8 || e == 11 || e == 5) {
                    try { recognizer?.destroy() } catch (x: Exception) { }
                    recognizer = null
                }
                scheduleListen(if (errs > 5) 4000L else 700L)
            }
            return
        }
        if ((e == 11 || e == 5 || e == 8) && !retried) {
            retried = true
            main.postDelayed({
                try { recognizer?.destroy() } catch (x: Exception) { }
                recognizer = null
                beginListen()
            }, 400)
        } else if (e == 7 || e == 6) {
            mode(0)
            status("Kuch sunai nahi diya, phir mic dabao")
        } else {
            mode(0)
            status("Sun nahi paaya (error $e), phir mic dabao")
        }
    }

    // सुना हुआ वाक्य: wake word जाँचो, फिर AI को भेजो
    private fun handle(t: String) {
        errs = 0
        val now = SystemClock.uptimeMillis()
        var q = t.trim()
        var accept = true
        if (ambient && now >= awaiting) {
            accept = false
            val low = q.lowercase()
            for (w in WAKE) {
                val idx = low.indexOf(w)
                if (idx >= 0) {
                    q = (q.substring(0, idx) + " " + q.substring(idx + w.length)).trim()
                    accept = true
                    break
                }
            }
        }
        if (!accept) {
            mode(0)
            scheduleListen(200)
            return
        }
        if (q.isEmpty()) {
            // सिर्फ wake word बोला: अब अगली बात का इंतज़ार
            awaiting = now + 8000
            beep(0)
            status("Bolo...")
            scheduleListen(100)
            return
        }
        awaiting = 0L
        beep(0)
        ask(q)
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
        f.put(fn("close_app", "Close an app that runs in the background",
            JSONObject().put("app_name", str("App name in English")),
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
                f.put(fn("set_ambient", "Turn always-listening mode on or off. Use when the user says hamesha suno, always listen, sunte raho (on) or sunna band karo (off)",
            JSONObject().put("on", flag("true to listen always, false to stop")), listOf("on")))
        f.put(fn("stop_assistant", "Completely stop and close this assistant when the user asks it to quit",
            JSONObject().put("reason", str("Why, can be empty")), listOf()))
        return JSONArray().put(JSONObject().put("function_declarations", f))
    }
    // "haan bilkul" जैसे फालतू शुरुआती शब्द हटाता है
    private val FILLERS = listOf("haan bilkul", "haan ji", "ji haan", "haan", "han", "bilkul", "zaroor", "jaroor",
        "theek hai", "thik hai", "samajh gaya", "sure", "of course",
        "हाँ बिल्कुल", "हां बिल्कुल", "हाँ जी", "हां जी", "हाँ", "हां", "बिल्कुल", "ज़रूर", "जरूर", "ठीक है", "समझ गया")

    private fun stripFiller(x: String): String {
        var s = x.trim()
        var again = true
        while (again) {
            again = false
            val low = s.lowercase()
            for (f in FILLERS) {
                if (low.startsWith(f) && (low.length == f.length || !low[f.length].isLetter())) {
                    s = s.substring(f.length).trimStart(',', '.', '!', ' ', '-', '।')
                    again = true
                    break
                }
            }
        }
        if (s.isEmpty()) return x.trim()
        return s.substring(0, 1).uppercase() + s.substring(1)
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
                return Reply(stripFiller(s), stripFiller(v))
    }

    private fun apiError(code: Int, body: String): Reply {
        return when {
            code == 400 && body.contains("API key", ignoreCase = true) ->
                Reply("API key galat hai. App kholkar gear dabao aur nayi key paste karo.",
                    "एपीआई की गलत है। ऐप खोलकर गियर दबाओ और नई की पेस्ट करो।")
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

    private fun deliver(r: Reply, q: String, save: Boolean) {
        chat("\nAI: ${r.text}\n")
        status("Taiyaar")
        if (save) {
            history.add(Pair(q, "S: ${r.text}\nV: ${r.voice}"))
            while (history.size > 12) history.removeAt(0)
        }
        busy = true
        val sr = tts.speak(r.voice, TextToSpeech.QUEUE_FLUSH, null, "r")
        if (sr != TextToSpeech.SUCCESS) {
            busy = false
            mode(0)
            if (ambient) scheduleListen(500)
        }
    }

    private fun ask(q: String) {
        val key = prefs.getString("key", "") ?: ""
        if (key.isEmpty()) {
            mode(0)
            status("Pehle app kholkar gear dabao aur API key save karo")
            if (ambient) scheduleListen(3000)
            return
        }
        busy = true
        mode(2)
        status("Soch raha hoon...")
        chat("\nTum: $q\n")
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
                    main.post {
                        mode(0)
                        beep(2)
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
                main.post {
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
                main.post {
                    busy = false
                    mode(0)
                    beep(2)
                    status("Error: ${e.message}")
                    if (ambient) scheduleListen(2000)
                }
            }
        }
    }

    // ---------- फोन के काम ----------
    private fun go(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(i)
    }

    private fun runAction(name: String, a: JSONObject): Reply {
        return try {
            when (name) {
                "open_app" -> openApp(a.getString("app_name"))
                "close_app" -> closeApp(a.getString("app_name"))
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
                                "set_ambient" -> {
                    val on = a.optBoolean("on", true)
                    setAmbient(on)
                    if (on) Reply("Ab main hamesha sununga. Pehle assistant bolo, phir baat karo",
                        "अब मैं हमेशा सुनूँगा। पहले असिस्टेंट बोलो, फिर बात करो")
                    else Reply("Hamesha sunna band kar diya", "हमेशा सुनना बंद कर दिया")
                                }
                "stop_assistant" -> {
                    main.postDelayed({ onQuit?.invoke() }, 3000)
                    Reply("Band ho raha hoon, phir milte hain", "बंद हो रहा हूँ, फिर मिलते हैं")
                }
                else -> Reply("Ye kaam mujhe abhi nahi aata", "ये काम मुझे अभी नहीं आता")
            }
        } catch (e: Exception) {
            Reply("Nahi ho paaya: ${e.message}", "नहीं हो पाया")
        }
    }

    private fun findApp(name: String): android.content.pm.ResolveInfo? {
        val pm = app.packageManager
        val q = name.lowercase().trim()
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        return apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
    }

    private fun openApp(name: String): Reply {
        val pm = app.packageManager
        val m = findApp(name) ?: return Reply("$name naam ka app nahi mila", "$name नाम का ऐप नहीं मिला")
        val li = pm.getLaunchIntentForPackage(m.activityInfo.packageName)
            ?: return Reply("$name khul nahi paaya", "$name खुल नहीं पाया")
        go(li)
        val label = m.loadLabel(pm).toString()
        return Reply("$label khol raha hoon", "$label खोल रहा हूँ")
    }

    private fun closeApp(name: String): Reply {
        val pm = app.packageManager
        val m = findApp(name) ?: return Reply("$name naam ka app nahi mila", "$name नाम का ऐप नहीं मिला")
        val pkg = m.activityInfo.packageName
        val label = m.loadLabel(pm).toString()
        if (pkg == app.packageName) {
            return Reply("Mujhe band karna ho to bolo: band ho ja", "मुझे बंद करना हो तो बोलो: बंद हो जा")
        }
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(pkg)
        return Reply("$label ko background se band kar diya", "$label को बैकग्राउंड से बंद कर दिया")
    }

    private fun setAlarm(h: Int, m: Int, label: String): Reply {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        go(i)
        val mm = "%02d".format(m)
        return Reply("Alarm $h:$mm ke liye set kar diya", "अलार्म $h:$mm के लिए सेट कर दिया")
    }

    private fun setTimer(sec: Int): Reply {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, sec)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        go(i)
        return Reply("$sec second ka timer laga diya", "$sec सेकंड का टाइमर लगा दिया")
    }

    private fun findNumber(q: String): String? {
        val onlyNumber = q.none { it.isLetter() } && q.count { it.isDigit() } >= 7
        if (onlyNumber) return q.filter { it.isDigit() || it == '+' }
        if (app.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val c = app.contentResolver.query(
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
        if (app.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return Reply("Call ki permission nahi hai. App kholkar permission do",
                "कॉल की परमिशन नहीं है। ऐप खोलकर परमिशन दो")
        }
        val num = findNumber(who) ?: return noNumber(who)
        go(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num))))
        return Reply("$who ko call laga raha hoon", "$who को कॉल लगा रहा हूँ")
    }

    private fun whatsapp(who: String, msg: String): Reply {
        val num = findNumber(who) ?: return noNumber(who)
        var d = num.filter { it.isDigit() }
        if (d.length == 10) d = "91$d"
        else if (d.length == 11 && d.startsWith("0")) d = "91" + d.drop(1)
        val url = Uri.parse("https://wa.me/$d?text=" + Uri.encode(msg))
        try {
            go(Intent(Intent.ACTION_VIEW, url).setPackage("com.whatsapp"))
        } catch (e: Exception) {
            go(Intent(Intent.ACTION_VIEW, url))
        }
        return Reply("WhatsApp khol diya, message likh diya hai, bhejne ke liye send dabao",
            "व्हाट्सऐप खोल दिया, मैसेज लिख दिया है, भेजने के लिए सेंड दबाओ")
    }

    private fun sms(who: String, msg: String): Reply {
        val num = findNumber(who) ?: return noNumber(who)
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num)))
            .putExtra("sms_body", msg)
        go(i)
        return Reply("SMS taiyaar hai, bhejne ke liye send dabao", "एसएमएस तैयार है, भेजने के लिए सेंड दबाओ")
    }

    private fun torch(on: Boolean): Reply {
        val cm = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return Reply("Is phone mein flash nahi mila", "इस फोन में फ्लैश नहीं मिला")
        cm.setTorchMode(id, on)
        return if (on) Reply("Torch chalu kar di", "टॉर्च चालू कर दी")
        else Reply("Torch band kar di", "टॉर्च बंद कर दी")
    }

    private fun openUrl(url: String, msg: Reply): Reply {
        go(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
        go(Intent(action))
        return Reply("Settings khol di", "सेटिंग खोल दी")
    }
}
