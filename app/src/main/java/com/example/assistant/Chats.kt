package com.example.assistant

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

// Har chat ek alag entry (Gemini / Gmail jaisa). Naya chat shuru karne par hi nayi entry banti hai, baaki baatein usi mein judti jaati hain.
object Chats {
    class C(val id: String, val title: String, val time: Long, val log: String)

    private fun read(p: SharedPreferences): ArrayList<C> {
        val out = ArrayList<C>()
        try {
            val a = JSONArray(p.getString("chats", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val t = o.optLong("time")
                out.add(C(o.optString("id").ifBlank { "L$t" }, o.optString("t"), t, o.optString("log")))
            }
        } catch (e: Exception) { }
        return out
    }

    private fun write(p: SharedPreferences, l: List<C>) {
        val a = JSONArray()
        for (c in l.sortedByDescending { it.time }.take(60)) a.put(JSONObject().put("id", c.id).put("t", c.title).put("time", c.time).put("log", c.log))
        p.edit().putString("chats", a.toString()).apply()
    }

    @Synchronized fun all(p: SharedPreferences): List<C> {
        var l = read(p)
        // ek baar: ek hi naam ki dohri entries mein se sabse poori rakho
        if (!p.getBoolean("chats_v2", false)) {
            val best = LinkedHashMap<String, C>()
            for (c in l.sortedByDescending { it.time }) {
                val k = c.title.trim().lowercase()
                val old = best[k]
                if (old == null || c.log.length > old.log.length) best[k] = c
            }
            l = ArrayList(best.values)
            write(p, l)
            p.edit().putBoolean("chats_v2", true).apply()
        }
        return l.sortedByDescending { it.time }
    }

    @Synchronized fun save(p: SharedPreferences, id: String, log: String) {
        val title = log.split(Regex("\n(?=(?:Tum|AI): )")).map { it.trim() }.firstOrNull { it.startsWith("Tum:") }
            ?.removePrefix("Tum:")?.trim()?.take(40)?.ifBlank { null } ?: "Chat"
        val l = read(p).filter { it.id != id }.toMutableList()
        l.add(C(id, title, System.currentTimeMillis(), log))
        write(p, l)
    }

    @Synchronized fun delete(p: SharedPreferences, id: String) { write(p, read(p).filter { it.id != id }) }

    fun cur(p: SharedPreferences): String = p.getString("cur_id", "") ?: ""
    fun setCur(p: SharedPreferences, id: String) { p.edit().putString("cur_id", id).apply() }
    fun newId(): String = System.currentTimeMillis().toString()
}
