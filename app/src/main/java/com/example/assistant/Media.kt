package com.example.assistant

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Image, video aur music banane ka kaam (Google ke models se)
object Media {
    class Out(val bytes: ByteArray, val mime: String)

    @Volatile var lastErr = ""

    private fun post(url: String, key: String, body: JSONObject, readMs: Int): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 10000
        c.readTimeout = readMs
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", key)
        c.doOutput = true
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        return Pair(code, text)
    }

    private fun why(code: Int, resp: String): String {
        val m = try { JSONObject(resp).optJSONObject("error")?.optString("message") ?: "" } catch (e: Exception) { "" }
        return when {
            code == 429 || m.contains("quota", true) -> "Free limit khatam ya is model ke liye paid plan chahiye"
            code == 403 || code == 404 -> "Ye model tumhari key par nahi chalta"
            m.isNotBlank() -> m.take(90)
            else -> "code $code"
        }
    }

    // Interactions API: steps -> model_output -> content (audio/video, base64)
    private fun interaction(key: String, model: String, input: Any, kind: String, readMs: Int): Out? {
        try {
            val body = JSONObject().put("model", model).put("input", input)
            val (code, resp) = post("https://generativelanguage.googleapis.com/v1beta/interactions", key, body, readMs)
            if (code !in 200..299) { lastErr = "$model: " + why(code, resp); return null }
            val steps = JSONObject(resp).optJSONArray("steps")
            if (steps != null) for (i in 0 until steps.length()) {
                val st = steps.getJSONObject(i)
                if (st.optString("type") != "model_output") continue
                val cs = st.optJSONArray("content") ?: continue
                for (j in 0 until cs.length()) {
                    val b = cs.getJSONObject(j)
                    if (b.optString("type") == kind && b.optString("data").isNotEmpty()) {
                        val mime = b.optString("mime_type").ifBlank { if (kind == "video") "video/mp4" else "audio/mpeg" }
                        Usage.hit("gemini", model, 0)
                        return Out(Base64.decode(b.getString("data"), Base64.DEFAULT), mime)
                    }
                }
            }
            lastErr = "$model: kuch bana nahi"
        } catch (e: Exception) {
            lastErr = "$model: " + (e.message ?: "net nahi chala").take(60)
        }
        return null
    }

    fun video(key: String, prompt: String, sel: Models.M? = null): Out? {
        lastErr = ""
        return interaction(key, sel?.id ?: "gemini-omni-1.1-flash", prompt, "video", 240000)
    }

    fun music(key: String, prompt: String, sel: Models.M? = null): Out? {
        lastErr = ""
        if (sel != null) interaction(key, sel.id, prompt, "audio", 150000)?.let { return it }
        interaction(key, "lyria-3-clip-preview", prompt, "audio", 120000)?.let { return it }
        return interaction(key, "lyria-3.5", prompt, "audio", 180000)
    }

    // attach = (mime, base64) photo ho to usi ko badalkar nayi image banata hai
    fun image(key: String, prompt: String, attach: Pair<String, String>?, sel: Models.M? = null): Out? {
        lastErr = ""
        val ps = sel
        val gem = listOf("gemini-3-pro-image-preview", "gemini-3.1-flash-image-preview", "gemini-2.5-flash-image", "gemini-3.1-flash-lite-image")
        if (ps != null && ps.prov == "openrouter") {
            try {
                val body = JSONObject().put("model", ps.id)
                    .put("modalities", JSONArray().put("image").put("text"))
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                val c = URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 10000
                c.readTimeout = 120000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Authorization", "Bearer " + Extra.openrouter)
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
                if (code in 200..299) {
                    val u = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                        ?.optJSONArray("images")?.optJSONObject(0)?.optJSONObject("image_url")?.optString("url") ?: ""
                    if (u.startsWith("data:") && u.contains(",")) return Out(Base64.decode(u.substringAfter(","), Base64.DEFAULT), "image/png")
                } else lastErr = "${ps.id}: " + why(code, text)
            } catch (e: Exception) { }
        }
        val openaiFirst = ps != null && ps.prov == "openai"
        if (openaiFirst && attach == null && Extra.openai.isNotEmpty()) openaiImage(prompt, listOf(ps!!.id))?.let { return it }
        val gemList = if (ps != null && ps.prov == "gemini") listOf(ps.id) + gem.filter { it != ps.id } else gem
        for (m in gemList) {
            try {
                val parts = JSONArray().put(JSONObject().put("text", prompt))
                if (attach != null) parts.put(JSONObject().put("inlineData",
                    JSONObject().put("mimeType", attach.first).put("data", attach.second)))
                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
                    .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                        .also { g -> if (m.contains("pro-image")) g.put("imageConfig", JSONObject().put("imageSize", "2K")) })
                    .put("safetySettings", WebSearch.safety())
                val (code, resp) = post("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent", key, body, 90000)
                if (code !in 200..299) { lastErr = "$m: " + why(code, resp); continue }
                val ps = JSONObject(resp).optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")
                if (ps != null) for (i in 0 until ps.length()) {
                    val d = ps.getJSONObject(i).let { it.optJSONObject("inlineData") ?: it.optJSONObject("inline_data") } ?: continue
                    val data = d.optString("data")
                    if (data.isNotEmpty()) {
                        Usage.hit("gemini", m, 0)
                        return Out(Base64.decode(data, Base64.DEFAULT), d.optString("mimeType").ifBlank { d.optString("mime_type").ifBlank { "image/png" } })
                    }
                }
                lastErr = "$m: image nahi aayi"
            } catch (e: Exception) {
                lastErr = "$m: " + (e.message ?: "net nahi chala").take(60)
            }
        }
        // Google se na bane to OpenAI se
                // Google se na bane to free Pollinations se (photo badalna isme nahi hota)
        if (attach == null) {
            try {
                val u = "https://image.pollinations.ai/prompt/" + URLEncoder.encode(prompt, "UTF-8") + "?width=1024&height=1024&nologo=true"
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 90000
                if (c.responseCode == 200) {
                    val b = c.inputStream.readBytes()
                    if (b.size > 2000) return Out(b, c.contentType ?: "image/jpeg")
                }
            } catch (e: Exception) { }
        }
        return null
    }

    private fun openaiImage(prompt: String, models: List<String>): Out? {
        for (model in models) {
            try {
                val body = JSONObject().put("model", model).put("prompt", prompt).put("size", "1024x1024")
                if (model.startsWith("dall-e")) body.put("response_format", "b64_json")
                val c = URL("https://api.openai.com/v1/images/generations").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 10000
                c.readTimeout = 120000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Authorization", "Bearer " + Extra.openai)
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
                if (code in 200..299) {
                    val b64 = JSONObject(text).optJSONArray("data")?.optJSONObject(0)?.optString("b64_json") ?: ""
                    if (b64.isNotEmpty()) { Usage.hit("openai", model, 0); return Out(Base64.decode(b64, Base64.DEFAULT), "image/png") }
                } else lastErr = "OpenAI $model: " + why(code, text)
            } catch (e: Exception) { }
        }
        return null
    }
}
