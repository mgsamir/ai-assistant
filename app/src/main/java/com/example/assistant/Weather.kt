package com.example.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Weather {
    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        return c.inputStream.bufferedReader().readText()
    }

    private fun desc(code: Int): String = when (code) {
        0 -> "आसमान साफ है"
        1, 2 -> "हल्के बादल हैं"
        3 -> "बादल छाए हैं"
        45, 48 -> "कोहरा है"
        in 51..57 -> "बूंदाबांदी हो रही है"
        in 61..67 -> "बारिश हो रही है"
        in 71..77 -> "बर्फबारी हो रही है"
        in 80..82 -> "तेज बौछारें पड़ रही हैं"
        in 95..99 -> "आंधी-तूफान है"
        else -> "मौसम मिला-जुला है"
    }

    fun getSmartWeather(query: String, city: String): String {
        if (city.isBlank()) return "कौन सी जगह का मौसम बताऊँ?"
        return try {
            val g = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=hi&name=" + URLEncoder.encode(city, "UTF-8")))
            val arr = g.optJSONArray("results")
            if (arr == null || arr.length() == 0) return "$city नाम की जगह नहीं मिली।"
            
            val p = arr.getJSONObject(0)
            val lat = p.getDouble("latitude")
            val lon = p.getDouble("longitude")
            val name = p.optString("name", city)

            val w = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,wind_speed_10m"))
            val cur = w.getJSONObject("current")
            val temp = cur.getDouble("temperature_2m")
            val wind = cur.getDouble("wind_speed_10m")
            val code = cur.getInt("weather_code")

            val q = query.lowercase()
            // अगर यूजर टेम्परेचर या डिग्री पूछेगा तो नंबर और वैल्यू बताएगा
            if (q.contains("temperature") || q.contains("temp") || q.contains("degree") || q.contains("डिग्री") || q.contains("टेम्परेचर") || q.contains("गर्मी") || q.contains("ठंडा")) {
                "यहाँ $name का अभी का तापमान ${temp}°C है और हवा की गति $wind किमी/घंटा है।"
            } else {
                // आम बोलचाल में मौसम की स्थिति बताएगा
                "आज $name में ${desc(code)}।"
            }
        } catch (e: Exception) {
            "मौसम की जानकारी लाने में त्रुटि हुई।"
        }
    }
}
