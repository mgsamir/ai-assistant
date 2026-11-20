package com.example.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Deep Research: sawal ko kai hisson mein baantkar search karta hai, phir ek poora report banata hai
object Research {
    private val MODELS = listOf("gemini-2.5-flash", "gemini-3.6-flash", "gemini-3.8-flash", "gemini-2.5-flash-lite")

    private fun gen(key: String, sys: String, user: String): String? {
        for (m in MODELS) {
            try {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", user)))))
                    .put("safetySettings", WebSearch.safety())
                val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent")
                    .openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 8000
                c.readTimeout = 60000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode != 200) continue
                val parts = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                val sb = StringBuilder()
                for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
                if (sb.isNotBlank()) { Usage.hit("gemini", m, 0); return sb.toString().trim() }
            } catch (e: Exception) { }
        }
        return null
    }

    private fun sLine(raw: String?): String {
        if (raw == null) return ""
        val t = raw.replace("*", "")
        val s = Regex("(?s)S:\\s*(.*?)(?:\\n\\s*V:|$)").find(t)?.groupValues?.get(1)?.trim()
        return s ?: t.trim()
    }

    fun run(key: String, q: String, personal: String): String {
        val plan = gen(key,
            "Split the user's topic into 4 different short web search questions that together cover it well (basics, latest news or numbers, causes or reasons, what it means for an ordinary person in India). The user lives in Hut Bay, Little Andaman, India; use this only if the topic is local. Write exactly 4 lines, one question per line, no numbering, no extra text.",
            q)
        val qs = (plan ?: "").lines().map { it.trim().trimStart('-', '*', ' ') }.filter { it.length > 5 }.take(4).ifEmpty { listOf(q) }
        val found = arrayOfNulls<String>(qs.size)
        val ts = qs.mapIndexed { i, s ->
            Thread {
                found[i] = sLine(WebSearch.ask(key, s, emptyList(), Providers.brave(s)))
            }.also { it.start() }
        }
        ts.forEach { it.join(70000) }
        val notes = qs.indices.joinToString("\n\n") { "Sawal: " + qs[it] + "\nMila: " + (found[it] ?: "") }
        val rep = gen(key,
            "Write a detailed, well organised research report using ONLY the findings given. Write in the same language and script as the topic, clear and natural like ChatGPT or Gemini. " +
            "Use short ## heading lines, **bold** for key words, short paragraphs, 350 to 600 words. Include exact dates, numbers and names from the findings. " +
            "Start with a 2 line summary, then the details, then what it means for the user. If findings disagree or are missing, say so honestly. " + personal,
            "Topic: $q\n\nFindings:\n$notes")
        return rep ?: "Couldn't create report. Try again."
    }
}
