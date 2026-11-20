package com.example.assistant

import android.content.SharedPreferences
import org.json.JSONArray

// Awaaz chunne ka system: ladka / ladki ki alag alag awaazein.
// Har awaaz ke saath: Gemini Live ka naam, ElevenLabs ki awaaz, aur phone ki apni awaaz ke liye pitch.
object Voice {
    class V(val id: String, val name: String, val girl: Boolean, val note: String,
            val gemini: String, val eleven: String, val pitch: Float)

    val ALL = listOf(
        V("kore", "Kore", true, "Clear and steady", "Kore", "21m00Tcm4TlvDq8ikWAM", 1.20f),
        V("aoede", "Aoede", true, "Light and cheerful", "Aoede", "EXAVITQu4vr4xnSDxMaL", 1.28f),
        V("zephyr", "Zephyr", true, "Bright and energetic", "Zephyr", "MF3mGyEYCl7XYWbV9V6O", 1.15f),
        V("leda", "Leda", true, "Youthful and direct", "Leda", "AZnzlk1XvdvUeBnXmlld", 1.24f),
        V("sulafat", "Sulafat", true, "Warm and soft", "Sulafat", "21m00Tcm4TlvDq8ikWAM", 1.10f),
        V("puck", "Puck", false, "Lively and light", "Puck", "pNInz6obpgDQGcFmaJgB", 0.90f),
        V("charon", "Charon", false, "Deep and calm", "Charon", "VR6AewLTigWG4xSOukaG", 0.75f),
        V("fenrir", "Fenrir", false, "Josheeli awaaz", "Fenrir", "TxGEqnHWrfWFTfGW9XjX", 0.85f),
        V("orus", "Orus", false, "Firm and reliable", "Orus", "ErXwobaYiN019PkySvjV", 0.80f),
        V("iapetus", "Iapetus", false, "Straight and clear", "Iapetus", "yoZ06aMxZJJ28mfd3POQ", 0.88f)
    )

    private const val DEF = "puck"

    fun cur(p: SharedPreferences?): V {
        val id = p?.getString("voice_id", DEF) ?: DEF
        return ALL.firstOrNull { it.id == id } ?: ALL.first { it.id == DEF }
    }

    fun set(p: SharedPreferences, id: String) { p.edit().putString("voice_id", id).apply() }

    // ElevenLabs: is account ki asli awaazon mein se wahi jinka gender mile; gender ke andar apni jagah ke hisaab se alag awaaz
    fun elevenFor(p: SharedPreferences?, v: V): String {
        try {
            val a = JSONArray(p?.getString("el_voices", "[]") ?: "[]")
            val ids = (0 until a.length()).map { a.getJSONObject(it) }
                .filter { (it.optString("g") == "female") == v.girl }.map { it.getString("id") }
            if (ids.isNotEmpty()) return ids[ALL.filter { it.girl == v.girl }.indexOf(v) % ids.size]
        } catch (e: Exception) { }
        return v.eleven
    }
}
