package com.example.assistant

import android.content.SharedPreferences

// Saari API keys/connections ek saath check karo: kaunsi set hai, kaunsi nahi, aur jahan
// bina quota kharch kiye live check mumkin hai (GitHub) wahan asli connectivity bhi.
object Health {
    fun check(p: SharedPreferences): String {
        val lines = ArrayList<String>()
        fun row(label: String, ok: Boolean, note: String = "") {
            lines.add((if (ok) "OK" else "MISSING") + " - $label" + (if (note.isNotBlank()) " ($note)" else ""))
        }
        row("Gemini key", Keys.get(p).isNotBlank())
        row("Groq key", Extra.groq.isNotBlank())
        row("OpenRouter key", Extra.openrouter.isNotBlank())
        row("Brave search key", Extra.brave.isNotBlank())
        row("News key (GNews/NewsData)", Extra.gnews.isNotBlank() || Extra.newsdata.isNotBlank())
        val gh = GitHub.checkConnection(p)
        row("GitHub (coding assistant)", gh.ok, gh.text)
        val labUrl = p.getString("lab_url", "") ?: ""
        val labSecret = p.getString("lab_secret", "") ?: ""
        if (labUrl.isBlank() || labSecret.isBlank()) row("Lab backend", false, "URL/secret Settings mein nahi daala -- optional, laptop/lab machine ke liye hai")
        else row("Lab backend", true, "URL aur secret set hain -- 'localhost scan karo' bolke live test kar sakta hai")

        val missing = lines.count { it.startsWith("MISSING") }
        val head = if (missing == 0) "Sab kuch set hai, koi dikkat nahi dikh rahi.\n\n" else "$missing cheez(ein) set nahi hain, neeche dekho.\n\n"
        return head + lines.joinToString("\n") { "- $it" }
    }
}
