package com.example.assistant

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Tpl(val name: String, val photo: Boolean, val thumb: String, val prompt: String)

// Trending aur Templates: Gemini (Google Search ke saath) roz dekhta hai ki abhi kya chal raha hai,
// aur list apne aap naye se badal jaati hai. Net na chale to pehle wali list rehti hai.
object Trends {
    private const val DAY = 24L * 3600 * 1000

    fun load(sp: SharedPreferences): Pair<List<Tpl>, List<Tpl>>? {
        val s = sp.getString("tpl_json", null) ?: return null
        return try { parse(JSONObject(s)) } catch (e: Exception) { null }
    }

    // 24 ghante se purani ho aur pichhle 1 ghante mein koshish na hui ho
    fun due(sp: SharedPreferences): Boolean {
        val now = System.currentTimeMillis()
        return now - sp.getLong("tpl_time", 0) > DAY && now - sp.getLong("tpl_try", 0) > 3600L * 1000
    }

    private fun parse(o: JSONObject): Pair<List<Tpl>, List<Tpl>>? {
        fun list(a: JSONArray?): List<Tpl> {
            val out = ArrayList<Tpl>()
            if (a == null) return out
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                val n = x.optString("name").trim()
                val th = x.optString("thumb").trim()
                val pr = x.optString("prompt").trim()
                if (n.isEmpty() || th.isEmpty() || pr.length < 40) continue
                out.add(Tpl(n.take(30), x.optBoolean("photo", false), th, pr))
            }
            return out.take(16)
        }
        val t = list(o.optJSONArray("trending"))
        val m = list(o.optJSONArray("templates"))
        return if (t.size >= 6 && m.size >= 6) Pair(t, m) else null
    }

    fun refresh(key: String, sp: SharedPreferences): Pair<List<Tpl>, List<Tpl>>? {
        sp.edit().putLong("tpl_try", System.currentTimeMillis()).apply()
        val today = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date())
        val ask = "Today is $today. Use Google Search to see which image creation templates and trending ideas the Gemini app, ChatGPT Images and similar AI image apps are promoting right now, especially ideas for India and the upcoming festivals or season. " +
            "Then output ONLY one JSON object (no markdown, no commentary) shaped exactly like {\"trending\":[12 items],\"templates\":[12 items]}. " +
            "'trending' = current seasonal and viral ideas (festival cards, photo restyle trends and similar). " +
            "'templates' = evergreen formats (poster, logo, illustration, headshot, icon, product photo, merch, infographic, flyer, book cover, app mockup, packaging and similar). " +
            "Each item is {\"name\": short title of at most 3 words, \"photo\": true if the idea needs the user to upload their own photo else false, " +
            "\"thumb\": one short English sentence describing a preview picture for the card (no real people, no brands), " +
            "\"prompt\": a complete, original, detailed English image generation prompt of 60 to 110 words; if photo is true it must start with 'Use my uploaded photo to' and say to keep the person's face recognisable; end with 'No text, logos or watermarks.' unless text is part of the design}. " +
            "Never use real people's names, brands, logos or copyrighted characters. Family friendly only."
        for (m in listOf("gemini-2.5-flash", "gemini-2.5-flash-lite")) {
            try {
                val body = JSONObject()
                    .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", ask)))))
                val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 10000
                c.readTimeout = 120000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode != 200) continue
                val resp = c.inputStream.bufferedReader().readText()
                val parts = JSONObject(resp).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                val sb = StringBuilder()
                for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
                val s = sb.toString()
                val a = s.indexOf('{')
                val b = s.lastIndexOf('}')
                if (a < 0 || b <= a) continue
                val js = s.substring(a, b + 1)
                val res = parse(JSONObject(js)) ?: continue
                Usage.hit("gemini", m, JSONObject(resp).optJSONObject("usageMetadata")?.optInt("totalTokenCount") ?: 0)
                sp.edit().putString("tpl_json", js).putLong("tpl_time", System.currentTimeMillis()).apply()
                return res
            } catch (e: Exception) { }
        }
        return null
    }
}
