package com.example.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Doosri services: Brave search, Groq / OpenAI / OpenRouter (Gemini ka badal), ElevenLabs (natural awaaz)
object Providers {

    // "aaj / abhi / latest / bhav / score" jaise sawaalon par khabar wali khoj bhi chalegi
    private val FRESH = Regex("(?i)\\b(today|now|latest|live|news|price|rate|score|result|current|breaking|aaj|abhi|taaza|khabar|bhav|bhaav|kimat|nateeja|share|sensex|nifty|gold|silver|petrol|diesel)\\b|\u0906\u091C|\u0905\u092D\u0940|\u0924\u093E\u091C\u093C\u093E|\u0916\u092C\u0930|\u0916\u093C\u092C\u0930|\u092D\u093E\u0935|\u0915\u0940\u092E\u0924|\u0928\u0924\u0940\u091C\u093E|\u0938\u094D\u0915\u094B\u0930")

    // live / taaza sawaal hai ya nahi (score, news, bhav, aaj...)
    fun isLive(q: String): Boolean = FRESH.containsMatchIn(q)

    private fun braveGet(path: String, q: String, extra: String, key: String): JSONObject? {
        return try {
            val c = URL("https://api.search.brave.com/res/v1/$path?q=" + URLEncoder.encode(q, "UTF-8") + extra)
                .openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("X-Subscription-Token", key)
            if (c.responseCode != 200) null else JSONObject(c.inputStream.bufferedReader().readText())
        } catch (e: Exception) { null }
    }

    // ek web page ka saaf text (script / style / tag hata kar), shuru ka hissa
    private fun pageText(u: String): String {
        return try {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 3500
            c.readTimeout = 4500
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            c.setRequestProperty("Accept", "text/html")
            if (c.responseCode != 200 || !(c.contentType ?: "").contains("text/html", true)) return ""
            val bos = java.io.ByteArrayOutputStream()
            c.inputStream.use { ins ->
                val buf = ByteArray(8192)
                while (bos.size() < 300000) { val n = ins.read(buf); if (n < 0) break; bos.write(buf, 0, n) }
            }
            String(bos.toByteArray(), Charsets.UTF_8)
                .replace(Regex("(?is)<(script|style|noscript|svg|header|footer|nav)[^>]*>.*?</\\1>"), " ")
                .replace(Regex("(?s)<!--.*?-->"), " ")
                .replace(Regex("<[^>]*>"), " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
                .replace(Regex("\\s+"), " ").trim().take(1600)
        } catch (e: Exception) { "" }
    }

    // Brave Search: top natije + (taaza sawaal par) khabar + upar ke 3 pages ka asli text
    fun brave(q: String): String? {
        val key = Extra.brave
        if (key.isEmpty() || q.isBlank()) return null
        Usage.hitSimple("brave")
        val web = braveGet("web/search", q, "&count=6", key)
        val arr = web?.optJSONObject("web")?.optJSONArray("results")
        val news = if (FRESH.containsMatchIn(q)) braveGet("news/search", q, "&count=5&freshness=pd", key)?.optJSONArray("results") else null
        if ((arr == null || arr.length() == 0) && (news == null || news.length() == 0)) return null
        val sb = StringBuilder()
        val res = ArrayList<Triple<String, String, String>>()
        fun clean(t: String) = t.replace(Regex("<[^>]*>"), "")
        if (arr != null) for (i in 0 until minOf(arr.length(), 6)) {
            val o = arr.getJSONObject(i)
            res.add(Triple(o.optString("title"), o.optString("url"), clean(o.optString("description"))))
            sb.append("- ").append(o.optString("title")).append(": ").append(clean(o.optString("description"))).append("\n")
        }
        if (news != null && news.length() > 0) {
            sb.append("\nLatest news:\n")
            for (i in 0 until minOf(news.length(), 5)) {
                val o = news.getJSONObject(i)
                val age = o.optString("age")
                res.add(Triple(o.optString("title"), o.optString("url"), clean(o.optString("description"))))
                sb.append("- ").append(o.optString("title")).append(if (age.isNotBlank()) " ($age)" else "").append(": ").append(clean(o.optString("description"))).append("\n")
            }
        }
        // upar ke 3 pages ek saath padho (zyada se zyada kuch second)
        val urls = res.map { it.second }.filter { it.startsWith("http") && !it.contains("youtube.com") && !it.contains("youtu.be") && !it.endsWith(".pdf") }.distinct().take(3)
        val pages = arrayOfNulls<String>(urls.size)
        val ts = urls.mapIndexed { i, u -> Thread { pages[i] = pageText(u) }.also { it.start() } }
        val end = System.currentTimeMillis() + 6000
        for (t in ts) { val left = end - System.currentTimeMillis(); if (left > 0) try { t.join(left) } catch (e: Exception) { } }
        val got = urls.indices.filter { !pages[it].isNullOrBlank() }
        if (got.isNotEmpty()) {
            sb.append("\nPage contents:\n")
            for (i in got) sb.append("[").append(urls[i]).append("]\n").append(pages[i]).append("\n\n")
        }
        lastResults = res
        return sb.toString().ifBlank { null }
    }

    @Volatile var lastResults: List<Triple<String, String, String>> = emptyList()

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
            val body = JSONObject().put("model", model).put("messages", msgs)
            if (!Regex("^(o\\d|gpt-5)").containsMatchIn(model)) body.put("temperature", 0.6)
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 40000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer $key")
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode != 200) return null
            val prov = if (url.contains("groq")) "groq" else if (url.contains("openai")) "openai" else "openrouter"
            if (prov == "groq") Usage.groqHeaders(model, c)
            val jo = JSONObject(c.inputStream.bufferedReader().readText())
            Usage.hit(prov, model, jo.optJSONObject("usage")?.optInt("total_tokens") ?: 0)
            lastModel = model
            jo.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
        } catch (e: Exception) { null }
    }

    // Chune hue model se seedha jawab (Groq / OpenAI / OpenRouter)
    fun chatWith(prov: String, id: String, system: String, hist: List<Pair<String, String>>, q: String): String? {
        val (url, key) = when (prov) {
            "groq" -> Pair("https://api.groq.com/openai/v1/chat/completions", Extra.groq)
            "openai" -> Pair("https://api.openai.com/v1/chat/completions", Extra.openai)
            "openrouter" -> Pair("https://openrouter.ai/api/v1/chat/completions", Extra.openrouter)
            else -> return null
        }
        return chat(url, key, id, system, hist, q)?.takeIf { it.isNotBlank() }
    }

    // Gemini na chale to pehle Groq, phir OpenAI, phir OpenRouter
    @Volatile var lastModel = ""

    // only = "" (sab, pehle jaisa) ya "groq" / "openrouter" (sirf wahi provider)
    fun fallback(system: String, hist: List<Pair<String, String>>, q: String, only: String = ""): String? {
        if (only.isEmpty() || only == "groq") {
            chat("https://api.groq.com/openai/v1/chat/completions", Extra.groq,
                "llama-3.3-70b-versatile", system, hist, q)?.let { if (it.isNotBlank()) return it }
            if (only == "groq") {
                val sp0 = Usage.p
                val more = if (sp0 != null) Models.list(sp0).filter { it.prov == "groq" && it.cat == "chat" && it.id != "llama-3.3-70b-versatile" }.take(3) else emptyList()
                for (m in more) chat("https://api.groq.com/openai/v1/chat/completions", Extra.groq,
                    m.id, system, hist, q)?.let { if (it.isNotBlank()) return it }
            }
        }
        if (only.isEmpty() || only == "openrouter") {
            // OpenRouter: pehle free models (paisa kharch nahi), tabhi auto jab free na chalein
            val sp = Usage.p
            val frees = if (sp != null) Models.list(sp).filter { it.prov == "openrouter" && it.free && it.cat == "chat" }.take(3) else emptyList()
            for (m in frees) {
                chat("https://openrouter.ai/api/v1/chat/completions", Extra.openrouter,
                    m.id, system, hist, q)?.let { if (it.isNotBlank()) return it }
            }
            chat("https://openrouter.ai/api/v1/chat/completions", Extra.openrouter,
                "openrouter/auto", system, hist, q)?.let { if (it.isNotBlank()) return it }
        }
        return null
    }

    // ElevenLabs ki apni awaazon ki list (ek baar), gender ke saath
    private fun elevenVoices() {
        val sp = Usage.p ?: return
        if (sp.getString("el_voices", "[]") != "[]") return
        try {
            val c = URL("https://api.elevenlabs.io/v1/voices").openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 10000
            c.setRequestProperty("xi-api-key", Extra.eleven)
            if (c.responseCode != 200) return
            val arr = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("voices") ?: return
            val all = ArrayList<JSONObject>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val g = o.optJSONObject("labels")?.optString("gender") ?: ""
                if (g == "male" || g == "female") all.add(JSONObject().put("id", o.optString("voice_id")).put("g", g).put("pre", o.optString("category") == "premade"))
            }
            val pre = all.filter { it.optBoolean("pre") }
            val use = org.json.JSONArray()
            (if (pre.isNotEmpty()) pre else all).forEach { use.put(it) }
            if (use.length() > 0) sp.edit().putString("el_voices", use.toString()).apply()
        } catch (e: Exception) { }
    }

    private fun wav(pcm: ByteArray, rate: Int): ByteArray {
        val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcm.size)
        return h.array() + pcm
    }

    // Gemini ki apni awaaz (Kore, Puck ...): chuni hui awaaz ka naam seedha jaata hai, isliye ladka/ladki sach mein alag
    fun geminiTts(key: String, text: String, voice: String): ByteArray? {
        if (key.isBlank() || text.isBlank()) return null
        val body = JSONObject()
            .put("contents", org.json.JSONArray().put(JSONObject().put("role", "user")
                .put("parts", org.json.JSONArray().put(JSONObject().put("text", text.take(1800))))))
            .put("generationConfig", JSONObject()
                .put("responseModalities", org.json.JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                    .put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)))))
        for (m in listOf("gemini-3.8-flash-lite-tts", "gemini-3.1-flash-tts-preview", "gemini-3.8-flash-tts")) {
            try {
                val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 8000; c.readTimeout = 25000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode != 200) continue
                val inl = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optJSONObject("inlineData") ?: continue
                val raw = android.util.Base64.decode(inl.optString("data"), android.util.Base64.DEFAULT)
                if (raw.size < 100) continue
                return if (raw.size > 4 && String(raw, 0, 4) == "RIFF") raw else wav(raw, 24000)
            } catch (e: Exception) { }
        }
        return null
    }

    // ElevenLabs: text se natural awaaz (mp3)
    // Voice screen mein dikhane ke liye: ElevenLabs sach mein chal raha hai ya nahi
    @Volatile var elevenOk: Boolean? = null
    @Volatile var elevenAt = 0L
    @Volatile var elevenErr = ""

    fun eleven(text: String): ByteArray? {
        val key = Extra.eleven
        if (key.isEmpty() || text.isBlank()) return null
        elevenVoices()
        return try {
            val c = URL("https://api.elevenlabs.io/v1/text-to-speech/${Voice.elevenFor(Usage.p, Voice.cur(Usage.p))}?output_format=mp3_44100_64")
                .openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 20000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("xi-api-key", key)
            c.doOutput = true
            val body = JSONObject().put("text", text).put("model_id", "eleven_multilingual_v2")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            if (code != 200) {
                val why = try { JSONObject(c.errorStream?.bufferedReader()?.readText() ?: "").optJSONObject("detail")?.optString("status").orEmpty() } catch (e: Exception) { "" }
                elevenOk = false
                elevenErr = if (why.isNotBlank()) "HTTP $code ($why)" else "HTTP $code"
                return null
            }
            val bytes = c.inputStream.readBytes()
            elevenOk = true
            elevenAt = System.currentTimeMillis()
            bytes
        } catch (e: Exception) { elevenOk = false; elevenErr = "Network error"; null }
    }
}
