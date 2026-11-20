package com.example.assistant

import android.content.SharedPreferences

// Pehle build ke time GitHub secret se aayi key use hoti hai.
// Woh khaali ho to purana tareeka chalta hai (app mein haath se daali hui key).
object Keys {
    fun get(prefs: SharedPreferences): String {
        val own = prefs.getString("key", "") ?: ""
        if (own.isNotBlank()) return own
        return BuildConfig.GEMINI_API_KEY
    }
}

// Baaki services ki keys (build ke time GitHub secrets se)
object Extra {
    // Settings mein dali hui key pehle, warna build wali
    private fun pick(name: String, built: String): String {
        val own = try { Usage.p?.getString("k_$name", "") ?: "" } catch (e: Exception) { "" }
        return if (own.isNotBlank()) own else built
    }
    val brave: String get() = pick("brave", BuildConfig.BRAVE_KEY)
    val vt: String get() = BuildConfig.VT_KEY
    val polli: String get() = BuildConfig.POLLI_KEY
    val groq: String get() = pick("groq", BuildConfig.GROQ_KEY)
    val openrouter: String get() = pick("openrouter", BuildConfig.OPENROUTER_KEY)
    val eleven: String get() = ""
    val openai: String get() = BuildConfig.OPENAI_KEY
}
