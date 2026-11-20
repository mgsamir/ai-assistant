package com.example.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

// Likhwane ke do tareeke: phone ki speech service (Dictation) ya yeh (ek hi mic stream + Groq Whisper)
interface Dict {
    fun start(existing: String, callback: Dictation.Cb): Boolean
    fun stop(done: (String) -> Unit)
    fun cancel()
    fun full(): String
}

// Mic ek baar khulta hai aur stop/send tak khula rehta hai (upar ka mic nishaan jalta-bujhta nahi).
// Jab tum bolte ho to wo hissa Groq Whisper se Hindi text banta hai; chup rehne par kuch bheja nahi jaata.
class WhisperDictation(private val ctx: Context) : Dict {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
    private val pool = Executors.newSingleThreadExecutor()

    @Volatile var active = false
        private set
    private var cb: Dictation.Cb? = null
    private var base = ""
    private val texts = HashMap<Int, String>()
    private val nextSeq = AtomicInteger(0)
    private val pending = AtomicInteger(0)
    @Volatile private var speaking = false
    @Volatile private var gen = 0
    @Volatile private var stopReq = false
    private var failedOnce = false
    private var doneCb: ((String) -> Unit)? = null

    private companion object {
        const val RATE = 16000
        const val FRAME = 640          // 40 ms
        const val END_SIL = 22         // lagbhag 0.9 second chup = hissa poora
        const val MIN_VOICED = 10      // 0.4 second se chhota hissa bhejna nahi
        const val MAX_FRAMES = 25 * 25 // ek hissa zyada se zyada 25 second
    }

    private fun join(a: String, b: String): String = when {
        a.isBlank() -> b.trim()
        b.isBlank() -> a.trim()
        else -> a.trim() + " " + b.trim()
    }

    override fun full(): String {
        var r = base
        for (k in texts.keys.sorted()) texts[k]?.let { if (it.isNotBlank()) r = join(r, it) }
        return r.trim()
    }

    private fun emit() {
        val t = full()
        cb?.text(t)
    }

    override fun start(existing: String, callback: Dictation.Cb): Boolean {
        if (active) return true
        if (Extra.groq.isBlank()) { callback.failed("Groq key not found"); return false }
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) { callback.failed("Couldn't open the microphone"); return false }
        cb = callback
        base = existing.trim()
        texts.clear()
        nextSeq.set(0)
        pending.set(0)
        speaking = false
        stopReq = false
        failedOnce = false
        doneCb = null
        val g = ++gen
        active = true
        val t = Thread { capture(g, minBuf) }
        t.isDaemon = true
        t.start()
        emit()
        return true
    }

    private fun lvl(g: Int, rms: Float) {
        main.post { if (g == gen) cb?.level((rms * 14f).coerceIn(0f, 1f)) }
    }

    private fun toBytes(b: ShortArray, n: Int): ByteArray {
        val o = ByteArray(n * 2)
        for (i in 0 until n) {
            val v = b[i].toInt()
            o[2 * i] = (v and 0xff).toByte()
            o[2 * i + 1] = ((v shr 8) and 0xff).toByte()
        }
        return o
    }

    private fun wavOf(pcm: ByteArray): ByteArray {
        val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcm.size)
        return h.array() + pcm
    }

    @SuppressLint("MissingPermission")
    private fun capture(g: Int, minBuf: Int) {
        var rec: AudioRecord? = null
        try {
            rec = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, FRAME * 16))
            if (rec.state != AudioRecord.STATE_INITIALIZED) throw RuntimeException("Couldn't open the microphone")
            rec.startRecording()
        } catch (e: Exception) {
            try { rec?.release() } catch (x: Exception) { }
            main.post {
                if (g == gen) {
                    active = false
                    cb?.failed("Couldn't open the microphone")
                    cb?.ended(full())
                }
            }
            return
        }
        val buf = ShortArray(FRAME)
        val pre = java.util.ArrayDeque<ByteArray>()
        val seg = ByteArrayOutputStream()
        var inSpeech = false
        var voiced = 0
        var silent = 0
        var hot = 0
        var segFrames = 0
        var floor = 0.01f
        while (!stopReq && g == gen) {
            val n = rec.read(buf, 0, FRAME)
            if (n < 0) break
            if (n == 0) continue
            var sum = 0.0
            for (i in 0 until n) { val v = buf[i].toInt(); sum += (v * v).toDouble() }
            val rms = (sqrt(sum / n) / 32768.0).toFloat()
            lvl(g, rms)
            val voice = rms > maxOf(0.015f, floor * 3.2f)
            if (!voice) floor = floor * 0.95f + rms * 0.05f
            val bytes = toBytes(buf, n)
            if (!inSpeech) {
                pre.addLast(bytes)
                if (pre.size > 8) pre.removeFirst()
                if (voice) hot++ else hot = 0
                if (hot >= 2) {
                    inSpeech = true
                    speaking = true
                    seg.reset()
                    for (b in pre) seg.write(b)
                    pre.clear()
                    voiced = hot
                    silent = 0
                    segFrames = seg.size() / (FRAME * 2)
                    main.post { if (g == gen) emit() }
                }
            } else {
                seg.write(bytes)
                segFrames++
                if (voice) { voiced++; silent = 0 } else silent++
                if (silent >= END_SIL || segFrames >= MAX_FRAMES) {
                    send(g, seg.toByteArray(), voiced)
                    seg.reset()
                    inSpeech = false
                    speaking = false
                    hot = 0; voiced = 0; silent = 0; segFrames = 0
                    pre.clear()
                }
            }
        }
        if (inSpeech && g == gen && seg.size() > 0) { send(g, seg.toByteArray(), voiced); speaking = false }
        try { rec.stop() } catch (e: Exception) { }
        try { rec.release() } catch (e: Exception) { }
        if (g == gen) main.post { if (g == gen) waitDone(g, SystemClock.uptimeMillis()) }
    }

    private fun send(g: Int, pcm: ByteArray, voicedFrames: Int) {
        if (voicedFrames < MIN_VOICED) { main.post { if (g == gen) emit() }; return }
        val seq = nextSeq.getAndIncrement()
        pending.incrementAndGet()
        val wav = wavOf(pcm)
        pool.execute {
            var txt: String? = null
            var err = ""
            for (attempt in 0..1) {
                if (g != gen) break
                try { txt = transcribe(wav); break } catch (e: Exception) {
                    err = e.message ?: "error"
                    try { Thread.sleep(1500) } catch (x: Exception) { }
                }
            }
            val result = txt
            main.post {
                pending.decrementAndGet()
                if (g == gen) {
                    if (result != null) texts[seq] = clean(result, voicedFrames)
                    else if (!failedOnce) { failedOnce = true; cb?.failed("Couldn't transcribe: " + err.take(80)) }
                    emit()
                }
            }
        }
    }

    // Whisper chup / halki awaaz par kabhi "Thank you" jaisa kuch likh deta hai, use hata do
    private fun clean(t: String, voicedFrames: Int): String {
        val s = t.trim()
        if (voicedFrames < 40 && Regex("(?i)^(thank you|thanks for watching|धन्यवाद|शुक्रिया|subtitles|www\\.|bye)[.! ।]*$").containsMatchIn(s)) return ""
        return s
    }

    // bhasha apne aap pehchani jaati hai: English bolo to English (Latin), Hindi bolo to Devanagari
    private fun transcribe(wav: ByteArray): String {
        val first = transcribeOnce(wav, null)
        // kabhi Urdu lipi aa jaye to Hindi maan kar dobara
        if (first.any { it in '\u0600'..'\u06FF' }) return transcribeOnce(wav, "hi")
        return first
    }

    private fun transcribeOnce(wav: ByteArray, lang: String?): String {
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "a.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("prompt", "Hello, how are you? नमस्ते, आप कैसे हैं? Hi. कैसे हो? What are you doing? क्या कर रहे हो?")
        if (lang != null) mb.addFormDataPart("language", lang)
        val body = mb.build()
        val req = Request.Builder().url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer " + Extra.groq).post(body).build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string() ?: ""
            if (!r.isSuccessful) throw RuntimeException("HTTP " + r.code)
            return JSONObject(s).optString("text", "")
        }
    }

    private fun waitDone(g: Int, t0: Long) {
        if (g != gen) return
        if (pending.get() > 0 && SystemClock.uptimeMillis() - t0 < 8000L) {
            main.postDelayed({ waitDone(g, t0) }, 150)
            return
        }
        active = false
        val d = doneCb
        doneCb = null
        val t = full()
        if (d != null) d(t) else cb?.ended(t)
    }

    override fun stop(done: (String) -> Unit) {
        if (!active) { done(full()); return }
        doneCb = done
        stopReq = true
    }

    override fun cancel() {
        gen++
        stopReq = true
        active = false
        doneCb = null
        speaking = false
    }
}
