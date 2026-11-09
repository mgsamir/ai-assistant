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
        private val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite", "gemini-3.6-flash", "gemini-3.8-flash", "gemini-2.5-flash")

    // "assistant" जैसा शब्द बोलने पर ही ambient मोड में जवाब देता है
    private val WAKE = listOf("assistant", "asistant", "jarvis", "असिस्टेंट", "असिस्टैंट", "एसिस्टेंट", "जार्विस", "जारविस", "सुनो", "ओए")

    private val PERSONA = """
You are a professional personal voice assistant.
Tone: calm, polite, direct and professional. NEVER address the user as "bhai", "dost", "yaar" or "boss", and never use friendly filler like "haan bhai" or "theek hai dost". Say "tum", never "aap".
The user lives in Hut Bay, Little Andaman, Andaman and Nicobar Islands, India. "Hut Bay" or "Hutbay" ALWAYS means this place, NEVER Hout Bay in South Africa. For "here", "my area", local news, ferry, weather, shops or government work, mean Hut Bay / Little Andaman. Never use English news words such as accused, listed, tenders, jetty, rescue operation, coastal cleanup, restrictions, delays. Say them in plain Hindi instead: aaropi, soochi mein, sarkari theka, ghat, bachav kaam, safai, rok, deri. Keep only words everyone uses (police, hospital, phone, bus). Never write English month-year phrases like "2026 ki ... news"; just say the date in simple words. Language: ALWAYS reply in simple everyday Hindustani, the Hindi and Urdu mix that people speak in India, with common English words (phone, alarm, message, call, battery, flashlight) kept as they are. Speak the way Gemini speaks to an ordinary Indian: clear, natural, easy words that people really use in daily talk, with festival and calendar words as people say them (tithi, tyohar, subah, shaam). Never use English office words like listed, countdown, top events, till, schedule details, and never leave an English phrase half translated. Explain in plain words so a person with little schooling understands. Never use heavy Sanskrit or shuddh Hindi words (not "prataha kaal" or "abhinandan"; say "subah" and "shukriya"). This stays the same even if the user speaks pure Hindi.
The user speaks mixed Hindi, English and some Andaman local words (for example "mera ko" means "mujhe"). If words from Bengali, Tamil, Telugu or another local language appear, work out the meaning and answer in Hindustani.
Voice-to-text is often wrong: it drops Hindi vowel marks and mishears words (for example "kitne baje" may arrive as "kutte"). Use the earlier messages and common sense to work out what the user really meant instead of taking every word literally.
Before a phone action (call, message, SMS, open app, close app, alarm, YouTube search), check that the request makes sense. If the words are unclear, look like a mishearing, or a contact name is doubtful, do NOT use a function. Ask one short question such as "Kisko call karna hai?" or "Tumne kya kaha, dobara bolo" and wait. If the request is clear, act at once without asking.
The current date and time are given at the end of these instructions. For any question about time, date or day, use them exactly. Say the time in 12 hour style with subah, dopahar, shaam or raat. Never guess or invent the time.
NEVER start a reply with filler such as "haan", "haan bilkul", "bilkul", "zaroor", "samajh gaya", "theek hai", "sure" or "of course". Start directly with the answer. For phone actions and small talk keep replies short (1 to 2 sentences). For information questions (news, dates, festivals, facts, history, science, how or why, explanations) give a complete, clear answer in one go: the direct answer first, then the key details such as dates, day, timings, reasons and anything that could confuse, so the user needs no follow-up question. Usually 4 to 8 sentences.
ALWAYS write every reply as exactly two lines:
S: the reply in Hinglish, meaning Hindi written in English (Roman) letters, for the screen
V: the same reply in Hindi Devanagari script, for the voice
If the user speaks English, write both lines in English.
Never use markdown, asterisks, hashes or emojis.
When the user asks for a phone action (call, alarm, timer, open app, close app, message, torch, settings), do not just talk: use the provided functions.
When the user says to stop or quit the assistant (for example "band ho ja", "stop assistant", "bas ab chup"), call stop_assistant.
For weather ALWAYS use the get_weather function, even for small places, villages and islands (like Little Andaman, Hut Bay, Diglipur). Never say weather information is unavailable. Write the place name in English. If the user's city is in the remembered facts use it, otherwise ask.
For news, scores, prices, current events, facts, people, places, "kya hai", "kaun hai", "kitna hai" or anything you are not fully sure about, ALWAYS call web_search with the question. It searches Google inside the app and gives you the answer. Then just tell the answer. The web_search query must be complete on its own: for follow-ups like "kab ka baat hai" or "or silver" add the topic and place from the earlier chat into the query. If the user only asks you to speak in Hindi or simpler words (for example "Hindi mein bolo"), do NOT search; just repeat your last answer in easy Hindi. NEVER tell the user to search on Google, never offer to open Google or any website, and never say you have no live information.
When the user says "yaad rakhna" or tells you a lasting fact about himself (name, city, family, likes, habits, important dates), save it with the remember function. When he says "bhool jao", use the forget function.
Never save passwords, OTPs, card numbers or bank details.
The user is an adult. Answer questions about the human body, sex education, reproduction (how babies are made), puberty, contraception, relationships and sexual health plainly, factually and without embarrassment or lecturing, like a good doctor or biology teacher. Do not refuse such questions and do not say they are against guidelines. Keep it informative, not pornographic.
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
    private val voiceFor = HashMap<String, String>()
    // typed sawal ka jawab sirf likha aaye, bola na jaye
    @Volatile private var silent = false
    // Baat-cheet mode (orb screen): mic tab tak chalu jab tak band na karo
    private var convo = false
    private var muted = false
    private fun live() = ambient || (convo && !muted)

    // Baat-cheet mode mein har baar mic chalu hone ki beep na aaye
    private val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val beepStreams = intArrayOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_MUSIC)
    private val beepMutedByUs = BooleanArray(3)
    private fun muteBeeps() {
        for (i in beepStreams.indices) {
            try {
                if (!am.isStreamMute(beepStreams[i])) {
                    am.adjustStreamVolume(beepStreams[i], AudioManager.ADJUST_MUTE, 0)
                    beepMutedByUs[i] = true
                }
            } catch (e: Exception) { }
        }
    }
    private fun unmuteBeeps() {
        for (i in beepStreams.indices) {
            if (!beepMutedByUs[i]) continue
            try { am.adjustStreamVolume(beepStreams[i], AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) { }
            beepMutedByUs[i] = false
        }
    }

    init {
        Usage.p = prefs
        // Hamesha sunne wala mode band hai. Mic sirf button dabane par sunta hai.
        ambient = false
        prefs.edit().putBoolean("ambient", false).apply()
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
        @JvmName("applyAmbientMode")
    fun setAmbient(on: Boolean) {
        // Hamesha sunna band hai, isliye "on" ko ignore karte hain
        ambient = false
        prefs.edit().putBoolean("ambient", false).apply()
    }

    fun shutdown() {
        running = false
        main.removeCallbacksAndMessages(null)
        unmuteBeeps()
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

    // ---- bolna: ElevenLabs ki natural awaaz, na chale to phone ki apni awaaz ----
    private var mp: android.media.MediaPlayer? = null
    @Volatile private var speakGen = 0

    private fun stopSpeech() {
        speakGen++
        try { tts.stop() } catch (e: Exception) { }
        try { mp?.stop() } catch (e: Exception) { }
        try { mp?.release() } catch (e: Exception) { }
        mp = null
    }

    private fun speak(v: String): Boolean {
        if (Extra.eleven.isEmpty()) return tts.speak(v, TextToSpeech.QUEUE_FLUSH, null, "r") == TextToSpeech.SUCCESS
        val gen = ++speakGen
        thread {
            val bytes = Providers.eleven(v)
            main.post {
                if (gen != speakGen) return@post
                if (bytes == null) {
                    if (tts.speak(v, TextToSpeech.QUEUE_FLUSH, null, "r") != TextToSpeech.SUCCESS) speakDone()
                    return@post
                }
                try {
                    val f = java.io.File(app.cacheDir, "say.mp3")
                    f.writeBytes(bytes)
                    try { mp?.release() } catch (e: Exception) { }
                    val m = android.media.MediaPlayer()
                    mp = m
                    m.setDataSource(f.absolutePath)
                    m.setOnPreparedListener { it.start(); mode(3) }
                    m.setOnCompletionListener { main.post { if (mp === it) speakDone() } }
                    m.setOnErrorListener { _, _, _ -> main.post { speakDone() }; true }
                    m.prepareAsync()
                } catch (e: Exception) {
                    speakDone()
                }
            }
        }
        return true
    }

    private fun speakDone() {
        busy = false
        if (convo && !muted) mode(1) else mode(0)
        if (ambient) {
            // जवाब के बाद 7 सेकंड तक wake word बोले बिना बात आगे बढ़ा सकते हो
            awaiting = SystemClock.uptimeMillis() + 7000
            status("Bolo, main sun raha hoon...")
            scheduleListen(350)
        } else if (convo) {
            // jawab ke baad mic apne aap dobara chalu
            status("Bolo, main sun raha hoon...")
            scheduleListen(350)
        }
    }

    // ---------- सुनना ----------
    private fun scheduleListen(delay: Long) {
        main.postDelayed({
            if (running && live() && !listening && !busy) beginListen()
        }, delay)
    }
    // Jawab ke neeche speaker dabane par wahi jawab dobara bolo (dobara dabane par ruk jao)
    fun isBusy(): Boolean = busy
    fun replay(screenText: String) {
        if (busy) {
            stopSpeech()
            busy = false
            mode(0)
            return
        }
        val v = voiceFor[screenText] ?: screenText
        busy = true
        if (!speak(v)) {
            busy = false
            mode(0)
        }
    }

    // टाइप करके भेजा हुआ सवाल
    fun sendText(q: String, speak: Boolean = false, att: Pair<String, String>? = null) {
        val ls = liveSess
        if (att == null && speak && ls != null && ls.ready) { ls.sendText(q.trim()); return }
        if (q.isBlank() || busy) return
        awaiting = 0L
        silent = !speak
        if (listening) {
            try { recognizer?.cancel() } catch (e: Exception) { }
            listening = false
        }
        ask(q.trim(), att)
    }
    // मिक/ऑर्ब बटन दबाने पर
    fun talk() {
        silent = false
        awaiting = SystemClock.uptimeMillis() + 8000
        if (busy) {
            stopSpeech()
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

    // ---------- Gemini Live (seedhi awaaz) ----------
    private var liveSess: LiveSession? = null
    private val liveCb = object : LiveSession.Cb {
        override fun status(s: String) { main.post { this@Assistant.status(s) } }
        override fun mode(m: Int) { main.post { this@Assistant.mode(m) } }
        override fun level(v: Float) { this@Assistant.level(v) }
        override fun chat(s: String) { main.post { this@Assistant.chat(s) } }
        override fun system(): String = systemText() + "\n\nVOICE CONVERSATION MODE: You are talking live by voice. Ignore the two line S:/V: format. Just speak naturally in simple everyday Hindustani (Hindi-Urdu mix, Roman words like phone, alarm kept in English). Keep small talk short, but for information questions give the full answer with key details. Never read out markdown or symbols."
        override fun tools(): JSONArray = this@Assistant.tools()
        override fun tool(name: String, args: JSONObject): String {
            if (name == "get_weather") return Weather.today(args.optString("city", "")).text
            if (name == "web_search") {
                return webAnswer(Keys.get(prefs), args.optString("query", "")).text
            }
            var out = ""
            val latch = java.util.concurrent.CountDownLatch(1)
            main.post {
                try { out = runAction(name, args).text } catch (e: Exception) { out = "Nahi ho paaya" }
                latch.countDown()
            }
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
            return out
        }
        override fun failed(reason: String) {
            main.post {
                liveSess = null
                this@Assistant.chat("\nAI: Live nahi chala:\n$reason\n")
                if (convo && !muted && !listening && !busy) beginListen()
                val msg = "Live nahi chala: " + reason.lines().firstOrNull().orEmpty().take(80)
                main.postDelayed({ this@Assistant.status(msg) }, 700)
                main.postDelayed({ this@Assistant.status(msg) }, 2500)
            }
        }
    }

    fun startConvo() {
        convo = true
        muted = false
        silent = false
        val key = Keys.get(prefs)
        if (key.isNotEmpty() && app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            stopSpeech()
            busy = false
            liveSess?.stop()
            liveSess = LiveSession(app, key, liveCb).also { it.start() }
            return
        }
        if (busy) {
            stopSpeech()
            busy = false
        }
        retried = false
        errs = 0
        if (!listening) beginListen()
    }

    fun stopConvo() {
        convo = false
        muted = false
        liveSess?.stop()
        liveSess = null
        main.removeCallbacksAndMessages(null)
        unmuteBeeps()
        stopSpeech()
        busy = false
        if (listening) {
            try { recognizer?.cancel() } catch (e: Exception) { }
            listening = false
        }
        mode(0)
    }

    fun setMuted(m: Boolean) {
        muted = m
        val ls = liveSess
        if (ls != null) {
            ls.muted = m
            if (m) { mode(0); status("Mic band hai") } else { mode(1); status("Bolo, main sun raha hoon...") }
            return
        }
        if (m) {
            if (listening) {
                try { recognizer?.cancel() } catch (e: Exception) { }
                listening = false
            }
            mode(0)
            status("Mic band hai")
        } else if (convo && !busy && !listening) {
            beginListen()
        }
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
                    if (!convo) mode(0)
                    if (live()) scheduleListen(200) else status("Kuch sunai nahi diya")
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
        if (convo) {
            muteBeeps()
            main.postDelayed({ unmuteBeeps() }, 500)
        }
        try {
            recognizer?.startListening(i)
        } catch (e: Exception) {
            listening = false
            status("Mic shuru nahi hua")
            if (live()) scheduleListen(2000)
        }
    }

    private fun handleError(e: Int) {
        listening = false
        if (convo && muted) { mode(0); return }
        if (e == 9) {
            // मिक की permission नहीं
            convo = false
            setAmbient(false)
            status("Mic ki permission nahi hai")
            return
        }
        if (live()) {
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

        @Volatile var learn = false
    var personal: Boolean
        get() = prefs.getBoolean("pi", true)
        set(v) { prefs.edit().putBoolean("pi", v).apply() }

    fun research(q: String, cb: (String) -> Unit) {
        val key = Keys.get(prefs)
        val p = if (personal) { val m = loadMem(); if (m.isEmpty()) "" else "Facts about the user you may use: " + m.joinToString("; ") } else ""
        thread { cb(Research.run(key, q, p)) }
    }

    fun securityScan(cb: (String) -> Unit) { thread { cb(Security.report(app)) } }

    fun agent(q: String, cb: (String) -> Unit) {
        val key = Keys.get(prefs)
        val id = Models.sel(prefs, "agent")?.id ?: "antigravity-preview-05-2026"
        thread { cb(Agent.run(key, q, id)) }
    }

    private fun systemText(): String {
        val mem = if (personal) loadMem() else emptyList()
        val now = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", Locale.ENGLISH).format(java.util.Date())
        var base = PERSONA + "\n\nCurrent date and time on the user's phone: " + now
        val apps = installedApps()
        if (apps.isNotEmpty()) base += "\n\nApps installed on the user's phone right now (${apps.size}): " +
            apps.take(250).joinToString(", ") { it.first } +
            ". To open one use open_app with its exact name from this list (the user may say it in Hindi or with a spelling difference; pick the closest). If the app is not in this list it is not installed (or was deleted): say so and do not call open_app."
        if (learn) base += "\n\nGUIDED LEARNING MODE is ON. Act like a patient teacher: teach step by step in small easy steps, use simple examples from daily life in India, give only one step at a time (3 to 5 sentences), then ask the user one short question to check understanding or ask if he wants the next step. Do not dump the whole answer at once. This overrides the rule to give complete long answers."
        if (mem.isEmpty()) return base
        return base + "\n\nFacts remembered about the user:\n" + mem.joinToString("\n") { "- $it" }
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
        f.put(fn("list_apps", "List every app installed on the phone right now (use when the user asks which apps he has)",
            JSONObject().put("filter", str("Optional word to filter app names, usually empty")), emptyList()))
        f.put(fn("security_scan", "Scan the phone for suspicious, fake, hidden or spying apps (virus check). Use when the user asks for virus check, security check, phone scan, hacker or fraud check",
            JSONObject().put("note", str("Optional, usually empty")), emptyList()))
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
        f.put(fn("web_search", "Search Google inside the app and get the real answer. Use for any news, live info, facts or question you are not sure about",
            JSONObject().put("query", str("What to search")), listOf("query")))
        f.put(fn("youtube_search", "Search or play something on YouTube",
            JSONObject().put("query", str("What to search")), listOf("query")))
        f.put(fn("show_on_earth", "Show a place on Google Earth (3D satellite view). Use when the user says Google Earth, earth mein dikhao, satellite view",
            JSONObject().put("place", str("Place name")), listOf("place")))
        f.put(fn("open_settings", "Open phone settings",
            JSONObject().put("page", str("wifi, bluetooth, location, display, sound or general")),
            listOf("page")))
        f.put(fn("remember", "Remember a lasting fact about the user forever",
            JSONObject().put("fact", str("The fact, as one short clear sentence")),
            listOf("fact")))
        f.put(fn("forget", "Forget a remembered fact",
            JSONObject().put("keyword", str("Main word of the fact to forget. Use ALL to forget everything")),
            listOf("keyword")))
                f.put(fn("wake_screen", "Turn the phone screen on (wake up the display) when the user says screen on karo, display chalu karo, phone jagao",
            JSONObject().put("seconds", num("How many seconds to keep it on, default 20")), listOf()))
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
    // Google se jawab; na mile to Brave ke natije + Groq/OpenRouter se; tab bhi na mile to wajah dikhao
    private fun webAnswer(key: String, q0: String): Reply {
        val q = if (Regex("hut\\s*bay", RegexOption.IGNORE_CASE).containsMatchIn(q0) && !q0.contains("andaman", true)) q0 + " Little Andaman Andaman Nicobar India" else q0
        val brave = Providers.brave(q)
        val ws = Models.sel(prefs, "web")
        if (ws != null && ws.prov != "gemini") {
            val t = Providers.chatWith(ws.prov, ws.id,
                "Search the internet and answer simply in everyday Hindustani, 2 to 6 short sentences, no markdown. The user lives in Hut Bay, Little Andaman, India. Write exactly two lines:\nS: answer in Hinglish (Roman letters)\nV: same answer in Hindi Devanagari",
                emptyList(), q)
            if (t != null) return parseReply(t)
        }
        val raw = WebSearch.ask(key, q, if (ws != null && ws.prov == "gemini") listOf(ws.id) else emptyList(), brave)
        if (raw != null) return parseReply(raw)
        if (brave != null) {
            val sys = "Answer the question using ONLY these search results. Simple everyday Hindustani, 1 to 4 short sentences, no markdown. " +
                "Write exactly two lines:\nS: answer in Hinglish (Roman letters)\nV: same answer in Hindi Devanagari"
            val fb = Providers.fallback(sys, emptyList(), q + "\n\nSearch results:\n" + brave)
            if (fb != null) return parseReply(fb)
        }
        val why = WebSearch.lastErr.ifBlank { "pata nahi" }
        return Reply("Search nahi chala ($why). Dobara try karo.", "सर्च नहीं चला। दोबारा कोशिश करो।")
    }

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
                        code == 0 ->
                Reply("Jawab nahi aaya. Net ya Google ka model dheema hai, dobara bolo.",
                    "जवाब नहीं आया। नेट या गूगल का मॉडल धीमा है, दोबारा बोलो।")
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
        voiceFor[r.text] = r.voice
        
        if (save) {
            history.add(Pair(q, "S: ${r.text}\nV: ${r.voice}"))
            while (history.size > 12) history.removeAt(0)
        }
        if (silent) {
            busy = false
            if (convo && !muted) mode(1) else mode(0)
            if (live()) scheduleListen(300)
            return
        }
        busy = true
        if (!speak(r.voice)) {
            busy = false
            mode(0)
            if (live()) scheduleListen(500)
        }
    }

    private fun ask(q: String, att: Pair<String, String>? = null) {
        val key = Keys.get(prefs)
        if (key.isEmpty()) {
            mode(0)
            status("Pehle app kholkar gear dabao aur API key save karo")
            if (live()) scheduleListen(3000)
            return
        }
        busy = true
        mode(2)
        status("Soch raha hoon...")
        chat("\nTum: $q\n")
        thread {
            try {
                val codeQ = Regex("(?i)\\b(code|coding|program|script|function|bug|error|kotlin|java|python|html|css|javascript|sql|api|app banao|website banao)\\b|कोड|प्रोग्राम").containsMatchIn(q)
                val pick0 = (if (codeQ) Models.sel(prefs, "coding") else null) ?: Models.sel(prefs, "chat")
                val pick = if (pick0 != null && Usage.paidOnly(pick0)) null else pick0
                var okModel = ""
                fun note(who: String) {
                    if (pick == null) return
                    val nm = Models.list(prefs).firstOrNull { it.id == who }?.name ?: who
                    main.post { android.widget.Toast.makeText(app, "${pick.name} nahi chala, $nm se jawab aaya", android.widget.Toast.LENGTH_LONG).show() }
                }
                if (pick != null && pick.prov != "gemini" && att == null) {
                    val txt = Providers.chatWith(pick.prov, pick.id, systemText(), history.takeLast(6), q)
                    if (txt != null) {
                        main.post { deliver(parseReply(txt), q, true) }
                        return@thread
                    }
                }
                val order = if (pick != null && pick.prov == "gemini") listOf(pick.id) + MODELS else MODELS
                val contents = JSONArray()
                for ((u, a) in history.takeLast(6)) {
                    contents.put(JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", u))))
                    contents.put(JSONObject().put("role", "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", a))))
                }
                val lastParts = JSONArray().put(JSONObject().put("text", q))
                if (att != null) lastParts.put(JSONObject().put("inlineData",
                    JSONObject().put("mimeType", att.first).put("data", att.second)))
                contents.put(JSONObject().put("role", "user").put("parts", lastParts))
                val body = JSONObject()
                    .put("system_instruction", JSONObject().put("parts",
                        JSONArray().put(JSONObject().put("text", systemText()))))
                    .put("tools", tools())
                    .put("safetySettings", WebSearch.safety())
                    .put("contents", contents)
                var code = 0
                var resp = ""
                val t0 = SystemClock.uptimeMillis()
                for (m in order) {
                    if (SystemClock.uptimeMillis() - t0 > 40000) break
                    try {
                        val lvl = Models.think(prefs)
                        val rb = JSONObject(body.toString())
                        if (m.startsWith("gemini-3")) rb.put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingLevel", lvl)))
                        else if (m == "gemini-2.5-flash" || m == "gemini-2.5-pro") rb.put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingBudget", if (lvl == "high") 8192 else 2048)))
                        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent")
                            .openConnection() as HttpURLConnection
                        c.requestMethod = "POST"
                        c.connectTimeout = 8000
                        c.readTimeout = 12000
                        c.setRequestProperty("Content-Type", "application/json")
                        c.setRequestProperty("x-goog-api-key", key)
                        c.doOutput = true
                        c.outputStream.use { it.write(rb.toString().toByteArray()) }
                        code = c.responseCode
                        if (code == 200) okModel = m
                        resp = (if (code == 200) c.inputStream else c.errorStream).bufferedReader().readText()
                        if (code == 200) Usage.hit("gemini", m, try { JSONObject(resp).optJSONObject("usageMetadata")?.optInt("totalTokenCount") ?: 0 } catch (e: Exception) { 0 })
                        if (code == 200 || code == 401 || code == 403 || (code == 400 && resp.contains("API key", ignoreCase = true))) break
                    } catch (e: Exception) {
                        code = 0
                        resp = ""
                    }
                }
                if (code == 200 && pick != null && okModel != pick.id) note(okModel)
                if (code != 200) {
                    val fb = Providers.fallback(systemText(), history.takeLast(6), q)
                    if (fb != null) {
                        note("dusre model")
                        main.post { deliver(parseReply(fb), q, true) }
                        return@thread
                    }
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
                    } else if (c.getString("name") == "web_search") {
                        val qq = c.optJSONObject("args")?.optString("query", "") ?: ""
                        pre[idx] = webAnswer(key, qq)
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
                    if (live()) scheduleListen(2000)
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
                "list_apps" -> listApps()
                "security_scan" -> { val t = Security.report(app); Reply(t, t) }
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
                "show_on_earth" -> { openEarth(a.getString("place")); Reply("Google Earth browser mein khol raha hoon", "गूगल अर्थ ब्राउज़र में खोल रहा हूँ") }
                "open_settings" -> openSettings(a.optString("page", "general"))
                "wake_screen" -> wakeScreen(a.optInt("seconds", 20))
                "remember" -> remember(a.getString("fact"))
                "forget" -> forget(a.getString("keyword"))
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

    // Phone mein abhi jitne app hain (naam, package). Install/delete par apne aap naya ho jaata hai
    @Volatile private var appsCache: List<Pair<String, String>>? = null
    private var appsReceiver: android.content.BroadcastReceiver? = null

    private fun watchApps() {
        if (appsReceiver != null) return
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                appsCache = null
                if (i?.action == Intent.ACTION_PACKAGE_ADDED && !i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    val pkg = i.data?.schemeSpecificPart ?: return
                    thread { Security.checkNew(app, pkg)?.let { Security.notify(app, it); Security.addLog(prefs, listOf(it), setOf(pkg)) } }
                }
            }
        }
        val f = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        }
        try { app.registerReceiver(r, f); appsReceiver = r } catch (e: Exception) { }
    }

    fun installedApps(): List<Pair<String, String>> {
        watchApps()
        appsCache?.let { return it }
        val pm = app.packageManager
        val l = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.second != app.packageName && it.first.isNotBlank() }
                .distinctBy { it.second }
                .sortedBy { it.first.lowercase() }
        } catch (e: Exception) { emptyList() }
        appsCache = l
        return l
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun findApp(name: String): android.content.pm.ResolveInfo? {
        val pm = app.packageManager
        val q = norm(name)
        if (q.isEmpty()) return null
        val l = installedApps()
        val hit = l.firstOrNull { norm(it.first) == q }
            ?: l.firstOrNull { it.second.lowercase() == name.lowercase().trim() }
            ?: l.firstOrNull { norm(it.first).contains(q) }
            ?: l.firstOrNull { q.contains(norm(it.first)) && norm(it.first).length >= 3 }
            ?: return null
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(hit.second), 0)
            .firstOrNull()
    }

    private fun listApps(): Reply {
        val l = installedApps()
        if (l.isEmpty()) return Reply("Koi app nahi mila", "कोई ऐप नहीं मिला")
        val names = l.joinToString(", ") { it.first }
        return Reply("Phone mein ${l.size} app hain: $names", "फ़ोन में ${l.size} ऐप हैं: $names")
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
        go(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        main.postDelayed({ try { am.killBackgroundProcesses(pkg) } catch (e: Exception) { } }, 1500)
        main.postDelayed({ try { am.killBackgroundProcesses(pkg) } catch (e: Exception) { } }, 4000)
        return Reply("$label band kar raha hoon", "$label बंद कर रहा हूँ")
    }
    @Suppress("DEPRECATION")
    private fun wakeScreen(sec: Int): Reply {
        val pm = app.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val wl = pm.newWakeLock(
            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "assistant:wake")
        wl.acquire(sec.coerceIn(5, 120) * 1000L)
        return Reply("Screen on kar di", "स्क्रीन चालू कर दी")
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

    // Google Earth browser mein
    fun openEarth(place: String) {
        go(Intent(Intent.ACTION_VIEW, Uri.parse("https://earth.google.com/web/search/" + Uri.encode(place))))
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
