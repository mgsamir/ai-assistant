package com.example.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Google Search seedha app ke andar: Gemini ka Google Search tool se jawab laata hai.
// Browser ya Google khulta nahi.
object WebSearch {
    // Google ke default filter bahut sakht hote hain (padhai wale sawaal bhi rok dete hain).
    // App ki taraf se koi extra rok nahi; jo rok Google ke model mein andar hai, wahi rahegi.
    fun safety(): JSONArray {
        val a = JSONArray()
        for (c in listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT")) {
            a.put(JSONObject().put("category", c).put("threshold", "BLOCK_NONE"))
        }
        return a
    }

    fun ask(key: String, query: String, models: List<String>): String? {
        val sys = "Use Google Search to find the current, correct answer. " +
            "Answer in simple everyday Hindustani (Hindi-Urdu mix, common English words kept), 1 to 4 short sentences, with the actual facts and numbers. " +
            "Never tell the user to search on Google or open a website. No markdown, no asterisks. " +
            "Write exactly two lines:\nS: the answer in Hinglish (Hindi in English letters)\nV: the same answer in Hindi Devanagari script"
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("safetySettings", safety())
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", query)))))
        for (m in models) {
            try {
                val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent")
                    .openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 8000
                c.readTimeout = 25000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val resp = (if (code == 200) c.inputStream else c.errorStream).bufferedReader().readText()
                if (code == 200) {
                    val parts = JSONObject(resp).optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                    val sb = StringBuilder()
                    for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
                    if (sb.isNotBlank()) return sb.toString()
                } else if (code == 401 || code == 403) return null
            } catch (e: Exception) { }
        }
        return null
    }
}
