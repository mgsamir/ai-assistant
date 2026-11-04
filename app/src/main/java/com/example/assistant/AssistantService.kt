package com.example.assistant

import android.content.Context

// Ab koi background service ya notification nahi hai.
// Assistant sirf ऐप खुला होने पर चलता है; ऐप बंद = assistant बंद.
object AssistantService {
    var engine: Assistant? = null

    fun ensure(ctx: Context) {
        if (engine != null) return
        val e = Assistant(ctx.applicationContext)
        e.onQuit = { stop() }
        engine = e
        e.start()
    }

    fun stop() {
        engine?.shutdown()
        engine = null
    }
}
