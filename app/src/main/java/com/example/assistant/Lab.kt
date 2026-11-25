package com.example.assistant

import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Apne hi LAN/lab machine par chal rahe core/server.py se baat karta hai.
// Settings mein "Lab backend URL" aur "Lab secret" daalne hote hain.
object Lab {
    fun url(p: SharedPreferences): String = p.getString("lab_url", "") ?: ""
    fun secret(p: SharedPreferences): String = p.getString("lab_secret", "") ?: ""

    private fun call(p: SharedPreferences, body: JSONObject): String {
        val base = url(p)
        if (base.isEmpty()) return "Lab backend URL Settings mein daalo pehle (jaise http://192.168.1.5:8765)."
        val sec = secret(p)
        if (sec.isEmpty()) return "Lab secret Settings mein daalo pehle."
        return try {
            val c = URL(base.trimEnd('/')).openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 20000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("X-Lab-Secret", sec)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
            val o = try { JSONObject(text) } catch (e: Exception) { null }
            if (o == null) return "Lab backend se jawab samajh nahi aaya ($code)."
            if (!o.optBoolean("ok", false)) return "Error: " + o.optString("error", "unknown")
            o.toString(2)
        } catch (e: Exception) {
            "Lab backend tak pahunch nahi paya: " + (e.message ?: "connection failed") + ". Check karo ki PC/lab machine par core/server.py chal raha hai aur phone usi WiFi/LAN par hai."
        }
    }

    fun runCommand(p: SharedPreferences, cmd: String): String = call(p, JSONObject().put("action", "run_command").put("cmd", cmd))
    fun checkWeb(p: SharedPreferences, url2: String): String = call(p, JSONObject().put("action", "check_web").put("url", url2))
    fun scanLocal(p: SharedPreferences): String = call(p, JSONObject().put("action", "scan_local"))
}
