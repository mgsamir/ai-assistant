package com.example.assistant

import android.content.SharedPreferences

// Pehle build ke time GitHub secret se aayi key use hoti hai.
// Woh khaali ho to purana tareeka chalta hai (app mein haath se daali hui key).
object Keys {
    fun get(prefs: SharedPreferences): String {
        val built = BuildConfig.GEMINI_API_KEY
        if (built.isNotEmpty()) return built
        return prefs.getString("key", "") ?: ""
    }
}

// Baaki services ki keys (build ke time GitHub secrets se)
object Extra {
    val brave: String get() = BuildConfig.BRAVE_KEY
    val vt: String get() = BuildConfig.VT_KEY
    val groq: String get() = BuildConfig.GROQ_KEY
    val openrouter: String get() = BuildConfig.OPENROUTER_KEY
    val eleven: String get() = BuildConfig.ELEVEN_KEY
    val openai: String get() = BuildConfig.OPENAI_KEY
}
