package com.example.assistant

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Model ki list (kaam ke hisaab se) aur chuna hua model
object Models {
    // free: OpenRouter ke liye asli daam se, baaki ke liye andaza (Gemini flash/Groq free, OpenAI paid)
    fun guessFree(prov: String, id: String, cat: String): Boolean = when (prov) {
        "groq" -> true
        "openai" -> false
        "gemini" -> cat != "photo" && cat != "video" && cat != "music" && !id.contains("pro")
        else -> false
    }

    class M(val prov: String, val id: String, val cat: String, val free: Boolean = guessFree(prov, id, cat)) {
        val key: String get() = "$prov|$id"
        val name: String get() = nice(id)
        val tag: String get() = when (prov) { "gemini" -> "Gemini"; "groq" -> "Groq"; "openai" -> "OpenAI"; else -> "OpenRouter" }
    }

    val CATS = listOf("chat" to "Chat", "coding" to "Coding", "web" to "Web search",
        "photo" to "Photo", "video" to "Video", "music" to "Music")

    fun nice(id: String): String =
        id.substringAfterLast('/').removePrefix("models/").split('-', '_', ':').filter { it.isNotEmpty() }
            .joinToString(" ") {
                when {
                    it == "gpt" -> "GPT"
                    it == "oss" -> "OSS"
                    it[0].isDigit() -> it
                    else -> it.replaceFirstChar { c -> c.uppercase() }
                }
            }

    private val builtin = listOf(
        M("gemini", "gemini-3.8-flash", "chat"), M("gemini", "gemini-3.6-flash", "chat"),
        M("gemini", "gemini-3.5-flash-lite", "chat"), M("gemini", "gemini-2.5-flash", "chat"),
        M("groq", "llama-3.3-70b-versatile", "chat"), M("groq", "openai/gpt-oss-120b", "chat"),
        M("groq", "openai/gpt-oss-20b", "chat"), M("groq", "qwen/qwen3.8-27b", "chat"),
        M("openai", "gpt-4o-mini", "chat"),
        M("gemini", "gemini-3.8-flash", "coding"), M("gemini", "gemini-2.5-flash", "coding"),
        M("groq", "llama-3.3-70b-versatile", "coding"), M("groq", "openai/gpt-oss-120b", "coding"),
        M("groq", "qwen/qwen3.8-27b", "coding"),
        M("gemini", "gemini-3.8-flash", "web"), M("gemini", "gemini-2.5-flash", "web"),
        M("openrouter", "perplexity/sonar", "web", false),
        M("gemini", "gemini-2.5-flash-image", "photo"), M("gemini", "gemini-3.1-flash-image-preview", "photo"),
        M("gemini", "gemini-3-pro-image-preview", "photo"), M("gemini", "gemini-3.1-flash-lite-image", "photo"),
        M("openai", "gpt-image-1", "photo"),
        M("openai", "dall-e-3", "photo"),
        M("gemini", "gemini-omni-1.1-flash", "video"),
        M("gemini", "lyria-3-clip-preview", "music"), M("gemini", "lyria-3.5", "music")
    )

    fun list(p: SharedPreferences): List<M> {
        val s = p.getString("models_json", null) ?: return builtin
        return try {
            val a = JSONArray(s)
            val out = ArrayList<M>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(M(o.getString("p"), o.getString("i"), o.getString("c"), o.optBoolean("f", guessFree(o.getString("p"), o.getString("i"), o.getString("c")))))
            }
            // jo hamesha chalte hain unhe bhi rakho
            for (b in builtin) if (out.none { it.key == b.key && it.cat == b.cat }) out.add(b)
            out
        } catch (e: Exception) { builtin }
    }

    fun stale(p: SharedPreferences) = System.currentTimeMillis() - p.getLong("models_time", 0L) > 12 * 3600 * 1000L

    fun sel(p: SharedPreferences, cat: String): M? {
        val v = p.getString("sel_$cat", null) ?: return null
        val i = v.indexOf('|')
        if (i < 0) return null
        return M(v.substring(0, i), v.substring(i + 1), cat, true)
    }

    fun setSel(p: SharedPreferences, cat: String, m: M?) {
        p.edit().apply { if (m == null) remove("sel_$cat") else putString("sel_$cat", m.key) }.apply()
    }

    fun think(p: SharedPreferences) = p.getString("think", "medium") ?: "medium"

    private fun get(url: String, hdr: Map<String, String>): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        for ((k, v) in hdr) c.setRequestProperty(k, v)
        if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
    } catch (e: Exception) { null }

    // internet se sab providers ki asli list laata hai (background mein)
    fun refresh(p: SharedPreferences, gemKey: String) {
        val out = ArrayList<M>()
        fun add(prov: String, id: String, cat: String, free: Boolean = guessFree(prov, id, cat)) { if (out.none { it.prov == prov && it.id == id && it.cat == cat }) out.add(M(prov, id, cat, free)) }

        if (gemKey.isNotEmpty()) get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", mapOf("x-goog-api-key" to gemKey))?.let { s ->
            try {
                val a = JSONObject(s).optJSONArray("models") ?: JSONArray()
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val id = o.getString("name").removePrefix("models/")
                    val gen = (o.optJSONArray("supportedGenerationMethods")?.toString() ?: "")
                    when {
                        id.contains("lyria") -> add("gemini", id, "music")
                        id.contains("veo") || id.contains("omni") -> add("gemini", id, "video")
                        id.contains("image") || id.startsWith("imagen") -> add("gemini", id, "photo")
                        id.startsWith("gemini") && gen.contains("generateContent") &&
                            listOf("tts", "embedding", "live", "audio", "robotics", "computer", "native", "dialog").none { id.contains(it) } -> {
                            add("gemini", id, "chat")
                            if (id.contains("pro") || (id.contains("flash") && !id.contains("lite"))) add("gemini", id, "coding")
                            if (!id.contains("lite")) add("gemini", id, "web")
                        }
                    }
                }
            } catch (e: Exception) { }
        }
        if (Extra.groq.isNotEmpty()) get("https://api.groq.com/openai/v1/models", mapOf("Authorization" to "Bearer " + Extra.groq))?.let { s ->
            try {
                val a = JSONObject(s).optJSONArray("data") ?: JSONArray()
                for (i in 0 until a.length()) {
                    val id = a.getJSONObject(i).getString("id")
                    if (listOf("whisper", "tts", "playai", "guard", "orpheus", "safeguard").any { id.contains(it) }) continue
                    add("groq", id, "chat")
                    if (listOf("oss", "coder", "qwen", "kimi", "llama-3.3", "deepseek").any { id.contains(it) }) add("groq", id, "coding")
                }
            } catch (e: Exception) { }
        }
        if (Extra.openai.isNotEmpty()) get("https://api.openai.com/v1/models", mapOf("Authorization" to "Bearer " + Extra.openai))?.let { s ->
            try {
                val a = JSONObject(s).optJSONArray("data") ?: JSONArray()
                val dated = Regex("-\\d{4}-\\d{2}-\\d{2}$")
                for (i in 0 until a.length()) {
                    val id = a.getJSONObject(i).getString("id")
                    if (dated.containsMatchIn(id) || id.endsWith("-latest") && false) continue
                    when {
                        id.startsWith("gpt-image") || id.startsWith("dall-e") -> add("openai", id, "photo")
                        id.contains("search") -> if (id.startsWith("gpt")) add("openai", id, "web")
                        listOf("audio", "realtime", "transcribe", "tts", "embedding", "moderation", "instruct", "davinci", "babbage", "vision", "sora", "image", "whisper").any { id.contains(it) } -> {}
                        Regex("^(gpt|o\\d|chatgpt|codex)").containsMatchIn(id) -> {
                            add("openai", id, "chat")
                            if (id.contains("codex") || Regex("^o\\d").containsMatchIn(id) || id.startsWith("gpt-5") || id.startsWith("gpt-4.1")) add("openai", id, "coding")
                        }
                    }
                }
            } catch (e: Exception) { }
        }
        get("https://openrouter.ai/api/v1/models", emptyMap())?.let { s ->
            try {
                val a = JSONObject(s).optJSONArray("data") ?: JSONArray()
                class R(val id: String, val created: Long, val img: Boolean, val free: Boolean)
                val rs = ArrayList<R>()
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val outm = o.optJSONObject("architecture")?.optJSONArray("output_modalities")?.toString() ?: ""
                    val pr = o.optJSONObject("pricing")
                    val fr = pr != null && pr.optString("prompt") == "0" && pr.optString("completion") == "0"
                    rs.add(R(o.getString("id"), o.optLong("created"), outm.contains("image"), fr))
                }
                rs.sortByDescending { it.created }
                var nImg = 0
                var nCode = 0
                var fChat = 0
                var fCode = 0
                var nWeb = 0
                val perProv = HashMap<String, Int>()
                val top = listOf("anthropic", "openai", "google", "deepseek", "qwen", "moonshotai", "x-ai", "meta-llama", "mistralai")
                for (r in rs) {
                    val prov = r.id.substringBefore('/')
                    if (r.free && !r.img && prov != "perplexity") {
                        val code = listOf("coder", "code", "devstral").any { r.id.contains(it) }
                        if (code && fCode < 6) { add("openrouter", r.id, "coding", true); fCode++ }
                        else if (!code && fChat < 10) { add("openrouter", r.id, "chat", true); fChat++ }
                        continue
                    }
                    if (r.img) { if (nImg < 6) { add("openrouter", r.id, "photo", r.free); nImg++ }; continue }
                    if (prov == "perplexity") { if (nWeb < 5) { add("openrouter", r.id, "web", false); nWeb++ }; continue }
                    if (prov !in top || r.id.contains(":free") || r.id.contains("-preview-")) continue
                    val n = perProv[prov] ?: 0
                    if (n < 2) { add("openrouter", r.id, "chat", false); perProv[prov] = n + 1 }
                    if (nCode < 8 && (listOf("coder", "code", "devstral").any { r.id.contains(it) } || prov == "anthropic" || prov == "deepseek")) {
                        add("openrouter", r.id, "coding", false); nCode++
                    }
                }
            } catch (e: Exception) { }
        }
        if (out.isEmpty()) return
        val arr = JSONArray()
        for (m in out) arr.put(JSONObject().put("p", m.prov).put("i", m.id).put("c", m.cat).put("f", m.free))
        p.edit().putString("models_json", arr.toString()).putLong("models_time", System.currentTimeMillis()).apply()
    }
}
