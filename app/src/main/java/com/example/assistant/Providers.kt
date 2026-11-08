package com.example.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Doosri services: Brave search, Groq / OpenAI / OpenRouter (Gemini ka badal), ElevenLabs (natural awaaz)
object Providers {

    // Brave Search: top natije "title - snippet" ki shakal mein
    fun brave(q: String): String? {
        val key = Extra.brave
        if (key.isEmpty() || q.isBlank()) return null
        return try {
            val c = URL("https://api.search.brave.com/res/v1/web/search?count=6&q=" + URLEncoder.encode(q, "UTF-8"))
                .openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("X-Subscription-Token", key)
            if (c.responseCode != 200) return null
            val arr = JSONObject(c.inputStream.bufferedReader().readText())
                .optJSONObject("web")?.optJSONArray("results") ?: return null
            val sb = StringBuilder()
            for (i in 0 until minOf(arr.length(), 6)) {
                val o = arr.getJSONObject(i)
                sb.append("- ").append(o.optString("title")).append(": ")
                    .append(o.optString("description").replace(Regex("<[^>]*>"), "")).append("\n")
            }
            sb.toString().ifBlank { null }
        } catch (e: Exception) { null }
    }

    // OpenAI jaisa chat (Groq aur OpenRouter dono isi tarah chalte hain)
    private fun chat(url: String, key: String, model: String, system: String,
                     hist: List<Pair<String, String>>, q: String): String? {
        if (key.isEmpty()) return null
        return try {
            val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
            for ((u, a) in hist) {
                msgs.put(JSONObject().put("role", "user").put("content", u))
                msgs.put(JSONObject().put("role", "assistant").put("content", a))
            }
            msgs.put(JSONObject().put("role", "user").put("content", q))
            val body = JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.6)
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 25000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer $key")
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode != 200) return null
            JSONObject(c.inputStream.bufferedReader().readText())
                .optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
        } catch (e: Exception) { null }
    }

    // Gemini na chale to pehle Groq, phir OpenAI, phir OpenRouter
    fun fallback(system: String, hist: List<Pair<String, String>>, q: String): String? {
        chat("https://api.groq.com/openai/v1/chat/completions", Extra.groq,
            "llama-3.3-70b-versatile", system, hist, q)?.let { if (it.isNotBlank()) return it }
        chat("https://api.openai.com/v1/chat/completions", Extra.openai,
            "gpt-4o-mini", system, hist, q)?.let { if (it.isNotBlank()) return it }
        chat("https://openrouter.ai/api/v1/chat/completions", Extra.openrouter,
            "openrouter/auto", system, hist, q)?.let { if (it.isNotBlank()) return it }
        return null
    }

    // ElevenLabs: text se natural awaaz (mp3)
    fun eleven(text: String): ByteArray? {
        val key = Extra.eleven
        if (key.isEmpty() || text.isBlank()) return null
        return try {
            val c = URL("https://api.elevenlabs.io/v1/text-to-speech/21m00Tcm4TlvDq8ikWAM?output_format=mp3_44100_64")
                .openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 20000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("xi-api-key", key)
            c.doOutput = true
            val body = JSONObject().put("text", text).put("model_id", "eleven_multilingual_v2")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode != 200) return null
            c.inputStream.readBytes()
        } catch (e: Exception) { null }
    }
}
