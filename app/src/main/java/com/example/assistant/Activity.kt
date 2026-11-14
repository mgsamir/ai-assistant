package com.example.assistant

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

// "AI abhi kya kar raha hai": har kaam ka ek step. Chat ke upar wali status line aur
// "Summary" sheet isi se bante hain; kisi step ko dabane par uski poori jaankari dikhti hai.
object Act {
    const val RUN = 0
    const val OK = 1
    const val FAIL = 2

    // kind: 0 sochna, 1 web, 2 phone ka kaam / tool, 3 model se baat, 4 photo-video-music
    class Step(val kind: Int, title0: String) {
        @Volatile var title = title0
        @Volatile var detail = ""
        @Volatile var req = ""
        @Volatile var res = ""
        @Volatile var state = RUN
        val t0 = SystemClock.uptimeMillis()
        @Volatile var t1 = 0L
        fun secs(): Float = ((if (t1 > 0) t1 else SystemClock.uptimeMillis()) - t0) / 1000f
    }

    private val ui = Handler(Looper.getMainLooper())

    @Volatile var steps: List<Step> = emptyList()
    @Volatile var running = false
    @Volatile var stopped = false
    @Volatile var startedAt = 0L
    @Volatile var onChange: (() -> Unit)? = null

    private var done: List<Step>? = null
    private var doneAt = 0L

    private fun fire() { onChange?.let { ui.post(it) } }

    @Synchronized fun start() {
        steps = emptyList()
        running = true
        stopped = false
        done = null
        startedAt = SystemClock.uptimeMillis()
        fire()
    }

    @Synchronized fun begin(kind: Int, title: String, req: String = "", detail: String = ""): Step {
        val s = Step(kind, title)
        // roke gaye / khatam kaam ka koi purana thread naya step na jod de
        if (!running) return s
        s.req = req
        s.detail = detail
        steps = steps + s
        fire()
        return s
    }

    fun update(s: Step, detail: String? = null, res: String? = null) {
        if (detail != null) s.detail = detail
        if (res != null) s.res = res
        fire()
    }

    fun ok(s: Step, detail: String? = null, res: String? = null) {
        if (detail != null) s.detail = detail
        if (res != null) s.res = res
        s.state = OK
        s.t1 = SystemClock.uptimeMillis()
        fire()
    }

    fun fail(s: Step, detail: String? = null, res: String? = null) {
        if (detail != null) s.detail = detail
        if (res != null) s.res = res
        s.state = FAIL
        s.t1 = SystemClock.uptimeMillis()
        fire()
    }

    // kaam poora (ya roka gaya): khule steps band karo aur jaankari bachao
    @Synchronized fun end(wasStopped: Boolean = false) {
        if (!running) return
        for (s in steps) {
            if (s.state == RUN) {
                s.state = if (wasStopped) FAIL else OK
                if (wasStopped) s.detail = "Stopped"
                s.t1 = SystemClock.uptimeMillis()
            }
        }
        running = false
        stopped = wasStopped
        done = steps
        doneAt = SystemClock.uptimeMillis()
        fire()
    }

    fun current(): Step? = steps.lastOrNull { it.state == RUN } ?: steps.lastOrNull()

    // jawab ke upar chhoti "kya kiya" line ke liye, sirf abhi ke jawab par
    @Synchronized fun takeFinished(): List<Step>? {
        val d = done
        done = null
        if (d == null || SystemClock.uptimeMillis() - doneAt > 4000L) return null
        val worth = d.size >= 2 || d.any { it.kind == 1 || it.kind == 2 || it.kind == 4 }
        return if (worth) d else null
    }
}
