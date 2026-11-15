package com.example.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View

// Mic dabane par: bolte raho, Hindi text banta rahe. Beech mein chup rehne par bhi band nahi hota,
// jab tak stop / send na dabao. Sab main thread par chalta hai.
class Dictation(private val ctx: Context) : Dict {
    interface Cb {
        fun text(full: String)
        fun level(v: Float)
        fun ended(full: String)      // bahut der chup rehne par apne aap ruka
        fun failed(msg: String)
    }

    @Volatile var active = false
        private set

    private val main = Handler(Looper.getMainLooper())
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var rec: SpeechRecognizer? = null
    private var cb: Cb? = null
    private var base = ""
    private var committed = ""
    private var partial = ""
    private var misses = 0
    private var stopping: ((String) -> Unit)? = null

    private val beepStreams = intArrayOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_MUSIC)
    private val mutedByUs = BooleanArray(3)

    private fun muteBeeps() {
        for (i in beepStreams.indices) {
            try {
                if (!am.isStreamMute(beepStreams[i])) {
                    am.adjustStreamVolume(beepStreams[i], AudioManager.ADJUST_MUTE, 0)
                    mutedByUs[i] = true
                }
            } catch (e: Exception) { }
        }
    }

    private fun unmuteBeeps() {
        for (i in beepStreams.indices) {
            if (!mutedByUs[i]) continue
            try { am.adjustStreamVolume(beepStreams[i], AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) { }
            mutedByUs[i] = false
        }
    }

    private fun join(a: String, b: String): String = when {
        a.isBlank() -> b.trim()
        b.isBlank() -> a.trim()
        else -> a.trim() + " " + b.trim()
    }

    override fun full(): String = join(join(base, committed), partial)

    private fun emit() { cb?.text(full()) }

    override fun start(existing: String, callback: Cb): Boolean {
        if (active) return true
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            callback.failed("Speech recognition nahi mila")
            return false
        }
        cb = callback
        base = existing.trim()
        committed = ""
        partial = ""
        misses = 0
        stopping = null
        active = true
        muteBeeps()
        newRecognizer()
        begin()
        emit()
        return true
    }

    private fun newRecognizer() {
        try { rec?.destroy() } catch (e: Exception) { }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onRmsChanged(v: Float) { cb?.level(((v + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onPartialResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) { partial = t; emit() }
            }
            // Android 13+: ek hi sunne ka daur, beech ke ruk-ruk kar aane wale hisse yahin aate hain (mic baar baar band-chalu nahi hota)
            override fun onSegmentResults(b: Bundle) {
                val t = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) { committed = join(committed, t); misses = 0 }
                partial = ""
                emit()
            }
            override fun onEndOfSegmentedSession() {
                partial = ""
                emit()
                if (stopping != null) finish() else again(120, false)
            }
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) { committed = join(committed, t); misses = 0 }
                partial = ""
                emit()
                if (stopping != null) finish() else again(120, false)
            }
            override fun onError(e: Int) {
                if (stopping != null) { finish(); return }
                when (e) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        val d = full()
                        shutdown()
                        cb?.failed("Mic ki permission nahi hai")
                        if (d.isNotBlank()) cb?.ended(d)
                    }
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // chup raha: phir se sunna shuru, bahut der chup rahe to rukk jao
                        partial = ""
                        misses++
                        if (misses > 8) {
                            val d = full()
                            shutdown()
                            cb?.ended(d)
                        } else again(100, false)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> again(500, true)
                    else -> {
                        misses++
                        if (misses > 5) {
                            val d = full()
                            shutdown()
                            cb?.failed("Awaaz sun nahi paaya (error $e)")
                            if (d.isNotBlank()) cb?.ended(d)
                        } else again(400, true)
                    }
                }
            }
        })
        rec = r
    }

    private fun begin() {
        if (!active) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (android.os.Build.VERSION.SDK_INT >= 33) putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
        try {
            rec?.startListening(i)
        } catch (e: Exception) {
            again(600, true)
        }
    }

    private fun again(delay: Long, recreate: Boolean) {
        main.postDelayed({
            if (!active || stopping != null) return@postDelayed
            if (recreate) newRecognizer()
            begin()
        }, delay)
    }

    // stop dabaya: abhi tak ka text de do (aakhri shabd ke liye thoda ruk kar)
    override fun stop(done: (String) -> Unit) {
        if (!active) { done(full()); return }
        stopping = done
        try { rec?.stopListening() } catch (e: Exception) { }
        main.postDelayed({ if (stopping != null) finish() }, 900)
    }

    private fun finish() {
        val d = stopping
        stopping = null
        val t = full()
        shutdown()
        d?.invoke(t)
    }

    // X dabaya: sab chhod do
    override fun cancel() {
        stopping = null
        shutdown()
    }

    private fun shutdown() {
        active = false
        main.removeCallbacksAndMessages(null)
        try { rec?.cancel() } catch (e: Exception) { }
        try { rec?.destroy() } catch (e: Exception) { }
        rec = null
        unmuteBeeps()
    }
}

// Lambe text ke liye: oonchai ek had tak badhti hai, usse zyada hone par andar scroll hota hai
class MaxHeightScroll(ctx: Context, private val maxPx: Int) : android.widget.ScrollView(ctx) {
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxPx, View.MeasureSpec.AT_MOST))
    }
}

// Awaaz ki oonchai ke hisaab se chalti hui khadi lakeerein (jaisa Claude / ChatGPT mein)
class WaveView(ctx: Context) : View(ctx) {
    private val n = 44
    private val lv = FloatArray(n)
    private var last = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CFCFD3")
        strokeCap = Paint.Cap.ROUND
    }

    fun push(v: Float) {
        last = maxOf(v, last * 0.55f)
        System.arraycopy(lv, 1, lv, 0, n - 1)
        lv[n - 1] = last
        postInvalidateOnAnimation()
    }

    fun clear() {
        java.util.Arrays.fill(lv, 0f)
        last = 0f
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = w / n
        p.strokeWidth = gap * 0.34f
        for (i in 0 until n) {
            val a = 0.07f + 0.93f * lv[i]
            val bh = h * a
            val x = gap * (i + 0.5f)
            c.drawLine(x, h / 2f - bh / 2f, x, h / 2f + bh / 2f, p)
        }
    }
}
