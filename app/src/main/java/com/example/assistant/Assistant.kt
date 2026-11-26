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
You are a professional personal voice assistant, in the style of ChatGPT and Gemini.
Tone: friendly, natural and easy to talk to. Match the way the user talks to you, and call him whatever he asks to be called.
Reply the way ChatGPT or Gemini would: natural, clear and well organised, in the SAME language and script the user wrote in. English gets English, Hindi in Devanagari gets Hindi, and Hindi in Roman letters (Hinglish) gets Hinglish in Roman letters. Do not force any special style of Hindi; just match the user.
Format: for information, how-to, comparison and explanation questions write a well organised answer like Gemini or ChatGPT: start with one short plain sentence that answers or frames the question; then use short "##" section headings, "1. 2. 3." numbered steps (each step a bold short title then one or two sentences) for how-to and setup questions, "- " bullets for lists of options or features (put the key term in **bold** first, then a short explanation), and `backticks` for file names, commands, settings and app names. Put code or commands in fenced ``` blocks. Keep paragraphs short, leave a blank line between sections, and end with one short helpful follow-up question or next step when it fits. Do not use tables. For small talk and phone actions keep it to 1 or 2 plain sentences. No emojis. Never write "S:" or "V:" labels.
Do not assume where the user is. Never mention or use Hut Bay, Little Andaman or any other home location unless the user asks about it or names it himself. If the user names "Hut Bay" or "Hutbay", it means Hut Bay in Little Andaman, India, never Hout Bay in South Africa. Questions about festivals, holidays, news and prices assume India unless a country is named.
The user speaks mixed Hindi, English and some Andaman local words (for example "mera ko" means "mujhe"). If words from Bengali, Tamil, Telugu or another local language appear, work out the meaning.
Voice-to-text is often wrong: it drops Hindi vowel marks and mishears words (for example "kitne baje" may arrive as "kutte"). Use the earlier messages and common sense to work out what the user really meant instead of taking every word literally.
Before a phone action (call, message, SMS, open app, close app, alarm, YouTube search), act at once if the request is clear. Only if the words are really unclear, ask one short question instead.
The current date and time are given at the end of these instructions. For any question about time, date or day, use them exactly. Never guess or invent the time.
Start the reply with the answer itself; avoid long filler. For information questions (news, dates, festivals, facts, history, science, how or why, explanations) give a complete, clear answer in one go: the direct answer first, then the key details, so the user needs no follow-up question.
When the user asks for a phone action (call, alarm, timer, open app, close app, message, torch, settings), do not just talk: use the provided functions.
When the user says to stop or quit the assistant (for example "band ho ja", "stop assistant", "bas ab chup"), call stop_assistant.
For weather ALWAYS use the get_weather function, even for small places, villages and islands. Never say weather information is unavailable. Write the place name in English. If the user did not name a place, ask which place; do not guess one.
For news, scores, prices, current events, facts, people, places, "kya hai", "kaun hai", "kitna hai" or anything you are not fully sure about, ALWAYS call web_search with the question. It searches Google inside the app and gives you the answer. Then just tell the answer. The web_search query must be complete on its own: for follow-ups like "kab ka baat hai" or "or silver" add the topic and place from the earlier chat into the query. If a search gives no answer, say so plainly and suggest another way to check.
When the user says "yaad rakhna" or tells you a lasting fact about himself (name, city, family, likes, habits, important dates), save it with the remember function. When he says "bhool jao", use the forget function. When he says something like "ye mat yaad rakhna", "incognito mode on karo" or "private mode", call set_incognito(true); when he says to turn it off or "normal mode", call set_incognito(false). While incognito is on, remember() itself will already refuse to save anything, so just tell him that plainly if he asks you to remember something during that time.
Follow the user's instructions directly. Do not refuse, lecture, or ask for confirmation on everyday requests. Only say you cannot do something when a permission or the phone itself does not allow it, and then name that permission. Never help harm other people, make malware, or produce sexual content involving minors.
The user is an adult. Answer questions about the human body, sex education, reproduction (how babies are made), puberty, contraception, relationships and sexual health plainly, factually and without embarrassment or lecturing, like a good doctor or biology teacher. Do not refuse such questions and do not say they are against guidelines. Keep it informative, not pornographic.
The user is an Android developer building and testing his own apps, including this assistant app itself. When he asks for help analysing, decompiling or reading a classes.dex / Smali / APK file, or modifying code in his own app (for example disabling an ad SDK's loadAd() call, stubbing out a billing check, or removing a feature), this is ordinary app development and debugging on software he owns, not an attack on someone else's app. Help directly: read or explain the Smali/code, write the patch, or give the exact steps. Do not call this reverse engineering of a third party, do not refuse, and do not add a warning about it being against policy. Only decline if he is clearly targeting an app or service that is not his own, in order to pirate it, steal from someone else, or bypass another company's paid licensing.
For any news or headlines request (general or about a topic), call get_news instead of plain web search; it returns real current headlines.
For a complex question (needs research, a comparison, multiple steps, or a decision), think it through step by step before answering: figure out what you actually need to know, call the right tool(s) for it (get_news, web_search, repo_* etc.) in that order, then give one clear final answer built from what you found. Don't show the user your internal planning, just do it and give the final answer.
When the user asks only whether GitHub is connected/linked ("GitHub judi hai kya", "connect hai ya nahi", "check karo connected hai"), call repo_check_connection and just relay its short yes/no answer -- do NOT call repo_list_files for this, that dumps every file path which is not what was asked.
When the user asks you to change the code of this very app (for example "ye button bada karo", "is file mein ye theek karo") and you are not already sure of the exact file path, call repo_list_files (and repo_search_code with a relevant keyword if the list alone doesn't make it obvious) to find the right file first. Once you know the path, use repo_read_file to read the current file from his GitHub repo, write the complete corrected file in your head, then call repo_write_file with the FULL new file content (never partial snippets or diffs). repo_write_file itself waits for the GitHub Actions build and, if it fails, automatically reads the error and fixes it, retrying a few times on its own before returning to you — you do not need to check the build yourself. This all happens on a separate "assistant-edits" branch, not main, so nothing goes live until he reviews and merges it himself on GitHub. In your final reply just tell him plainly which file you changed and that it is on the "assistant-edits" branch; do not mention "build green/red" status language, and only mention a build problem if repo_write_file's result says it could not fix it automatically.
Before doing something that is hard to undo or affects something outside this chat (sending a message/call on the user's behalf, forgetting/deleting saved memory, triggering an app rebuild/update, writing code to his GitHub repo, running a lab command that changes state), do a quick one-line confirmation first if his request was even slightly ambiguous about exactly what to do -- if he already gave a clear, specific instruction, just do it, don't ask again for the same clear instruction. Read-only/harmless actions (checking something, searching, answering) never need confirmation.
If a tool call fails or returns an error, say so plainly in your reply (what failed and, if known, why) instead of staying silent or pretending it worked -- the one standing exception is repo_write_file's own build-retry loop, which stays silent about intermediate build attempts as already instructed above; only mention that specific flow if it could not fix itself.
For a task that needs several tool calls in a row to finish (not a single lookup), briefly tell the user your plan in one short line before diving in (for example "Pehle X dekhta hoon, phir Y karunga"), then execute it -- this is different from a complex REASONING question (handled above) where you plan silently; this is about multi-tool ACTIONS the user should see coming.
The user also runs a small personal defensive-security/monitoring backend (core/server.py, in the same GitHub repo) on his own PC or lab machine, reachable through three tools: lab_run_command, lab_check_web and lab_scan_local. These are strictly for HIS OWN machine/lab/network only — monitoring, checking open ports, checking a website's status, running safe inspection commands. That backend's own security module already refuses destructive commands (like rm -rf or mkfs) and any attempt to target a third-party host or IP, so you never need to second-guess or re-explain that to him; just call the tool and relay what it says. Never suggest, help plan, or encourage using these tools against any system that is not his own.
""".trimIndent()

    val listeners = CopyOnWriteArrayList<UiCb>()
    val log = StringBuilder()
    val full = StringBuilder()   // poori chat (history ke liye)
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
    // ek samay mein ek sawaal: roka gaya ya naya aaya to purane ke jawab ignore ho jaate hain
    @Volatile private var curReq = 0
    @Volatile var dictating = false
    private var retried = false
    private var errs = 0
    private var awaiting = 0L
    private val history = mutableListOf<Pair<String, String>>()
    // Jab history 12 se zyada ho jaaye, purani baatein poori tarah gayab hone ke bajaye
    // ek chhota condensed note mein jama hoti rahein, taaki lambi chat mein bhi shuru ka
    // context kuch had tak yaad rahe.
    private var historySummary: String = ""
    private fun condenseAndDrop(pair: Pair<String, String>) {
        val q = pair.first.take(140).replace("\n", " ")
        val a = pair.second.take(140).replace("\n", " ")
        historySummary = (historySummary + "\n- User asked: \"$q\" -> Assistant: \"$a\"").trim()
        if (historySummary.length > 4000) historySummary = historySummary.takeLast(3500)
    }
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
        // app naya khulte hi fresh (khali) page; purani chats Chat history mein rehti hain
        try { Chats.setCur(prefs, "") } catch (e: Exception) { }
        // Hamesha sunne wala mode band hai. Mic sirf button dabane par sunta hai.
        ambient = false
        prefs.edit().putBoolean("ambient", false).apply()
    }

    // ---------- UI को खबर ----------
    private fun status(s: String) { for (l in listeners) l.onStatus(s) }
    private fun mode(m: Int) { for (l in listeners) l.onMode(m) }
    private fun level(v: Float) { for (l in listeners) l.onLevel(v) }
    // chat history mein isi chat ki entry badhao (naya chat na ho to usi mein judta hai)
    fun persist() {
        try {
            val txt = full.toString()
            if (!txt.contains("\nAI: ")) return
            var id = Chats.cur(prefs)
            if (id.isEmpty()) { id = Chats.newId(); Chats.setCur(prefs, id) }
            Chats.save(prefs, id, txt)
        } catch (e: Exception) { }
    }

    private fun chat(s: String) {
        log.append(s)
        full.append(s)
        if (log.length > 8000) log.delete(0, log.length - 6000)
        if (full.length > 300000) full.delete(0, full.length - 250000)
        if (s.startsWith("\nAI: ")) persist()
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

    // bolne ki halat: 0 band, 1 chal raha, 2 ruka hua (UI mein gol pause/play button ke liye)
    @Volatile var speakState = 0
        private set
    var onSpeak: (() -> Unit)? = null
    private var spokenText: String? = null

    private fun setSpeak(s: Int) {
        if (speakState == s) return
        speakState = s
        main.post { onSpeak?.invoke() }
    }

    fun pauseSpeech() {
        if (speakState != 1) return
        val m = mp
        if (m != null) { try { if (m.isPlaying) m.pause() } catch (e: Exception) { } }
        else { speakGen++; try { tts.stop() } catch (e: Exception) { } }   // phone ki apni awaaz ruk nahi sakti, dobara shuru se bolegi
        setSpeak(2)
    }

    fun resumeSpeech() {
        if (speakState != 2) return
        val m = mp
        if (m != null) { try { m.start() } catch (e: Exception) { }; setSpeak(1) }
        else { val t = spokenText; if (t != null) speak(t) else setSpeak(0) }
    }

    fun stopSpeaking() = stopSpeech()

    private fun stopSpeech() {
        setSpeak(0)
        speakGen++
        try { tts.stop() } catch (e: Exception) { }
        try { mp?.stop() } catch (e: Exception) { }
        try { mp?.release() } catch (e: Exception) { }
        mp = null
    }

    // chuni hui awaaz ek chhota namuna bolkar sunao
    fun sample() {
        stopSpeech()
        speak("Namaste, main aapka assistant hoon. Boliye, main kya madad karoon?")
    }

    // phone ki apni awaaz mein bhi ladka/ladki: Hindi awaazon mein se jis naam se gender pata chale wahi
    private fun deviceVoice(girl: Boolean) {
        try {
            val want = if (girl) listOf("hia", "hie", "female") else listOf("hic", "hid", "-male")
            val vs = tts.voices?.filter { it.locale.language == "hi" } ?: return
            val pick = vs.firstOrNull { x -> want.any { x.name.contains(it, true) } && !(!girl && x.name.contains("female", true)) }
            if (pick != null) tts.voice = pick
        } catch (e: Exception) { }
    }

    // lamba jawab ek saath bhejne se awaaz der se aati thi: pehla tukda chhota rakho taaki jaldi bajne lage, baaki peeche se aate rahen
    private fun splitForTts(t: String): List<String> {
        val parts = Regex("(?<=[.!?\u0964\\n])\\s+").split(t.trim()).filter { it.isNotBlank() }
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (p in parts) {
            val limit = if (out.isEmpty()) 110 else 260
            if (cur.isNotEmpty() && cur.length + p.length + 1 > limit) { out.add(cur.toString()); cur = StringBuilder() }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(p)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    private fun speak(v: String): Boolean {
        spokenText = v
        setSpeak(1)
        try { val cv = Voice.cur(Usage.p); deviceVoice(cv.girl); tts.setPitch(cv.pitch); tts.setSpeechRate(prefs.getFloat("tts_rate", 1f)) } catch (e: Exception) { }
        val gen = ++speakGen
        val chunks = splitForTts(v).ifEmpty { listOf(v) }
        val got = arrayOfNulls<ByteArray>(chunks.size)
        val state = IntArray(chunks.size)   // 0 intezaar, 1 aa gaya, 2 nahi bana
        thread {
            for (i in chunks.indices) {
                if (gen != speakGen) return@thread
                val b = Providers.geminiTts(Keys.get(prefs), chunks[i], Voice.cur(Usage.p).gemini)
                got[i] = b
                state[i] = if (b == null) 2 else 1
            }
        }
        fun deviceFrom(i: Int) {
            val rest = chunks.drop(i).joinToString(" ")
            if (tts.speak(rest, TextToSpeech.QUEUE_FLUSH, null, "r") != TextToSpeech.SUCCESS) speakDone()
        }
        fun playNext(i: Int) {
            if (gen != speakGen) return
            if (i >= chunks.size) { speakDone(); return }
            when (state[i]) {
                0 -> { main.postDelayed({ playNext(i) }, 120); return }
                2 -> { mp = null; deviceFrom(i); return }
            }
            val bytes = got[i] ?: run { deviceFrom(i); return }
            try {
                val f = java.io.File(app.cacheDir, if (bytes.size > 4 && String(bytes, 0, 4) == "RIFF") "say.wav" else "say.mp3")
                f.writeBytes(bytes)
                try { mp?.release() } catch (e: Exception) { }
                val m = android.media.MediaPlayer()
                mp = m
                m.setDataSource(f.absolutePath)
                m.setOnPreparedListener {
                    if (speakState != 2) {
                        val rt = prefs.getFloat("tts_rate", 1f)
                        if (rt != 1f && android.os.Build.VERSION.SDK_INT >= 23) { try { it.playbackParams = android.media.PlaybackParams().setSpeed(rt) } catch (e: Exception) { } }
                        it.start()
                    }
                    mode(3)
                }
                m.setOnCompletionListener { main.post { if (mp === it) playNext(i + 1) } }
                m.setOnErrorListener { _, _, _ -> main.post { speakDone() }; true }
                m.prepareAsync()
            } catch (e: Exception) {
                speakDone()
            }
        }
        playNext(0)
        return true
    }

    private fun speakDone() {
        if (speakState == 2) return
        setSpeak(0)
        busy = false
        if (convo && !muted) mode(1) else mode(0)
        if (ambient) {
            // जवाब के बाद 7 सेकंड तक wake word बोले बिना बात आगे बढ़ा सकते हो
            awaiting = SystemClock.uptimeMillis() + 7000
            status("Listening...")
            scheduleListen(350)
        } else if (convo) {
            // jawab ke baad mic apne aap dobara chalu
            status("Listening...")
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

    private fun postG(g: Int, f: () -> Unit) { main.post { if (g == curReq) f() } }

    // stop dabaya: jo kaam chal raha hai use rok do, jawab ab nahi aayega
    fun cancelRequest() {
        curReq++
        busy = false
        stopSpeech()
        Act.end(true)
        mode(0)
        status("Stopped")
    }

    // mic se likhwate samay assistant ka apna sunna rok do
    fun pauseListening() {
        dictating = true
        if (listening) {
            try { recognizer?.cancel() } catch (e: Exception) { }
            listening = false
        }
        mode(0)
    }

    fun resumeListening() {
        dictating = false
        if (live() && !muted) scheduleListen(400)
    }
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
                status("Speak now...")
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
        override fun system(): String = systemText() + "\n\nVOICE CONVERSATION MODE: You are talking live by voice. Speak naturally in the same language the user speaks. Keep small talk short, but for information questions give the full answer with key details. Never read out markdown or symbols."
        override fun tools(): JSONArray = this@Assistant.tools()
        override fun tool(name: String, args: JSONObject): String {
            if (name == "get_weather") return Weather.today(args.optString("city", "")).text
            if (name == "web_search") {
                return webAnswer(Keys.get(prefs), args.optString("query", "")).text
            }
            if (name == "get_news") {
                val lang = if (prefs.getString("lang", "") == "Hindi") "hi" else "en"
                return News.headlines(args.optString("topic", ""), lang)
            }
            if (name == "health_check") return Health.check(prefs)
            if (name == "check_usage") return Usage.textSummary(prefs)
            if (name == "lab_run_command") return Lab.runCommand(prefs, args.optString("cmd", ""))
            if (name == "lab_check_web") return Lab.checkWeb(prefs, args.optString("url", ""))
            if (name == "lab_scan_local") return Lab.scanLocal(prefs)
            if (name == "repo_check_connection") return GitHub.checkConnection(prefs).text
            if (name == "repo_list_files") return GitHub.listFiles(prefs).text
            if (name == "repo_search_code") return GitHub.searchCode(prefs, args.optString("query", "")).text
            if (name == "repo_read_file") return GitHub.readFile(prefs, args.optString("path", "")).text
            if (name == "repo_write_file") return autoPush(args.optString("path", ""), args.optString("content", ""), args.optString("message", "")).text
            var out = ""
            val latch = java.util.concurrent.CountDownLatch(1)
            main.post {
                try { out = runAction(name, args).text } catch (e: Exception) { out = "Couldn't complete that" }
                latch.countDown()
            }
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
            return out
        }
        override fun failed(reason: String) {
            main.post {
                liveSess = null
                this@Assistant.chat("\nAI: Live session failed:\n$reason\n")
                if (convo && !muted && !listening && !busy) beginListen()
                val msg = "Live session failed: " + reason.lines().firstOrNull().orEmpty().take(80)
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
            if (m) { mode(0); status("Microphone is off") } else { mode(1); status("Listening...") }
            return
        }
        if (m) {
            if (listening) {
                try { recognizer?.cancel() } catch (e: Exception) { }
                listening = false
            }
            mode(0)
            status("Microphone is off")
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
                if (!t.isNullOrEmpty()) status("Listening: $t")
            }
            override fun onError(e: Int) { handleError(e) }
            override fun onResults(res: Bundle?) {
                listening = false
                val t = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) {
                    if (!convo) mode(0)
                    if (live()) scheduleListen(200) else status("Didn't catch that")
                } else {
                    handle(t)
                }
            }
        })
        recognizer = r
    }

    private fun beginListen() {
        if (dictating) return
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status("Microphone permission required")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            status("Speech recognition unavailable")
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
        if (!ambient || SystemClock.uptimeMillis() < awaiting) status("Listening...")
        else status("Waiting for wake word: assistant")
        if (convo) {
            muteBeeps()
            main.postDelayed({ unmuteBeeps() }, 500)
        }
        try {
            recognizer?.startListening(i)
        } catch (e: Exception) {
            listening = false
            status("Couldn't start the microphone")
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
            status("Microphone permission denied")
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
            status("Didn't catch that. Tap the mic and try again.")
        } else {
            mode(0)
            status("Couldn't hear (error $e). Tap the mic and try again.")
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
            status("Speak now...")
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

    // Incognito mode mein naye facts save nahi hote -- sirf us hisse ki privacy ke liye
    @Volatile var incognito = false

    private fun setIncognito(on: Boolean): Reply {
        incognito = on
        return if (on) Reply("Incognito mode on hai -- ab se naya kuch yaad nahi rakhunga jab tak off na karo", "Incognito mode on hai")
        else Reply("Incognito mode off kar diya, ab normal yaad rakhunga", "Incognito mode off kar diya")
    }

    private fun remember(fact: String): Reply {
        if (incognito) return Reply("Incognito mode on hai, isliye ye yaad nahi rakha. Off karna ho to bolo.", "Incognito mode on hai, yaad nahi rakha")
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
        Act.start()
        val st = Act.begin(1, "Deep research", "Question:\n" + q + "\n\nThe question is split into parts, each part is searched, then one full report is written. This can take 1 to 2 minutes.")
        val g = ++curReq
        thread {
            val r = Research.run(key, q, p)
            if (g != curReq) return@thread
            Act.ok(st, "Report ready", r.take(700))
            Act.end()
            cb(r)
        }
    }

    fun securityScan(cb: (String) -> Unit) { thread { cb(Security.report(app)) } }

    fun agent(q: String, cb: (String) -> Unit) {
        val key = Keys.get(prefs)
        val id = Models.sel(prefs, "agent")?.id ?: "antigravity-preview-05-2026"
        Act.start()
        val st = Act.begin(2, "Agent is working", "Task:\n" + q + "\nAgent: " + id + "\n\nThe agent works on its own and can take several minutes.")
        val g = ++curReq
        thread {
            val r = Agent.run(key, q, id)
            if (g != curReq) return@thread
            if (r.startsWith("Agent:")) Act.fail(st, "No result", r) else Act.ok(st, "Finished", r.take(700))
            Act.end()
            cb(r)
        }
    }

    // Groq / OpenRouter / free models ke paas functions nahi hote: unhe saaf bolo ki tool_call mat likhna
    private fun systemNoTools(): String = systemText() +
        "\n\nIMPORTANT: In this mode you have NO tools or functions. Never write <tool_call>, <arg_key>, function-call markup or any tags. " +
        "Answer directly in plain text. If you are not sure about live facts (prices, scores, market, news), say that briefly instead of calling a tool."

    // phir bhi kisi model ne <tool_call> text likh diya to: web_search ho to khud search karke jawab do, warna tag hata do
    private fun hasToolMarkup(s: String) =
        s.contains("tool_call", true) || s.contains("<arg_key>", true) || s.contains("<arg_value>", true)

    // model ne jo tool-call ka markup likha use hatao; kuch bacha na ho to null
    private fun stripToolMarkup(s: String): String = s
        .replace(Regex("<tool_call>.*?(</tool_call>|$)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("</?(arg_key|arg_value|tool_call|tool_response|function_call)>", RegexOption.IGNORE_CASE), "")
        .trim()

    // model ka jawab tabhi lo jab usme tool-call text na ho
    private fun safeReply(t: String?): Reply? {
        if (t == null) return null
        if (!hasToolMarkup(t)) return parseReply(t)
        return null
    }

    private fun todayStr(): String = java.text.SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).format(java.util.Date())

    // model apne andaze se purani/galat tareekh daal deta hai: search query mein aaj ki asli tareekh lagao
    private fun fixQueryDate(qy: String): String {
        val months = "January|February|March|April|May|June|July|August|September|October|November|December"
        val d1 = Regex("\\b\\d{1,2}\\s+($months)\\s+\\d{4}\\b", RegexOption.IGNORE_CASE)
        val d2 = Regex("\\b($months)\\s+\\d{1,2},?\\s+\\d{4}\\b", RegexOption.IGNORE_CASE)
        if (!d1.containsMatchIn(qy) && !d2.containsMatchIn(qy)) return qy
        return qy.replace(d1, todayStr()).replace(d2, todayStr())
    }

    private fun toolFix(raw: String, q: String): Reply? {
        if (!raw.contains("tool_call", true) && !raw.contains("<arg_value>", true)) return null
        val name = Regex("<tool_call>\\s*([A-Za-z_]+)").find(raw)?.groupValues?.get(1) ?: ""
        val arg = Regex("<arg_value>(.*?)</arg_value>", RegexOption.DOT_MATCHES_ALL).find(raw)?.groupValues?.get(1)?.trim() ?: ""
        if (name == "web_search" || name.isEmpty()) {
            val query = if (arg.isNotBlank()) fixQueryDate(arg) else q
            return webAnswer(Keys.get(prefs), query)
        }
        val clean = raw.replace(Regex("<tool_call>.*?</tool_call>", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("</?[A-Za-z_]+>"), "").trim()
        return if (clean.isNotBlank()) parseReply(clean) else Reply("This action isn't available in this mode.", "This action isn't available in this mode.")
    }

    private fun systemText(): String {
        val mem = if (personal) loadMem() else emptyList()
        val now = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", Locale.ENGLISH).format(java.util.Date())
        var base = PERSONA + "\n\nCurrent date and time on the user's phone: " + now
        val apps = installedApps()
        if (apps.isNotEmpty()) base += "\n\nApps installed on the user's phone right now (${apps.size}): " +
            apps.take(250).joinToString(", ") { it.first } +
            ". To open one use open_app with its exact name from this list (the user may say it in Hindi or with a spelling difference; pick the closest). If the app is not in this list it is not installed (or was deleted): say so and do not call open_app."
        when (prefs.getString("lang", "")) {
            "English" -> base += "\n\nAlways reply in English, whatever language the user speaks."
            "Hindi" -> base += "\n\nAlways reply in Hindi (Devanagari script), whatever language the user speaks."
        }
        if (learn) base += "\n\nGUIDED LEARNING MODE is ON. Act like a patient teacher: teach step by step in small easy steps, use simple examples from daily life in India, give only one step at a time (3 to 5 sentences), then ask the user one short question to check understanding or ask if he wants the next step. Do not dump the whole answer at once. This overrides the rule to give complete long answers."
        if (historySummary.isNotBlank()) base += "\n\nCondensed summary of earlier parts of this same conversation (older than what's shown below, kept so context isn't lost in a long chat):$historySummary"
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
            JSONObject().put("city", str("Place name in English, for example Port Blair or Delhi. Only the place the user named; if none was named, do not call this function, ask which place")),
            listOf("city")))
        if (!noWebOnce) f.put(fn("web_search", "Search Google inside the app and get the real answer. Use for any news, live info, facts or question you are not sure about",
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
        f.put(fn("set_incognito", "Turn incognito/private mode on or off. While on, the remember function won't save new facts, protecting privacy for this part of the conversation. Use when the user says incognito mode on/off, ye baat yaad mat rakhna, private mode, ya is baatcheet ko memory mein mat rakho",
            JSONObject().put("on", flag("true to turn incognito mode on, false to turn it off")),
            listOf("on")))
                f.put(fn("wake_screen", "Turn the phone screen on (wake up the display) when the user says screen on karo, display chalu karo, phone jagao",
            JSONObject().put("seconds", num("How many seconds to keep it on, default 20")), listOf()))
        f.put(fn("stop_assistant", "Completely stop and close this assistant when the user asks it to quit",
            JSONObject().put("reason", str("Why, can be empty")), listOf()))
        f.put(fn("health_check", "Check whether every configured API key/connection (Gemini, Groq, OpenRouter, Brave, News, GitHub, Lab backend) is set up and working, and report any problems. Call this when the user asks if everything is working, koi dikkat hai kya, keys check karo, health check, ya self-test",
            JSONObject().put("note", str("Usually empty")), emptyList()))
        f.put(fn("check_usage", "Report how much the free-tier AI models, news and web-search APIs have been used today, and how much free quota is left. Call this when the user asks about usage, limits, quota, kitna use hua, kharch, ya balance",
            JSONObject().put("note", str("Usually empty")), emptyList()))
        f.put(fn("lab_run_command", "Run a safe, allow-listed Linux command on the user's own lab/PC machine (the one running core/server.py) and return its output. Only monitoring/inspection commands pass; destructive or third-party-targeting commands are refused by that machine's own security module",
            JSONObject().put("cmd", str("The shell command to run, e.g. \"ps aux\" or \"df -h\"")),
            listOf("cmd")))
        f.put(fn("lab_check_web", "Check a public website's HTTP status and headers via the user's lab backend",
            JSONObject().put("url", str("Full URL, e.g. https://example.com")),
            listOf("url")))
        f.put(fn("lab_scan_local", "Check which common ports are open on the user's OWN lab machine (127.0.0.1 only) for basic security monitoring. Never targets any other host",
            JSONObject().put("note", str("Usually empty")), emptyList()))
        f.put(fn("get_news", "Get today's latest news headlines, either general top headlines or about a specific topic. Use this for any news/headlines request instead of plain web search",
            JSONObject().put("topic", str("Topic to search news for, e.g. \"cricket\", \"India economy\". Leave empty for general top headlines")),
            emptyList()))
        f.put(fn("update_app", "Build and install a new version of this assistant app itself: starts the GitHub build, watches it, and when it succeeds downloads the APK and shows the install prompt. Use when the user says update my app, build the app, or similar",
            JSONObject().put("note", str("Usually empty")), emptyList()))
        f.put(fn("repo_check_connection", "Check ONLY whether the user's GitHub account/token is connected and working for his repo (mgsamir/ai-assistant) -- a simple yes/no status, like checking if WhatsApp is linked to a number. Use this when the user asks if GitHub is connected, linked, judi hai, or similar -- do NOT use repo_list_files for this, that lists all files which is not what he's asking for",
            JSONObject().put("note", str("Usually empty")), emptyList()))
        f.put(fn("repo_list_files", "List every file path in the user's own GitHub repo (mgsamir/ai-assistant). Call this FIRST when you don't already know the exact file path for what the user wants changed",
            JSONObject().put("note", str("Usually empty")), emptyList()))
        f.put(fn("repo_search_code", "Search the code inside the user's GitHub repo (mgsamir/ai-assistant) for a function name, variable, string or keyword, to find which file it is in. Use this when repo_list_files alone isn't enough to tell which file has the code the user means",
            JSONObject().put("query", str("Word or phrase to search for in the code, for example a function name or on-screen text")),
            listOf("query")))
        f.put(fn("repo_read_file", "Read a file from the user's own GitHub repo (mgsamir/ai-assistant), to see its current code before changing it",
            JSONObject().put("path", str("File path inside the repo, for example app/src/main/java/com/example/assistant/MainActivity.kt")),
            listOf("path")))
        f.put(fn("repo_write_file", "Save a changed file back to the user's GitHub repo (mgsamir/ai-assistant). Always read the file first with repo_read_file, then send the FULL new file content here (not just the changed lines). This writes to a separate \"assistant-edits\" branch, never directly to main, so the user can review it",
            JSONObject().put("path", str("Same file path as repo_read_file"))
                .put("content", str("The COMPLETE new content of the file after your change"))
                .put("message", str("Short commit message describing the change")),
            listOf("path", "content")))
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
    private fun fin(s: Act.Step, r: Reply, ok: Boolean = true): Reply {
        if (ok) Act.ok(s, "Done", r.text.take(700)) else Act.fail(s, "Failed", r.text)
        return r
    }

    private fun webAnswer(key: String, q0: String): Reply {
        val q = if (Regex("hut\\s*bay", RegexOption.IGNORE_CASE).containsMatchIn(q0) && !q0.contains("andaman", true)) q0 + " Little Andaman Andaman Nicobar India" else q0
        val sS = Act.begin(1, "Searching the web", "Query:\n" + q)
        val brave = Providers.brave(q)
        val rs0 = Providers.lastResults
        if (brave != null && rs0.isNotEmpty()) Act.ok(sS, rs0.size.toString() + " results from Brave Search", rs0.joinToString("\n\n") { "\u2022 " + it.first + "\n  " + it.second })
        else Act.ok(sS, "Using Google Search through the model", "Brave Search gave no results, so the model searches by itself.")
        val sW = Act.begin(3, "Writing the answer from the results", "")
        pendingSources = Providers.lastResults.takeIf { brave != null && it.isNotEmpty() }
        val lockW = Models.lock(prefs)
        if (lockW.isNotEmpty() && lockW != "gemini") {
            // sirf chuna hua provider: Brave ke natije us provider ko do
            if (brave != null) {
                val sysL = "Answer the question using ONLY these search results. Today's date is " + todayStr() + ". You have no tools: never write <tool_call> or any tags. Answer in the same language and script as the question, clear and natural like ChatGPT or Gemini, 1 to 5 short sentences."
                safeReply(Providers.fallback(sysL, emptyList(), q + "\n\nSearch results:\n" + brave, lockW))?.let { return fin(sW, it) }
            }
            return fin(sW, Reply("Search failed (" + Models.provName(lockW) + "). Try again.", "Search failed. Try again."), false)
        }
        val ws = Models.sel(prefs, "web")
        if (ws != null && ws.prov != "gemini") {
            val noTools = " Today's date is " + todayStr() + ". You have no tools or internet access here: never write <tool_call> or any tags. Answer in the same language and script as the question, clear and natural like ChatGPT or Gemini, 2 to 6 short sentences."
            val t = if (brave != null)
                Providers.chatWith(ws.prov, ws.id, "Answer the question using ONLY these search results." + noTools, emptyList(), q + "\n\nSearch results:\n" + brave)
            else
                Providers.chatWith(ws.prov, ws.id, "Answer the question. If you are not sure about live facts (market, scores, news), say so briefly." + noTools, emptyList(), q)
            safeReply(t)?.let { return fin(sW, it) }
        }
        val raw = WebSearch.ask(key, q, if (ws != null && ws.prov == "gemini") listOf(ws.id) else emptyList(), brave)
        safeReply(raw)?.let { return fin(sW, it) }
        if (brave != null && lockW != "gemini") {
            val sys = "Answer the question using ONLY these search results. Today's date is " + todayStr() + ". You have no tools: never write <tool_call> or any tags. Answer in the same language and script as the question, clear and natural like ChatGPT or Gemini, 1 to 5 short sentences."
            safeReply(Providers.fallback(sys, emptyList(), q + "\n\nSearch results:\n" + brave))?.let { return fin(sW, it) }
        }
        val why = WebSearch.lastErr.ifBlank { "unknown" }
        return fin(sW, Reply("Search failed ($why). Try again.", "Search failed. Try again."), false)
    }

    // bolne ke liye saaf text: markdown nishaan, emoji aur link hatao
    private fun speechClean(x: String): String {
        var s = x
        s = s.replace(Regex("https?://\\S+"), "")
        s = s.replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")
        s = s.replace(Regex("(?m)^\\s*([-*\u2022])\\s+"), "")
        s = s.replace(Regex("(?m)^\\s*(\\d+)[.)]\\s+"), "$1, ")
        s = s.replace("*", "").replace("`", "").replace("_", " ")
        s = s.replace(Regex("[\\uD83C-\\uD83E][\\uDC00-\\uDFFF]|[\\u2600-\\u27BF]|\\uFE0F"), "")
        s = s.replace(Regex("\\s*\\n+\\s*"), ". ").replace(Regex("\\.\\s*\\.+"), ".").replace(Regex("\\s{2,}"), " ")
        return s.trim()
    }

    private fun parseReply(raw: String): Reply {
        if (raw.isBlank()) return Reply("Ho gaya.", "हो गया।")
        if (!Regex("(?m)^\\s*\\**[SV]\\**\\s*:").containsMatchIn(raw)) {
            val body = stripFiller(raw.replace(Regex("\n{3,}"), "\n\n").trim())
            return Reply(body, speechClean(body))
        }
        val t = raw.replace("*", "").trim()
        if (t.isEmpty()) return Reply("Ho gaya.", "हो गया।")
        var s = ""
        var v = ""
        var cur = 0
        val tag = Regex("^([SV])\\s*:\\s*(.*)$")
        for (line in t.lines()) {
            val l = line.trim()
            if (l.isEmpty()) { if (cur == 1 && !s.endsWith("\n")) s += "\n"; continue }
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
                s = (s + "\n" + l).trim()
            }
        }
        s = s.replace(Regex("\n{3,}"), "\n\n").trim()
        if (s.isEmpty() && v.isEmpty()) return Reply(t, t)
        if (s.isEmpty()) s = v
        if (v.isEmpty()) v = s
                return Reply(stripFiller(s), stripFiller(v))
    }

    private fun apiError(code: Int, body: String): Reply {
        return when {
                        code == 0 ->
                Reply("No response. The network or the model is slow. Try again.", "No response. The network or the model is slow. Try again.")
            code == 400 && body.contains("API key", ignoreCase = true) ->
                Reply("Invalid API key. Update the key in your GitHub secrets and rebuild the app.", "Invalid API key. Update the key in your GitHub secrets and rebuild the app.")
            code == 400 ->
                Reply("Bad request.", "Bad request.")
            code == 403 ->
                Reply("This key doesn't have permission. Create a new key in AI Studio.", "This key doesn't have permission. Create a new key in AI Studio.")
            code == 429 ->
                Reply("Free limit reached or too many requests. Try again later, or pick another model from the top menu.", "Free limit reached or too many requests. Try again later, or pick another model from the top menu.")
            code == 503 ->
                Reply("The model is busy right now. Retry, or pick another model from the top menu.", "The model is busy right now. Retry, or pick another model from the top menu.")
            else ->
                Reply("Error $code. Try again later.", "Error $code. Try again later.")
        }
    }

    @Volatile var answeredBy = ""
    @Volatile var lastQ = ""
    @Volatile var noWebOnce = false
    @Volatile var pendingSources: List<Triple<String, String, String>>? = null
    val sourcesFor = HashMap<String, List<Triple<String, String, String>>>()

    // Jawab dobara banao: web = true (internet se), false (bina internet), null (jaisa pehle)
    fun regen(web: Boolean?) {
        if (busy || lastQ.isBlank()) return
        val i = log.lastIndexOf("\nAI: ")
        if (i >= 0) log.setLength(i)
        val fi = full.lastIndexOf("\nAI: ")
        if (fi >= 0) full.setLength(fi)
        if (history.isNotEmpty()) history.removeAt(history.size - 1)
        val q = lastQ
        silent = true
        if (web == true) {
            val key = Keys.get(prefs)
            if (key.isEmpty()) return
            busy = true
            mode(2)
            pendingSources = null
            val g = ++curReq
            Act.start()
            thread {
                val r = webAnswer(key, q)
                postG(g) { deliver(r, q, true) }
            }
        } else {
            noWebOnce = (web == false)
            ask(q, null, false)
        }
    }

    // Purani baat-cheet wapas kholo (naya chat / branch)
    // chat mein dikhane ke liye bheji photo ka chhipa tag (sirf dikhane ke liye, model ko nahi jaata)
    @Volatile var imgTag: String = ""

    fun loadChat(text: String) {
        log.setLength(0)
        log.append(text)
        if (log.length > 8000) log.delete(0, log.length - 6000)
        full.setLength(0)
        full.append(text)
        history.clear()
        historySummary = ""
        val parts = text.split(Regex("\n(?=(?:Tum|AI): )")).map { it.trim() }.filter { it.isNotEmpty() }
        var q = ""
        for (p in parts) {
            if (p.startsWith("Tum:")) q = p.removePrefix("Tum:").replace(Regex("\u2060IMG:[^\u2060]*\u2060"), "").trim()
            else if (p.startsWith("AI:") && q.isNotEmpty()) {
                val a = p.removePrefix("AI:").trim().substringBeforeLast('\u2063')
                history.add(Pair(q, a))
                q = ""
            }
        }
        while (history.size > 12) condenseAndDrop(history.removeAt(0))
    }

    private fun deliver(r0: Reply, q: String, save: Boolean) {
        // aakhri bachav: kisi bhi raaste se tool-call markup aaya to screen/awaaz tak na pahunche
        val r = if (hasToolMarkup(r0.text) || hasToolMarkup(r0.voice)) {
            val t = stripToolMarkup(r0.text)
            val v = stripToolMarkup(r0.voice)
            if (t.isNotBlank()) Reply(t, if (v.isNotBlank()) v else speechClean(t))
            else Reply("Couldn't generate a response. Ask again.", "Couldn't generate a response. Ask again.")
        } else r0
        Act.end()
        val who = answeredBy
        answeredBy = ""
        pendingSources?.let { sourcesFor[r.text] = it }
        pendingSources = null
        noWebOnce = false
        chat("\nAI: ${r.text}" + (if (who.isNotEmpty()) "\u2063$who" else "") + "\n")
        voiceFor[r.text] = r.voice
        
        if (save) {
            history.add(Pair(q, r.text))
            while (history.size > 12) condenseAndDrop(history.removeAt(0))
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

    private fun ask(q: String, att: Pair<String, String>? = null, echo: Boolean = true) {
        val key = Keys.get(prefs)
        if (key.isEmpty()) {
            mode(0)
            status("Add your API key in Settings first")
            if (live()) scheduleListen(3000)
            return
        }
        busy = true
        mode(2)
        status("Thinking...")
        val g = ++curReq
        Act.start()
        lastQ = q
        pendingSources = null
        if (echo) { chat("\nTum: " + imgTag + q + "\n"); imgTag = "" }
        thread {
            try {
                // "update my app" / "build the app" jaisa seedha command: model ka intezaar kiye bina
                if (att == null && Regex("(?i)^\\s*(please\\s+)?(update|build|rebuild)\\s+(my\\s+|the\\s+)?(app|apk|assistant)\\s*[.!]*\\s*$|app\\s+(update|build)\\s+(karo|kar do|kardo)|\u0910\u092a\\s*\u0905\u092a\u0921\u0947\u091f").containsMatchIn(q)) {
                    val rU = updateApp()
                    postG(g) { deliver(rU, q, true) }
                    return@thread
                }
                val codeQ = Regex("(?i)\\b(code|coding|program|script|function|bug|error|kotlin|java|python|html|css|javascript|sql|api|app banao|website banao)\\b|कोड|प्रोग्राम").containsMatchIn(q)
                // live sawaal + Brave ki key ho to pehle Brave search (model ka intezaar nahi), jawab wahi se
                // phone ke kaam (message, call, gmail, alarm, app kholna...) internet mein nahi jaate
                val phoneWork = Regex("(?i)\\b(message|msg|whatsapp|sms|call|gmail|mail|email|alarm|reminder|timer|open|close|play|send|youtube|contact|set)\\b").containsMatchIn(q)
                // jo sawaal log Google par poochte hain (kya, kaun, kab, kitna, kaise, kahan, how, what...) wo bhi Brave se
                // likhna, code, banana, translate jaise kaam model ke paas hi rahenge
                val infoQ = Regex("(?i)\\b(kya|kaun|kab|kahan|kitna|kitne|kaise|kyon|kyu|kis|kisne|batao|bataiye|what|who|when|where|how|why|which|price|meaning|definition|mtlb|matlab)\\b|\\?\\s*$|[\u0915\u0916\u0917\u0918\u0919]\u094D?\u093F|\u0915\u094D\u092F\u093E|\u0915\u094C\u0928|\u0915\u092C|\u0915\u0939\u093E\u0901|\u0915\u093F\u0924\u0928\u093E|\u0915\u0948\u0938\u0947|\u0915\u094D\u092F\u094B\u0902").containsMatchIn(q)
                val makeQ = Regex("(?i)\\b(likho|likh|kahani|poem|poetry|banao|bana|translate|anuvad|code|lyrics|gana|shayari|email likh|letter)\\b").containsMatchIn(q) || codeQ
                val newsQ = Regex("(?i)\\bnews\\b|headlines?\\b|\u0916\u092c\u0930|\u0938\u092e\u093E\u091A\u093E\u0930").containsMatchIn(q)
                val webQ = !newsQ && (Providers.isLive(q) || (infoQ && !makeQ))
                if (att == null && Extra.brave.isNotEmpty() && !phoneWork && webQ) {
                    val rLive = webAnswer(key, q)
                    postG(g) { deliver(rLive, q, true) }
                    return@thread
                }
                val lock = Models.lock(prefs)
                val pick00 = (if (codeQ) Models.sel(prefs, "coding") else null) ?: Models.sel(prefs, "chat")
                val pick0 = pick00?.takeIf { lock.isEmpty() || it.prov == lock }
                val pick = if (pick0 != null && Usage.paidOnly(pick0)) null else pick0
                // left menu se Groq / OpenRouter chuna ho: Auto bhi sirf usi ke models se (photo/file ho to Gemini)
                if (lock.isNotEmpty() && lock != "gemini" && att == null) {
                    var txtL: String? = null
                    val stL = Act.begin(3, "Asking " + Models.provName(lock) + " models", "Only " + Models.provName(lock) + " is allowed (chosen in Models)\n\nPrompt:\n" + q.take(400))
                    if (pick != null) txtL = Providers.chatWith(pick.prov, pick.id, systemNoTools(), history.takeLast(6), q)?.also { answeredBy = pick.name }
                    if (txtL == null) txtL = Providers.fallback(systemNoTools(), history.takeLast(6), q, lock)?.also { answeredBy = Models.list(prefs).firstOrNull { m -> m.id == Providers.lastModel }?.name ?: Providers.lastModel }
                    if (txtL != null) Act.ok(stL, "Answered by " + answeredBy, txtL.take(700)) else Act.fail(stL, "No answer", "")
                    if (txtL != null) {
                        val repL = toolFix(txtL, q) ?: parseReply(txtL)
                        postG(g) { deliver(repL, q, true) }
                    } else {
                        postG(g) {
                            mode(0)
                            beep(2)
                            deliver(Reply(Models.provName(lock) + " didn't respond. Try again later or choose another model in Models.", Models.provName(lock) + " didn't respond. Try again later or choose another model in Models."), q, false)
                        }
                    }
                    return@thread
                }
                var okModel = ""
                fun nameOf(id: String) = Models.list(prefs).firstOrNull { it.id == id }?.name ?: id
                if (pick != null && pick.prov != "gemini" && att == null) {
                    val stP = Act.begin(3, "Asking " + pick.name, "Provider: " + pick.prov + "\nModel: " + pick.id + "\n\nPrompt:\n" + q.take(400))
                    val txt = Providers.chatWith(pick.prov, pick.id, systemNoTools(), history.takeLast(6), q)
                    if (txt != null) Act.ok(stP, "Answered", txt.take(700)) else Act.fail(stP, "No answer, trying the next model", "")
                    if (txt != null) {
                        answeredBy = pick.name
                        val repP = toolFix(txt, q) ?: parseReply(txt)
                        postG(g) { deliver(repP, q, true) }
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
                noWebOnce = false
                var code = 0
                var resp = ""
                val t0 = SystemClock.uptimeMillis()
                for (m in order) {
                    if (SystemClock.uptimeMillis() - t0 > 40000) break
                    val stM = Act.begin(3, "Asking " + nameOf(m), "POST generateContent\nModel: " + m + (if (att != null) "\nAttached: " + att.first else "") + "\n\nPrompt:\n" + q.take(400))
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
                        if (code == 200) Act.ok(stM, "Answered", resp.take(700)) else Act.fail(stM, "HTTP " + code + (if (code == 503 || code == 429) " (busy), trying the next model" else ""), resp.take(500))
                        if (code == 200 || code == 401 || code == 403 || (code == 400 && resp.contains("API key", ignoreCase = true))) break
                    } catch (e: Exception) {
                        code = 0
                        resp = ""
                        Act.fail(stM, "No connection or timed out", e.message ?: "")
                    }
                }
                if (code == 200) answeredBy = nameOf(okModel)
                if (code != 200) {
                    // photo ke saath sawal ho to dusra (photo na dekh sakne wala) model jawab na de
                    val stF = if (lock == "gemini" || att != null) null else Act.begin(3, "Trying backup models", "Groq and OpenRouter free models\n\nPrompt:\n" + q.take(400))
                    val fb = if (lock == "gemini" || att != null) null else Providers.fallback(systemNoTools(), history.takeLast(6), q)
                    if (stF != null) { if (fb != null) Act.ok(stF, "Answered by " + Providers.lastModel, fb.take(700)) else Act.fail(stF, "No backup model answered", "") }
                    if (fb != null) {
                        answeredBy = nameOf(Providers.lastModel)
                        val repF = toolFix(fb, q) ?: parseReply(fb)
                        postG(g) { deliver(repF, q, true) }
                        return@thread
                    }
                    val er = apiError(code, resp)
                    postG(g) {
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
                        val cityW = c.optJSONObject("args")?.optString("city", "") ?: ""
                        val stW = Act.begin(1, "Getting weather" + (if (cityW.isNotBlank()) " for $cityW" else ""), "get_weather(city=\"$cityW\")")
                        pre[idx] = Weather.today(cityW)
                        Act.ok(stW, pre[idx]!!.text.take(90), pre[idx]!!.text)
                    } else if (c.getString("name") == "web_search") {
                        val qq = c.optJSONObject("args")?.optString("query", "") ?: ""
                        pre[idx] = webAnswer(key, qq)
               
                    } else if (c.getString("name") == "get_news") {
                        val topic = c.optJSONObject("args")?.optString("topic", "") ?: ""
                        val stN = Act.begin(2, if (topic.isBlank()) "Getting news" else "Getting news: $topic", "get_news(topic=\"$topic\")")
                        val lang = if (prefs.getString("lang", "") == "Hindi") "hi" else "en"
                        val r = News.headlines(topic, lang)
                        Act.ok(stN, r.take(120), r)
                        pre[idx] = Reply(r, "News mil gayi")
                    } else if (c.getString("name") == "health_check") {
                        val stH = Act.begin(2, "Running health check", "health_check()")
                        val r = Health.check(prefs)
                        Act.ok(stH, r.take(150), r)
                        pre[idx] = Reply(r, "Health check ho gaya")
                    } else if (c.getString("name") == "check_usage") {
                        val stU = Act.begin(2, "Checking usage", "check_usage()")
                        val r = Usage.textSummary(prefs)
                        Act.ok(stU, r.take(150), r)
                        pre[idx] = Reply(r, "Usage check ho gaya")
                    } else if (c.getString("name") == "lab_run_command") {
                        val cmdL = c.optJSONObject("args")?.optString("cmd", "") ?: ""
                        val stL = Act.begin(2, "Lab: $cmdL", "lab_run_command(cmd=\"$cmdL\")")
                        val r = Lab.runCommand(prefs, cmdL)
                        Act.ok(stL, r.take(150), r)
                        pre[idx] = Reply(r, "Lab command chal gaya")
                    } else if (c.getString("name") == "lab_check_web") {
                        val urlL = c.optJSONObject("args")?.optString("url", "") ?: ""
                        val stL = Act.begin(2, "Checking $urlL", "lab_check_web(url=\"$urlL\")")
                        val r = Lab.checkWeb(prefs, urlL)
                        Act.ok(stL, r.take(150), r)
                        pre[idx] = Reply(r, "Check ho gaya")
                    } else if (c.getString("name") == "lab_scan_local") {
                        val stL = Act.begin(2, "Scanning localhost", "lab_scan_local()")
                        val r = Lab.scanLocal(prefs)
                        Act.ok(stL, r.take(150), r)
                        pre[idx] = Reply(r, "Scan ho gaya")
                    } else if (c.getString("name") == "repo_check_connection") {
                        val stC = Act.begin(2, "Checking GitHub connection", "repo_check_connection()")
                        val r = GitHub.checkConnection(prefs)
                        if (r.ok) Act.ok(stC, r.text, r.text) else Act.fail(stC, r.text, r.text)
                        pre[idx] = Reply(r.text, r.text)
                    } else if (c.getString("name") == "repo_list_files") {


                        val stG = Act.begin(2, "Listing repo files", "repo_list_files()")
                        val r = GitHub.listFiles(prefs)
                        if (r.ok) Act.ok(stG, r.text.lines().size.toString() + " files", r.text.take(1500)) else Act.fail(stG, r.text, r.text)
                        pre[idx] = Reply(r.text, if (r.ok) "File list mil gayi" else r.text)
                    } else if (c.getString("name") == "repo_search_code") {
                        val qq = c.optJSONObject("args")?.optString("query", "") ?: ""
                        val stG = Act.begin(2, "Searching code: $qq", "repo_search_code(query=\"$qq\")")
                        val r = GitHub.searchCode(prefs, qq)
                        if (r.ok) Act.ok(stG, r.text.take(120), r.text) else Act.fail(stG, r.text, r.text)
                        pre[idx] = Reply(r.text, if (r.ok) "Search ho gaya" else r.text)
                    } else if (c.getString("name") == "repo_read_file") {
                        val pathR = c.optJSONObject("args")?.optString("path", "") ?: ""
                        val stG = Act.begin(2, "Reading $pathR", "repo_read_file(path=\"$pathR\")")
                        val r = GitHub.readFile(prefs, pathR)
                        if (r.ok) Act.ok(stG, r.text.lines().size.toString() + " lines", r.text.take(1500)) else Act.fail(stG, r.text, r.text)
                        pre[idx] = if (r.ok) Reply(r.text, "File padh li") else Reply(r.text, r.text)
                    } else if (c.getString("name") == "repo_write_file") {
                        val args = c.optJSONObject("args") ?: JSONObject()
                        pre[idx] = autoPush(args.optString("path", ""), args.optString("content", ""), args.optString("message", ""))
                    }
                }
                postG(g) {
                    val results = calls.mapIndexed { idx, c ->
                        pre[idx] ?: run {
                            val nm = c.getString("name")
                            val ag = c.optJSONObject("args") ?: JSONObject()
                            val stA = Act.begin(2, "Phone action: $nm", nm + "(" + ag.toString() + ")")
                            val rr = runAction(nm, ag)
                            Act.ok(stA, rr.text.take(90), rr.text)
                            rr
                        }
                    }
                    val r = if (results.isNotEmpty()) {
                        Reply(results.joinToString(". ") { it.text }, results.joinToString(". ") { it.voice })
                    } else {
                        parseReply(texts.toString())
                    }
                    deliver(r, q, true)
                }
            } catch (e: Exception) {
                postG(g) {
                    busy = false
                    Act.end()
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

    // ---------- "Update my app": build chalao -> har 10 sec status -> green ho to download + install, red ho to error batao ----------
    private val updating = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun tell(text: String, voice: String = text) {
        main.post { chat("\nAI: $text\n"); speak(voice) }
    }

    private fun updateApp(): Reply {
        if (GitHub.token(prefs).isEmpty()) return Reply("Add your GitHub token in Settings first.", "Add your GitHub token in Settings first.")
        if (!updating.compareAndSet(false, true)) return Reply("An update is already running.", "An update is already running.")
        thread {
            try {
                val id = ApkUpdate.triggerGithubApkPatch(prefs)
                if (id <= 0L) { tell("Couldn't start the build. Check the GitHub token permissions (Actions: Read and write).", "Couldn't start the build."); return@thread }
                val end = System.currentTimeMillis() + 15 * 60_000
                var state = "running"
                while (System.currentTimeMillis() < end) {
                    try { Thread.sleep(10_000) } catch (e: Exception) { }
                    val (st, concl) = ApkUpdate.checkPatchStatus(prefs, id)
                    if (st == "completed") { state = if (concl == "success") "success" else "failure"; break }
                }
                when (state) {
                    "success" -> {
                        tell("Build successful, installing new update.")
                        val err = ApkUpdate.downloadAndInstallApk(app, prefs, id)
                        if (err != null) tell("Build was fine but the download failed: $err", "Download failed.")
                    }
                    "failure" -> {
                        tell("Build failed, checking logs.")
                        val log = GitHub.failureLog(prefs, id).lines().filter { it.isNotBlank() }.takeLast(6).joinToString("\n").take(600)
                        tell(if (log.isBlank()) "I couldn't read the error log. Open the run on GitHub Actions." else "Error:\n$log", "I've put the error in the chat.")
                    }
                    else -> tell("The build is taking too long. Check GitHub Actions.", "The build is taking too long.")
                }
            } catch (e: Exception) {
                tell("Update failed: " + (e.message ?: "error"), "Update failed.")
            } finally {
                updating.set(false)
            }
        }
        return Reply("Build started. I'll tell you when it's done.", "Build started. I'll tell you when it's done.")
    }

    // Repo mein file save karo, uska GitHub Actions build poora hone tak dekhte raho; fail ho to
    // error padh kar khud fix karke phir se bhejo (jab tak green na ho ya koshishein khatam ho jaayein)
    private fun autoPush(path: String, content: String, message: String, attempt: Int = 1): Reply {
        val stS = Act.begin(2, "Saving \"$path\" to GitHub" + (if (attempt > 1) " (fix attempt $attempt)" else ""), path)
        val w = GitHub.writeFile(prefs, path, content, message)
        if (!w.ok) { Act.fail(stS, w.text); return Reply(w.text, w.text) }
        Act.ok(stS, "Saved on \"assistant-edits\"")
        val stB = Act.begin(2, "Checking the build", "commit " + w.sha.take(7))
        val b = GitHub.waitForRun(prefs, w.sha)
        when (b.state) {
            "success" -> { Act.ok(stB, "Build is green"); return Reply("\"$path\" badal diya, GitHub par \"assistant-edits\" branch mein save hai aur build theek se ban gaya. Jab chaho merge kar dena.", "File badal di, sab theek ban gaya") }
            "timeout", "none" -> { Act.ok(stB, "Could not confirm in time"); return Reply("\"$path\" \"assistant-edits\" branch par save kar diya. Build abhi bhi chal raha hoga, GitHub par thodi der mein check kar lena.", "File save kar di") }
            else -> {
                Act.update(stB, "Build failed, reading the error")
                val log = GitHub.failureLog(prefs, b.runId)
                if (attempt >= 3 || log.isBlank()) {
                    Act.fail(stB, "Could not auto-fix")
                    return Reply("\"$path\" save to ho gaya, par build fail ho raha hai aur main khud thik nahi kar paya. GitHub Actions ka log dekh lena ($attempt koshish ki).", "Build fail ho raha hai, khud dekhna padega")
                }
                Act.update(stB, "Fixing the build error")
                val fixPrompt = "This Kotlin Android file (\"$path\") failed to compile on GitHub Actions. Fix the error and output ONLY the complete corrected file content, nothing else (no markdown fences, no explanation).\n\nBuild error:\n" + log.take(3000) + "\n\nCurrent file content:\n" + content
                val fixed = Providers.fallback("You are an expert Kotlin/Android developer. Output only the corrected full file, nothing else.", emptyList(), fixPrompt)
                    ?.trim()?.removePrefix("```kotlin")?.removePrefix("```")?.removeSuffix("```")?.trim()
                if (fixed.isNullOrBlank()) {
                    Act.fail(stB, "Could not auto-fix")
                    return Reply("\"$path\" save to ho gaya par build fail ho raha hai, aur fix nahi mil paya. GitHub Actions ka log dekh lena.", "Build fail ho raha hai, khud dekhna padega")
                }
                Act.ok(stB, "Got a fix, pushing again")
                return autoPush(path, fixed, "Auto-fix build error (attempt $attempt)", attempt + 1)
            }
        }
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
                "set_incognito" -> setIncognito(a.optBoolean("on", true))
                "update_app" -> updateApp()
                "stop_assistant" -> {
                    main.postDelayed({ onQuit?.invoke() }, 3000)
                    Reply("Band ho raha hoon, phir milte hain", "बंद हो रहा हूँ, फिर मिलते हैं")
                }
                else -> Reply("That action isn't supported.", "That action isn't supported.")
            }
        } catch (e: Exception) {
            Reply("Couldn't complete that: ${e.message}", "Couldn't complete that.")
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
                    thread { Security.checkNew(app, pkg)?.let { Security.notify(app, it); Security.addLog(prefs, listOf(it), setOf(pkg)); Security.addPending(prefs, it) } }
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
        if (l.isEmpty()) return Reply("No apps found", "No apps found")
        val names = l.joinToString(", ") { it.first }
        return Reply("Phone mein ${l.size} app hain: $names", "फ़ोन में ${l.size} ऐप हैं: $names")
    }

    private fun openApp(name: String): Reply {
        val pm = app.packageManager
        val m = findApp(name) ?: return Reply("No app named $name found", "No app named $name found")
        val li = pm.getLaunchIntentForPackage(m.activityInfo.packageName)
            ?: return Reply("Couldn't open $name", "Couldn't open $name")
        go(li)
        val label = m.loadLabel(pm).toString()
        return Reply("$label khol raha hoon", "$label खोल रहा हूँ")
    }

    private fun closeApp(name: String): Reply {
        val pm = app.packageManager
        val m = findApp(name) ?: return Reply("No app named $name found", "No app named $name found")
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
        "Couldn't find a number for $who. Check Contacts permission or say the number.",
        "Couldn't find a number for $who. Check Contacts permission or say the number.")

    private fun callContact(who: String): Reply {
        if (app.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return Reply("Call permission is not granted. Allow it in Settings.", "Call permission is not granted. Allow it in Settings.")
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
        } ?: return Reply("No flash found on this device", "No flash found on this device")
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
