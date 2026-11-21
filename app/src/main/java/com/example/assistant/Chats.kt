package com.example.assistant

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

// Har chat ek alag entry (Gemini / Gmail jaisa). Naya chat shuru karne par hi nayi entry banti hai, baaki baatein usi mein judti jaati hain.
object Chats {
    class C(val id: String, val auto: String, val time: Long, val log: String, val ct: String = "", val pin: Boolean = false) {
        val title: String get() = ct.ifBlank { auto }
    }

    private fun read(p: SharedPreferences): ArrayList<C> {
        val out = ArrayList<C>()
        try {
            val a = JSONArray(p.getString("chats", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val t = o.optLong("time")
                out.add(C(o.optString("id").ifBlank { "L$t" }, o.optString("t"), t, o.optString("log"), o.optString("ct"), o.optBoolean("pin")))
            }
        } catch (e: Exception) { }
        return out
    }

    private fun write(p: SharedPreferences, l: List<C>) {
        val a = JSONArray()
        for (c in l.sortedWith(compareByDescending<C> { it.pin }.thenByDescending { it.time }).take(60)) a.put(JSONObject().put("id", c.id).put("t", c.auto).put("ct", c.ct).put("pin", c.pin).put("time", c.time).put("log", c.log))
        p.edit().putString("chats", a.toString()).apply()
    }

    @Synchronized fun all(p: SharedPreferences): List<C> {
        var l = read(p)
        // ek baar: ek hi naam ki dohri entries mein se sabse poori rakho
        if (!p.getBoolean("chats_v3", false)) {
            val best = LinkedHashMap<String, C>()
            for (c in l.sortedByDescending { it.time }) {
                val k = c.auto.trim().lowercase()
                val old = best[k]
                if (old == null || c.log.length > old.log.length) best[k] = c
            }
            l = ArrayList(best.values)
            write(p, l)
            p.edit().putBoolean("chats_v3", true).apply()
        }
        return l.sortedWith(compareByDescending<C> { it.pin }.thenByDescending { it.time })
    }

    @Synchronized fun save(p: SharedPreferences, id: String, log: String) {
        val title = log.split(Regex("\n(?=(?:Tum|AI): )")).map { it.trim() }.firstOrNull { it.startsWith("Tum:") }
            ?.removePrefix("Tum:")?.replace(Regex("\u2060IMG:[^\u2060]*\u2060"), "")?.trim()?.take(40)?.ifBlank { null } ?: "Chat"
        // isi chat ki file badlo; bilkul ek jaisi dusri entry ho to hata do
        val old = read(p).firstOrNull { it.id == id }
        val l = read(p).filter { it.id != id && !(it.auto == title && it.log == log) }.toMutableList()
        l.add(C(id, title, System.currentTimeMillis(), log, old?.ct ?: "", old?.pin ?: false))
        write(p, l)
    }

    // ek chat ki poori file (nahi mili to null)
    @Synchronized fun get(p: SharedPreferences, id: String): C? = if (id.isEmpty()) null else read(p).firstOrNull { it.id == id }

    @Synchronized fun clearAll(p: SharedPreferences) {
        p.edit().putString("chats", "[]").apply()
    }

    @Synchronized fun rename(p: SharedPreferences, id: String, name: String) {
        write(p, read(p).map { if (it.id == id) C(it.id, it.auto, it.time, it.log, name.trim().take(60), it.pin) else it })
    }

    @Synchronized fun setPin(p: SharedPreferences, id: String, on: Boolean) {
        write(p, read(p).map { if (it.id == id) C(it.id, it.auto, it.time, it.log, it.ct, on) else it })
    }

    @Synchronized fun delete(p: SharedPreferences, id: String) { write(p, read(p).filter { it.id != id }) }

    fun cur(p: SharedPreferences): String = p.getString("cur_id", "") ?: ""
    fun setCur(p: SharedPreferences, id: String) { p.edit().putString("cur_id", id).apply() }
    fun newId(): String = System.currentTimeMillis().toString()
}
