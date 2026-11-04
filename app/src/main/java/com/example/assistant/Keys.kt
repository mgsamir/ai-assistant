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
