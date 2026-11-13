package com.example.assistant

import android.content.SharedPreferences

// Awaaz chunne ka system: ladka / ladki ki alag alag awaazein.
// Har awaaz ke saath: Gemini Live ka naam, ElevenLabs ki awaaz, aur phone ki apni awaaz ke liye pitch.
object Voice {
    class V(val id: String, val name: String, val girl: Boolean, val note: String,
            val gemini: String, val eleven: String, val pitch: Float)

    val ALL = listOf(
        V("kore", "Kore", true, "Saaf aur pakki awaaz", "Kore", "21m00Tcm4TlvDq8ikWAM", 1.20f),
        V("aoede", "Aoede", true, "Halki aur hansti hui", "Aoede", "EXAVITQu4vr4xnSDxMaL", 1.28f),
        V("zephyr", "Zephyr", true, "Chamakdar aur tez", "Zephyr", "MF3mGyEYCl7XYWbV9V6O", 1.15f),
        V("leda", "Leda", true, "Jawaan aur seedhi", "Leda", "AZnzlk1XvdvUeBnXmlld", 1.24f),
        V("sulafat", "Sulafat", true, "Garam aur narm", "Sulafat", "21m00Tcm4TlvDq8ikWAM", 1.10f),
        V("puck", "Puck", false, "Zinda-dil aur halki", "Puck", "pNInz6obpgDQGcFmaJgB", 0.90f),
        V("charon", "Charon", false, "Gehri aur shaant", "Charon", "VR6AewLTigWG4xSOukaG", 0.75f),
        V("fenrir", "Fenrir", false, "Josheeli awaaz", "Fenrir", "TxGEqnHWrfWFTfGW9XjX", 0.85f),
        V("orus", "Orus", false, "Pakki aur bharosemand", "Orus", "ErXwobaYiN019PkySvjV", 0.80f),
        V("iapetus", "Iapetus", false, "Seedhi aur saaf", "Iapetus", "yoZ06aMxZJJ28mfd3POQ", 0.88f)
    )

    private const val DEF = "puck"

    fun cur(p: SharedPreferences?): V {
        val id = p?.getString("voice_id", DEF) ?: DEF
        return ALL.firstOrNull { it.id == id } ?: ALL.first { it.id == DEF }
    }

    fun set(p: SharedPreferences, id: String) { p.edit().putString("voice_id", id).apply() }
}
