package com.example.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

object Weather {
    private class Place(val keys: List<String>, val name: String, val lat: Double, val lon: Double)

    // अंडमान-निकोबार की जगहें (अंदाज़न निर्देशांक), क्योंकि छोटे द्वीप खोज में अक्सर नहीं मिलते
    private val places = listOf(
        Place(listOf("little andaman", "hut bay", "लिटिल अंडमान", "हट बे"), "लिटिल अंडमान", 10.59, 92.55),
        Place(listOf("havelock", "swaraj dweep", "हैवलॉक", "स्वराज"), "हैवलॉक", 11.97, 92.99),
        Place(listOf("neil island", "shaheed dweep", "नील आइलैंड", "शहीद द्वीप"), "नील आइलैंड", 11.83, 93.03),
        Place(listOf("diglipur", "दिगलीपुर"), "दिगलीपुर", 13.27, 92.98),
        Place(listOf("mayabunder", "मायाबंदर"), "मायाबंदर", 12.92, 92.90),
        Place(listOf("rangat", "रंगत"), "रंगत", 12.50, 92.92),
        Place(listOf("baratang", "बाराटांग"), "बाराटांग", 12.05, 92.78),
        Place(listOf("wandoor", "वांडूर"), "वांडूर", 11.60, 92.62),
        Place(listOf("campbell bay", "कैंपबेल बे"), "कैंपबेल बे", 7.00, 93.92),
        Place(listOf("car nicobar", "कार निकोबार"), "कार निकोबार", 9.17, 92.82),
        Place(listOf("nancowry", "kamorta", "नानकौरी"), "नानकौरी", 8.03, 93.53)
    )

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
            val q = city.trim().lowercase()
            val known = places.firstOrNull { p -> p.keys.any { q.contains(it) } }
            val loc: Triple<Double, Double, String> = if (known != null) {
                Triple(known.lat, known.lon, known.name)
            } else {
                val g = JSONObject(get(
                    "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
                        URLEncoder.encode(city, "UTF-8")))
                val arr = g.optJSONArray("results")
                if (arr == null || arr.length() == 0) return "$city नाम की जगह नहीं मिली। पास का कोई बड़ा शहर या द्वीप बताओ"
                val p = arr.getJSONObject(0)
                Triple(p.getDouble("latitude"), p.getDouble("longitude"), p.optString("name", city))
            }
            val lat = loc.first
            val lon = loc.second
            val name = loc.third
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
