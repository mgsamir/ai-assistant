package com.example.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

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
        in 80..82 -> "तेज़ बौछारें पड़ रही हैं"
        in 95..99 -> "आंधी-तूफान है"
        else -> "मौसम मिला-जुला है"
    }

    fun today(city: String): String {
        if (city.isBlank()) return "कौन से शहर का मौसम बताऊँ?"
        return try {
            val g = JSONObject(get(
                "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
                    URLEncoder.encode(city, "UTF-8")))
            val arr = g.optJSONArray("results")
            if (arr == null || arr.length() == 0) return "$city नाम की जगह नहीं मिली"
            val p = arr.getJSONObject(0)
            val lat = p.getDouble("latitude")
            val lon = p.getDouble("longitude")
            val name = p.optString("name", city)
            val w = JSONObject(get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=1"))
            val cur = w.getJSONObject("current")
            val day = w.getJSONObject("daily")
            val t = cur.getDouble("temperature_2m").roundToInt()
            val feels = cur.getDouble("apparent_temperature").roundToInt()
            val hum = cur.getDouble("relative_humidity_2m").roundToInt()
            val wind = cur.getDouble("wind_speed_10m").roundToInt()
            val d = desc(cur.getInt("weather_code"))
            val mx = day.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
            val mn = day.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
            val rain = day.getJSONArray("precipitation_probability_max").optInt(0)
            "${name} में अभी ${t} डिग्री है और ${d}। महसूस ${feels} डिग्री जैसा हो रहा है, उमस ${hum}% और हवा ${wind} किमी प्रति घंटा। आज कम से कम ${mn} और ज़्यादा से ज़्यादा ${mx} डिग्री रहेगा, बारिश की संभावना ${rain}%।"
        } catch (e: Exception) {
            "मौसम नहीं मिल पाया: ${e.message}"
        }
    }
}
