package com.example.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

// text = स्क्रीन के लिए (Roman Hinglish), voice = आवाज़ के लिए (देवनागरी)
class Reply(val text: String, val voice: String)

object Weather {
    private class Place(
        val keys: List<String>,
        val en: String,
        val hi: String,
        val lat: Double,
        val lon: Double
    )

    // अंडमान-निकोबार की जगहें (अंदाज़न निर्देशांक), क्योंकि छोटे द्वीप खोज में अक्सर नहीं मिलते
    private val places = listOf(
        Place(listOf("little andaman", "hut bay", "लिटिल अंडमान", "हट बे"), "Little Andaman", "लिटिल अंडमान", 10.59, 92.55),
        Place(listOf("port blair", "sri vijaya puram", "पोर्ट ब्लेयर", "श्री विजयपुरम"), "Port Blair", "पोर्ट ब्लेयर", 11.62, 92.73),
        Place(listOf("havelock", "swaraj dweep", "हैवलॉक", "स्वराज"), "Havelock", "हैवलॉक", 11.97, 92.99),
        Place(listOf("neil island", "shaheed dweep", "नील आइलैंड", "शहीद द्वीप"), "Neil Island", "नील आइलैंड", 11.83, 93.03),
        Place(listOf("diglipur", "दिगलीपुर"), "Diglipur", "दिगलीपुर", 13.27, 92.98),
        Place(listOf("mayabunder", "मायाबंदर"), "Mayabunder", "मायाबंदर", 12.92, 92.90),
        Place(listOf("rangat", "रंगत"), "Rangat", "रंगत", 12.50, 92.92),
        Place(listOf("baratang", "बाराटांग"), "Baratang", "बाराटांग", 12.05, 92.78),
        Place(listOf("wandoor", "वांडूर"), "Wandoor", "वांडूर", 11.60, 92.62),
        Place(listOf("campbell bay", "कैंपबेल बे"), "Campbell Bay", "कैंपबेल बे", 7.00, 93.92),
        Place(listOf("car nicobar", "कार निकोबार"), "Car Nicobar", "कार निकोबार", 9.17, 92.82),
        Place(listOf("nancowry", "kamorta", "नानकौरी"), "Nancowry", "नानकौरी", 8.03, 93.53)
    )

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        return c.inputStream.bufferedReader().readText()
    }

    private fun desc(code: Int): Pair<String, String> = when (code) {
        0 -> Pair("aasman saaf hai", "आसमान साफ है")
        1, 2 -> Pair("halke badal hain", "हल्के बादल हैं")
        3 -> Pair("badal chhaye hain", "बादल छाए हैं")
        45, 48 -> Pair("kohra hai", "कोहरा है")
        in 51..57 -> Pair("boondabaandi ho rahi hai", "बूंदाबांदी हो रही है")
        in 61..67 -> Pair("baarish ho rahi hai", "बारिश हो रही है")
        in 71..77 -> Pair("barfbaari ho rahi hai", "बर्फबारी हो रही है")
        in 80..82 -> Pair("tez bauchhaaren pad rahi hain", "तेज़ बौछारें पड़ रही हैं")
        in 95..99 -> Pair("aandhi-toofan hai", "आंधी-तूफान है")
        else -> Pair("mausam mila-jula hai", "मौसम मिला-जुला है")
    }

    fun today(city: String): Reply {
        if (city.isBlank()) {
            return Reply("Kaun se shehar ka mausam bataoon?", "कौन से शहर का मौसम बताऊँ?")
        }
        return try {
            val q = city.trim().lowercase()
            val known = places.firstOrNull { p -> p.keys.any { q.contains(it) } }
            var lat = 0.0
            var lon = 0.0
            var nameEn = city
            var nameHi = city
            if (known != null) {
                lat = known.lat
                lon = known.lon
                nameEn = known.en
                nameHi = known.hi
            } else {
                val g = JSONObject(get(
                    "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
                        URLEncoder.encode(city, "UTF-8")))
                val arr = g.optJSONArray("results")
                if (arr == null || arr.length() == 0) {
                    return Reply(
                        "$city naam ki jagah nahi mili. Paas ka koi bada shehar ya dweep batao",
                        "$city नाम की जगह नहीं मिली। पास का कोई बड़ा शहर या द्वीप बताओ")
                }
                val p = arr.getJSONObject(0)
                lat = p.getDouble("latitude")
                lon = p.getDouble("longitude")
                nameEn = p.optString("name", city)
                nameHi = nameEn
            }
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
            Reply(
                "$nameEn mein abhi $t degree hai aur ${d.first}. Mehsoos $feels degree jaisa ho raha hai, umas $hum% aur hawa $wind km/h. " +
                    "Aaj kam se kam $mn aur zyada se zyada $mx degree rahega, baarish ki sambhavna $rain%.",
                "$nameHi में अभी $t डिग्री है और ${d.second}। महसूस $feels डिग्री जैसा हो रहा है, उमस $hum% और हवा $wind किमी प्रति घंटा। " +
                    "आज कम से कम $mn और ज़्यादा से ज़्यादा $mx डिग्री रहेगा, बारिश की संभावना $rain%।")
        } catch (e: Exception) {
            Reply("Couldn't get the weather. Check your internet connection.", "Couldn't get the weather. Check your internet connection.")
        }
    }
}
