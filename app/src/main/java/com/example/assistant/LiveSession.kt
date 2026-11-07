package com.example.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.sqrt

// Gemini Live: ek hi WebSocket par mic ki awaaz seedhi bhejte hain aur AI ki awaaz seedhi bajate hain.
class LiveSession(private val ctx: Context, private val key: String, private val cb: Cb) {

    interface Cb {
        fun status(s: String)
        fun mode(m: Int)
        fun level(v: Float)
        fun chat(s: String)
        fun system(): String
        fun tools(): JSONArray
        fun tool(name: String, args: JSONObject): String
        fun failed(reason: String)
    }

    private val MODELS = listOf("gemini-3.8-live", "gemini-3.1-flash-live-preview", "gemini-2.5-flash-native-audio-preview-12-2025")
    private val URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="

    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS).build()
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile var ready = false; private set
    @Volatile var muted = false
    @Volatile private var active = false
    @Volatile private var ws: WebSocket? = null
    @Volatile private var attempt = 0
    @Volatile private var everReady = false
    @Volatile private var resumeHandle: String? = null
    @Volatile private var gen = 0

    private var rec: AudioRecord? = null
    private var track: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private val q = LinkedBlockingQueue<ByteArray>()
    private val trackLock = Any()
    @Volatile private var speaking = false
    @Volatile private var lastWrite = 0L
    @Volatile private var outLevel = 0f
    private var savedMode = AudioManager.MODE_NORMAL
    private var savedSpeaker = false

    private val inText = StringBuilder()
    private val outText = StringBuilder()

    // ---------- shuru / band ----------
    fun start() {
        if (active) return
        active = true
        try {
            savedMode = am.mode
            savedSpeaker = am.isSpeakerphoneOn
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
        } catch (e: Exception) { }
        cb.status("Gemini Live se jud raha hoon...")
        initTrack()
        thread(name = "live-play") { playLoop() }
        connect()
    }

    fun stop() {
        active = false
        ready = false
        gen++
        try { ws?.close(1000, "bye") } catch (e: Exception) { }
        ws = null
        stopMic()
        q.clear()
        synchronized(trackLock) {
            try { track?.pause(); track?.flush(); track?.release() } catch (e: Exception) { }
            track = null
        }
        try {
            am.mode = savedMode
            am.isSpeakerphoneOn = savedSpeaker
        } catch (e: Exception) { }
        try { http.dispatcher.executorService.shutdown() } catch (e: Exception) { }
    }

    fun sendText(t: String) {
        val w = ws ?: return
        if (!ready) return
        interruptPlayback()
        flushTranscripts()
        cb.chat("\nTum: $t\n")
        w.send(JSONObject().put("realtimeInput", JSONObject().put("text", t)).toString())
        cb.mode(2)
    }

    // ---------- connection ----------
    private fun setupJson(modelIdx: Int, full: Boolean): String {
        val setup = JSONObject()
        setup.put("model", "models/" + MODELS[modelIdx])
        setup.put("generationConfig", JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                .put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Puck")))))
        setup.put("systemInstruction", JSONObject().put("parts",
            JSONArray().put(JSONObject().put("text", cb.system()))))
        // function declarations (Live API camelCase maangta hai)
        val decls = cb.tools().optJSONObject(0)?.optJSONArray("function_declarations")
        if (decls != null) {
            setup.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decls)))
        }
        setup.put("inputAudioTranscription", JSONObject())
        setup.put("outputAudioTranscription", JSONObject())
        if (full) {
            setup.put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            val sr = JSONObject()
            resumeHandle?.let { sr.put("handle", it) }
            setup.put("sessionResumption", sr)
        }
        return JSONObject().put("setup", setup).toString()
    }

    private fun connect() {
        if (!active) return
        val myGen = ++gen
        val modelIdx = (attempt / 2).coerceAtMost(MODELS.size - 1)
        val full = attempt % 2 == 0
        ready = false
        val req = Request.Builder().url(URL + key).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (myGen != gen) return
                webSocket.send(setupJson(modelIdx, full))
            }

            override fun onMessage(webSocket: WebSocket, text: String) { if (myGen == gen) handle(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { if (myGen == gen) handle(bytes.utf8()) }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (myGen == gen) lost("band ($code) $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (myGen == gen) lost((response?.code?.toString() ?: "") + " " + (t.message ?: ""))
            }
        })
    }

    private fun lost(why: String) {
        if (!active) return
        ready = false
        stopMic()
        if (everReady) {
            // beech mein toot gaya: wahi model dobara try karo
            everReady = false
            if (attempt < 100) {
                cb.status("Dobara jud raha hoon...")
                thread { Thread.sleep(600); connect() }
                return
            }
        }
        attempt++
        if (attempt >= MODELS.size * 2) {
            active = false
            cb.failed(why.trim())
            return
        }
        thread { Thread.sleep(300); connect() }
    }

    // ---------- server ka message ----------
    private fun handle(raw: String) {
        val j = try { JSONObject(raw) } catch (e: Exception) { return }
        if (j.has("setupComplete")) {
            ready = true
            everReady = true
            cb.status("Bolo, main sun raha hoon...")
            cb.mode(1)
            startMic()
            return
        }
        j.optJSONObject("sessionResumptionUpdate")?.let {
            if (it.optBoolean("resumable", false)) {
                val h = it.optString("newHandle", "")
                if (h.isNotEmpty()) resumeHandle = h
            }
        }
        if (j.has("goAway")) {
            // server session band karne wala hai: naya jod lo
            try { ws?.close(1000, "refresh") } catch (e: Exception) { }
            return
        }
        j.optJSONObject("toolCall")?.let { tc ->
            val calls = tc.optJSONArray("functionCalls") ?: return@let
            thread {
                val resp = JSONArray()
                for (i in 0 until calls.length()) {
                    val c = calls.getJSONObject(i)
                    val out = try { cb.tool(c.getString("name"), c.optJSONObject("args") ?: JSONObject()) }
                    catch (e: Exception) { "Nahi ho paaya" }
                    resp.put(JSONObject().put("id", c.optString("id"))
                        .put("name", c.getString("name"))
                        .put("response", JSONObject().put("result", out)))
                }
                ws?.send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", resp)).toString())
            }
        }
        val sc = j.optJSONObject("serverContent") ?: return
        if (sc.optBoolean("interrupted", false)) {
            interruptPlayback()
            flushTranscripts()
        }
        sc.optJSONObject("inputTranscription")?.optString("text")?.let { inText.append(it) }
        sc.optJSONObject("outputTranscription")?.optString("text")?.let {
            if (inText.isNotEmpty()) flushIn()
            outText.append(it)
        }
        val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val d = parts.getJSONObject(i).optJSONObject("inlineData") ?: continue
                val b = Base64.decode(d.optString("data"), Base64.DEFAULT)
                if (b.isNotEmpty()) {
                    if (inText.isNotEmpty()) flushIn()
                    q.offer(b)
                }
            }
        }
        if (sc.optBoolean("turnComplete", false)) flushTranscripts()
    }

    private fun flushIn() {
        val t = inText.toString().trim()
        inText.setLength(0)
        if (t.isNotEmpty()) cb.chat("\nTum: $t\n")
    }

    private fun flushTranscripts() {
        flushIn()
        val o = outText.toString().trim()
        outText.setLength(0)
        if (o.isNotEmpty()) cb.chat("\nAI: $o\n")
    }

    // ---------- mic ----------
    private fun startMic() {
        if (rec != null) return
        thread(name = "live-mic") {
            val sr = 16000
            val min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val frame = 1280
            val r = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sr,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frame * 4))
            } catch (e: Exception) { null }
            if (r == null || r.state != AudioRecord.STATE_INITIALIZED) {
                cb.status("Mic chalu nahi ho paaya")
                try { r?.release() } catch (e: Exception) { }
                return@thread
            }
            try { if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(r.audioSessionId)?.also { it.enabled = true } } catch (e: Exception) { }
            try { if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(r.audioSessionId)?.also { it.enabled = true } } catch (e: Exception) { }
            rec = r
            try { r.startRecording() } catch (e: Exception) { rec = null; return@thread }
            val buf = ByteArray(frame)
            var smooth = 0f
            while (active && rec === r) {
                var n = 0
                while (n < frame && active && rec === r) {
                    val k = r.read(buf, n, frame - n)
                    if (k <= 0) break
                    n += k
                }
                if (n < frame) break
                if (muted || !ready) { cb.level(0f); continue }
                var sum = 0.0
                var i = 0
                while (i < frame) {
                    val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort().toInt()
                    sum += s.toDouble() * s
                    i += 2
                }
                val rms = sqrt(sum / (frame / 2)).toFloat() / 32768f
                val micLvl = (rms * 9f).coerceIn(0f, 1f)
                smooth = smooth * 0.6f + (if (speaking) outLevel else micLvl) * 0.4f
                cb.level(smooth)
                ws?.send(JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
                    .put("data", Base64.encodeToString(buf, 0, frame, Base64.NO_WRAP))
                    .put("mimeType", "audio/pcm;rate=16000"))).toString())
            }
            try { r.stop() } catch (e: Exception) { }
            try { r.release() } catch (e: Exception) { }
            if (rec === r) rec = null
        }
    }

    private fun stopMic() {
        val r = rec
        rec = null
        try { aec?.release() } catch (e: Exception) { }
        try { ns?.release() } catch (e: Exception) { }
        aec = null
        ns = null
        if (r != null) {
            try { r.stop() } catch (e: Exception) { }
        }
    }

    // ---------- AI ki awaaz ----------
    private fun initTrack() {
        val sr = 24000
        val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        synchronized(trackLock) {
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, sr / 2))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            track?.play()
        }
    }

    private fun interruptPlayback() {
        q.clear()
        synchronized(trackLock) {
            try { track?.pause(); track?.flush(); track?.play() } catch (e: Exception) { }
        }
        if (speaking) {
            speaking = false
            outLevel = 0f
            cb.mode(1)
        }
    }

    private fun playLoop() {
        while (active) {
            val b = try { q.poll(150, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { null }
            if (b == null) {
                if (speaking && System.currentTimeMillis() - lastWrite > 700) {
                    speaking = false
                    outLevel = 0f
                    cb.mode(1)
                }
                continue
            }
            if (!speaking) {
                speaking = true
                cb.mode(3)
            }
            var sum = 0.0
            var i = 0
            while (i + 1 < b.size) {
                val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xff)).toShort().toInt()
                sum += s.toDouble() * s
                i += 2
            }
            outLevel = (sqrt(sum / maxOf(1, b.size / 2)).toFloat() / 32768f * 6f).coerceIn(0f, 1f)
            try {
                synchronized(trackLock) { }
                track?.write(b, 0, b.size)
            } catch (e: Exception) { }
            lastWrite = System.currentTimeMillis() + (b.size / 2) * 1000L / 24000
        }
    }
}
