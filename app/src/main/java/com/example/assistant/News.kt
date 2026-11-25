package com.example.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Taaza headlines: pehle GNews, wo na ho ya fail ho to NewsData.io. Dono free hain.
object News {
    private fun get(url: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        val code = c.responseCode
        val text = try { (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "" } catch (e: Exception) { "" }
        return code to text
    }

    private fun gnews(query: String, lang: String): String? {
        val key = Extra.gnews
        if (key.isEmpty()) return null
        val q = URLEncoder.encode(query, "UTF-8")
        val url = if (query.isBlank())
            "https://gnews.io/api/v4/top-headlines?lang=$lang&max=6&token=$key"
        else
            "https://gnews.io/api/v4/search?q=$q&lang=$lang&max=6&token=$key"
        val (code, body) = get(url)
        if (code != 200) return null
        val arr = JSONObject(body).optJSONArray("articles") ?: return null
        if (arr.length() == 0) return ""
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            sb.append("- ").append(a.optString("title")).append(" (").append(a.optJSONObject("source")?.optString("name") ?: "").append(")\n")
            val d = a.optString("description"); if (d.isNotBlank()) sb.append("  ").append(d.take(180)).append("\n")
        }
        return sb.toString().trim()
    }

    private fun newsdata(query: String, lang: String): String? {
        val key = Extra.newsdata
        if (key.isEmpty()) return null
        val q = if (query.isBlank()) "" else "&q=" + URLEncoder.encode(query, "UTF-8")
        val url = "https://newsdata.io/api/1/news?apikey=$key&language=$lang$q"
        val (code, body) = get(url)
        if (code != 200) return null
        val arr = JSONObject(body).optJSONArray("results") ?: return null
        if (arr.length() == 0) return ""
        val sb = StringBuilder()
        for (i in 0 until minOf(6, arr.length())) {
            val a = arr.getJSONObject(i)
            sb.append("- ").append(a.optString("title")).append(" (").append(a.optString("source_id")).append(")\n")
            val d = a.optString("description"); if (d.isNotBlank() && d != "null") sb.append("  ").append(d.take(180)).append("\n")
        }
        return sb.toString().trim()
    }

    // query khaali ho to top headlines, nahi to us topic ki news. lang = "hi" ya "en"
    fun headlines(query: String, lang: String): String {
        if (Extra.gnews.isEmpty() && Extra.newsdata.isEmpty())
            return "News API key nahi mili. Settings mein GNews ya NewsData.io ki key daalo."
        val r1 = try { gnews(query, lang) } catch (e: Exception) { null }
        if (!r1.isNullOrBlank()) return r1
        val r2 = try { newsdata(query, lang) } catch (e: Exception) { null }
        if (!r2.isNullOrBlank()) return r2
        return if (r1 == "" || r2 == "") "\"$query\" par koi news nahi mili abhi." else "News laane mein dikkat hui, thodi der baad try karo."
    }
}
