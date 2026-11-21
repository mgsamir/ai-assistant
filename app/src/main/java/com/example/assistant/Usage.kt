package com.example.assistant

import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Model kitna use hua: aaj ki ginti, Groq/OpenRouter ka asli balance
object Usage {
    @Volatile var p: SharedPreferences? = null

    // Gemini ka din Pacific time ke raat 12 baje badalta hai, baaki ka phone ke hisaab se
    private fun day(prov: String = ""): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        if (prov == "gemini") f.timeZone = java.util.TimeZone.getTimeZone("America/Los_Angeles")
        return f.format(Date())
    }

    // Google ki free limit (roz ki requests). Google ab khud nahi batata, ye naapi hui kam-se-kam limit hai
    // Google ki Rate Limit page se (free tier, roz ki requests)
    private fun geminiDefault(id: String): Int? = when {
        id.contains("pro") || id.contains("image") || id.contains("banana") || id.startsWith("imagen") ||
            id.contains("veo") || id.contains("omni") || id.contains("lyria") || id.contains("argon") ||
            id.contains("deep-research") -> 0
        id.contains("antigravity") -> 100
        id.contains("gemma") -> 14400
        id.contains("tts") -> 10
        id.contains("transcribe") -> 25
        id.contains("robotics") -> 20
        id.contains("lite") -> if (id.contains("3.5") || id.contains("3.1")) 500 else 20
        id.contains("flash") -> 20
        else -> null
    }

    // minute mein kitni requests (free tier)
    private fun geminiRpm(id: String): Int? = when {
        id.contains("antigravity") -> 60
        id.contains("gemma") -> 30
        id.contains("tts") || id.contains("transcribe") -> 3
        id.contains("lite") -> if (id.contains("3.5") || id.contains("3.1")) 15 else 10
        id.contains("flash") -> 5
        else -> null
    }

    private fun all(): JSONObject = try { JSONObject(p?.getString("usage", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    // ek call hua: prov = gemini/groq/openai/openrouter
    fun hit(prov: String, model: String, tokens: Int) {
        val sp = p ?: return
        try {
            synchronized(this) {
                val a = all()
                val k = "$prov|$model"
                val o = a.optJSONObject(k) ?: JSONObject()
                if (o.optString("d") != day(prov)) { o.put("d", day(prov)); o.put("r", 0); o.put("t", 0) }
                o.put("r", o.optInt("r") + 1)
                o.put("t", o.optInt("t") + tokens)
                a.put(k, o)
                sp.edit().putString("usage", a.toString()).apply()
            }
        } catch (e: Exception) { }
    }

    // aaj ki ginti har provider ki: (provider, requests, tokens)
    fun summary(): List<Triple<String, Int, Int>> {
        val a = all()
        val m = LinkedHashMap<String, IntArray>()
        val it = a.keys()
        while (it.hasNext()) {
            val k = it.next()
            val o = a.optJSONObject(k) ?: continue
            val prov = k.substringBefore('|')
            if (o.optString("d") != day(prov)) continue
            val x = m.getOrPut(prov) { IntArray(2) }
            x[0] += o.optInt("r"); x[1] += o.optInt("t")
        }
        return m.map { Triple(it.key, it.value[0], it.value[1]) }
    }

    // Groq har jawab ke saath asli baaki limit bhejta hai
    fun groqHeaders(model: String, c: HttpURLConnection) {
        val sp = p ?: return
        try {
            val lr = c.getHeaderField("x-ratelimit-limit-requests")?.toDoubleOrNull() ?: return
            val rr = c.getHeaderField("x-ratelimit-remaining-requests")?.toDoubleOrNull() ?: return
            synchronized(this) {
                val a = all()
                val k = "groq|$model"
                val o = a.optJSONObject(k) ?: JSONObject()
                o.put("gl", lr); o.put("gr", rr)
                a.put(k, o)
                sp.edit().putString("usage", a.toString()).apply()
            }
        } catch (e: Exception) { }
    }

    // OpenRouter: asli credit (dollar)
    fun refreshOpenRouter() {
        val sp = p ?: return
        if (Extra.openrouter.isEmpty()) return
        try {
            val c = URL("https://openrouter.ai/api/v1/key").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("Authorization", "Bearer " + Extra.openrouter)
            if (c.responseCode != 200) return
            val d = JSONObject(c.inputStream.bufferedReader().readText()).optJSONObject("data") ?: return
            val e = sp.edit()
            e.putFloat("or_used", d.optDouble("usage", 0.0).toFloat())
            if (d.isNull("limit")) e.remove("or_limit") else e.putFloat("or_limit", d.optDouble("limit").toFloat())
            e.apply()
        } catch (e: Exception) { }
    }

    class Info(val frac: Float?, val text: String)

    fun limitOf(m: Models.M): Int = p?.getInt("lim_" + m.key, 0) ?: 0
    fun setLimit(m: Models.M, n: Int) { p?.edit()?.apply { if (n <= 0) remove("lim_" + m.key) else putInt("lim_" + m.key, n) }?.apply() }

    // frac = kitna kharch ho chuka (0..1), text = dikhane wali line
    // Free plan mein bilkul nahi chalne wala Gemini model (aur user ne apni limit nahi daali)
    fun paidOnly(m: Models.M): Boolean = m.prov == "gemini" && limitOf(m) == 0 && geminiDefault(m.id) == 0

    fun info(m: Models.M): Info? {
        val sp = p ?: return null
        val o = all().optJSONObject(m.key)
        val today = o != null && o.optString("d") == day(m.prov)
        val req = if (today) o!!.optInt("r") else 0
        val tok = if (today) o!!.optInt("t") else 0
        if (m.prov == "groq" && o != null && o.has("gl")) {
            val l = o.optDouble("gl"); val r = o.optDouble("gr")
            if (l > 0) return Info(((l - r) / l).toFloat().coerceIn(0f, 1f), "${r.toInt()} / ${l.toInt()} left today")
        }
        if (m.prov == "openrouter") {
            if (m.id.contains(":free")) {
                val a = all()
                var n = 0
                val it = a.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val oo = a.optJSONObject(k) ?: continue
                    if (k.startsWith("openrouter|") && k.contains(":free") && oo.optString("d") == day("openrouter")) n += oo.optInt("r")
                }
                val lim = limitOf(m).let { if (it > 0) it else 50 }
                return Info((n.toFloat() / lim).coerceIn(0f, 1f), "$n / $lim used today (shared by all free models) \u00b7 ${maxOf(0, lim - n)} left")
            }
            val used = sp.getFloat("or_used", -1f)
            if (sp.contains("or_limit")) {
                val l = sp.getFloat("or_limit", 0f)
                if (l > 0f) return Info((used / l).coerceIn(0f, 1f), "\$" + String.format(Locale.US, "%.2f", l - used) + " left of \$" + String.format(Locale.US, "%.2f", l))
            }
            if (used >= 0f) return Info(null, "\$" + String.format(Locale.US, "%.3f", used) + " spent, no limit")
        }
        var lim = limitOf(m)
        if (lim == 0 && m.prov == "gemini") {
            val d = geminiDefault(m.id)
            if (d == 0) return Info(null, "Paid plan only")
            if (d != null) lim = d
        }
        if (lim > 0) return Info((req.toFloat() / lim).coerceIn(0f, 1f), "$req / $lim used today \u00b7 ${maxOf(0, lim - req)} left" + (if (m.prov == "gemini") geminiRpm(m.id)?.let { " \u00b7 $it per min" } ?: "" else ""))
        if (req > 0) return Info(null, "$req requests today, ${if (tok >= 1000) (tok / 1000).toString() + "k" else tok.toString()} tokens")
        if (m.prov == "openai") return Info(null, "Paid \u00b7 check balance on the OpenAI website")
        return null
    }
}
