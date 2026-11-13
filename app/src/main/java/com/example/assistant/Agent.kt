package com.example.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Antigravity agent: Google ke cloud mein kai kadam mein kaam karta hai (code, search, links), thoda time lagta hai
object Agent {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/interactions"

    private fun call(url: String, key: String, body: JSONObject?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 60000
        c.setRequestProperty("x-goog-api-key", key)
        if (body != null) {
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        return Pair(code, text)
    }

    private fun text(j: JSONObject): String {
        val sb = StringBuilder()
        val steps = j.optJSONArray("steps") ?: return ""
        for (i in 0 until steps.length()) {
            val st = steps.optJSONObject(i) ?: continue
            if (st.optString("type") != "model_output") continue
            val cs = st.optJSONArray("content") ?: continue
            for (k in 0 until cs.length()) {
                val b = cs.optJSONObject(k) ?: continue
                if (b.optString("type") == "text") sb.append(b.optString("text")).append("\n")
            }
        }
        return sb.toString().trim()
    }

    fun run(key: String, q: String, agent: String): String {
        if (key.isEmpty()) return "Gemini key nahi mili."
        try {
            val sys = "Reply in the same language as the task, clear and natural. The user lives in Hut Bay, Little Andaman, India. Give the final result clearly and briefly."
            val body = JSONObject().put("agent", agent).put("input", sys + "\n\nTask: " + q).put("background", true)
            var (code, resp) = call(BASE, key, body)
            if (code !in 200..299) {
                val m = try { JSONObject(resp).optJSONObject("error")?.optString("message") ?: "" } catch (e: Exception) { "" }
                return if (code == 429) "Agent ki free limit khatam ho gayi (roz 100). Kal Pacific time se dubara chalegi."
                else "Agent nahi chala: " + (m.ifBlank { "code $code" }).take(120)
            }
            var j = JSONObject(resp)
            val id = j.optString("id")
            Usage.hit("gemini", agent, 0)
            var tries = 0
            while (j.optString("status") in listOf("in_progress", "queued", "") && id.isNotEmpty() && tries < 60) {
                Thread.sleep(5000)
                val r = call("$BASE/$id", key, null)
                if (r.first in 200..299) j = JSONObject(r.second)
                tries++
            }
            val out = text(j)
            if (out.isNotBlank()) return out
            return if (j.optString("status") == "failed") "Agent se kaam poora nahi hua." else "Agent ne abhi tak jawab nahi diya, thodi der baad try karo."
        } catch (e: Exception) {
            return "Agent tak pahunch nahi paye, net check karo."
        }
    }
}
