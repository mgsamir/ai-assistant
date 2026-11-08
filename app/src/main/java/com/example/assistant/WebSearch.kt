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

    @Volatile var lastErr = ""
    private val SEARCH_MODELS = listOf("gemini-2.5-flash", "gemini-3.6-flash", "gemini-3.8-flash", "gemini-2.5-flash-lite", "gemini-3.5-flash-lite")

    fun ask(key: String, query: String, models: List<String>, extra: String? = null): String? {
        lastErr = ""
        val sys = "Use Google Search to find the current, correct answer. " +
            "Today is " + java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.ENGLISH).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") }.format(java.util.Date()) + " (India). " +
            "The user lives in Hut Bay, Little Andaman, Andaman and Nicobar Islands, India. Hut Bay always means this place, never Hout Bay in South Africa; local questions mean Hut Bay / Little Andaman. For days, festivals, holidays, news, prices and events assume India and ignore foreign events unless he names a country. If he says kal, aaj or parson, use today's date. " +
            "Speak like Gemini speaks to an ordinary Indian: clear, natural, easy daily words, no heavy shuddh Hindi, and no English office words (listed, countdown, top events, schedule), and no half translated English phrases. Do not use English news words like accused, listed, tenders, jetty, rescue operation, restrictions, delays; say them in plain Hindi (aaropi, sarkari theka, ghat, bachav kaam, rok, deri). Do not start with \"2026 ki ... news\"; say dates in simple words. " +
            "Answer in simple everyday Hindustani (Hindi-Urdu mix; keep only English words everyone uses, like police, hospital, phone, bus). Give a complete, clear answer in one go like a good Gemini answer: the direct answer first, " +
            "then all key details (exact dates and day, timings, reasons, names, numbers, and anything that could confuse, such as two different dates). Say clearly whether it is yesterday, today or tomorrow compared with today's date. 4 to 8 sentences. " +
            "Never tell the user to search on Google or open a website. No markdown, no asterisks. " +
            "Write exactly two lines, each line one paragraph:\nS: the full answer in Hinglish (Hindi in English letters)\nV: the same full answer in Hindi Devanagari script"
        val text = if (extra.isNullOrBlank()) query
            else query + "\n\nExtra results from the Brave search engine (use them together with Google Search):\n" + extra
        for (pass in 0..1) {
            for (m in SEARCH_MODELS) {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
                    .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))))
                if (pass == 0) body.put("safetySettings", safety())
                try {
                    val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent")
                        .openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.connectTimeout = 8000
                    c.readTimeout = 20000
                    c.setRequestProperty("Content-Type", "application/json")
                    c.setRequestProperty("x-goog-api-key", key)
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                    val code = c.responseCode
                    val resp = (if (code == 200) c.inputStream else c.errorStream).bufferedReader().readText()
                    if (code == 200) {
                        val parts = JSONObject(resp).optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")
                        if (parts == null) { lastErr = "$m: khaali jawab"; continue }
                        val sb = StringBuilder()
                        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
                        if (sb.isNotBlank()) return sb.toString()
                        lastErr = "$m: khaali text"
                    } else {
                        val msg = try { JSONObject(resp).optJSONObject("error")?.optString("message", "") ?: "" } catch (e: Exception) { "" }
                        lastErr = "$m: $code ${msg.take(90)}"
                        if (code == 401 || code == 403) return null
                    }
                } catch (e: Exception) { lastErr = "$m: ${e.message ?: e.javaClass.simpleName}" }
            }
        }
        return null
    }
}
