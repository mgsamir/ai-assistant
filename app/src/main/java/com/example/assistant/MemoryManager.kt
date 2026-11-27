package com.example.assistant

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Bounded local memory; sensitive credential-looking text is never persisted. */
class MemoryManager(private val prefs: SharedPreferences) {
    data class Upgrade(
        val id: String,
        val title: String,
        val reason: String,
        val description: String,
        val risk: String,
        val location: String,
        val source: String,
        val status: String = "pending",
        val patchPath: String = "",
        val patchContent: String = ""
    )

    private val lock = Any()
    private val credential = Regex("(?i)(api[_-]?key|access[_-]?token|password|secret|gh[pousr]_[A-Za-z0-9_]{12,})\\s*[:=]\\s*[^\\s,;]+")

    fun remember(text: String, kind: String = "fact"): Boolean {
        if (text.isBlank() || text.length > 2000 || credential.containsMatchIn(text)) return false
        synchronized(lock) {
            val arr = readArray("assistant_memory")
            arr.put(JSONObject().put("kind", kind.take(40)).put("text", text.take(1200)).put("at", System.currentTimeMillis()))
            while (arr.length() > 100) arr.remove(0)
            prefs.edit().putString("assistant_memory", arr.toString()).apply()
        }
        return true
    }

    fun relevantContext(query: String, maxChars: Int = 1800): String = synchronized(lock) {
        val terms = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val scored = (0 until readArray("assistant_memory").length()).mapNotNull { i -> readArray("assistant_memory").optJSONObject(i) }
            .map { row -> terms.count { row.optString("text").lowercase().contains(it) } to row.optString("text") }
            .sortedByDescending { it.first }.filter { it.first > 0 }.take(8)
        val out = StringBuilder()
        for ((_, text) in scored) {
            if (out.length + text.length + 1 > maxChars.coerceAtLeast(0)) break
            if (out.isNotEmpty()) out.append('\n')
            out.append(text)
        }
        out.toString()
    }

    fun log(event: String, detail: String) {
        val safe = if (credential.containsMatchIn(detail)) "[credential-like value redacted]" else detail.take(800)
        synchronized(lock) {
            val arr = readArray("assistant_logs")
            arr.put(JSONObject().put("at", System.currentTimeMillis()).put("event", event.take(80)).put("detail", safe))
            while (arr.length() > 300) arr.remove(0)
            prefs.edit().putString("assistant_logs", arr.toString()).apply()
        }
    }

    fun logs(): List<JSONObject> = synchronized(lock) {
        val a = readArray("assistant_logs"); (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    fun pendingUpgrades(): List<Upgrade> = synchronized(lock) { readUpgrades().filter { it.status == "pending" } }
    fun upgradeHistory(): List<Upgrade> = synchronized(lock) { readUpgrades() }

    fun saveUpgrade(upgrade: Upgrade): Upgrade = synchronized(lock) {
        val all = readUpgrades().toMutableList()
        val existing = all.indexOfFirst { it.title.trim().equals(upgrade.title.trim(), true) && it.status == "pending" }
        val merged = if (existing >= 0) upgrade.copy(
            id = all[existing].id,
            source = if (all[existing].source == upgrade.source) upgrade.source else "dono",
            patchPath = upgrade.patchPath.ifBlank { all[existing].patchPath },
            patchContent = upgrade.patchContent.ifBlank { all[existing].patchContent }
        ) else upgrade.copy(id = upgrade.id.ifBlank { UUID.randomUUID().toString() })
        if (existing >= 0) all[existing] = merged else all.add(merged)
        writeUpgrades(all.takeLast(100)); merged
    }

    fun attachDraft(id: String, path: String, content: String): Upgrade? = synchronized(lock) {
        if (path.isBlank() || content.isBlank() || content.length > 120_000) return@synchronized null
        val all = readUpgrades().toMutableList(); val i = all.indexOfFirst { it.id == id && it.status == "pending" }
        if (i < 0) return@synchronized null
        all[i] = all[i].copy(patchPath = path, patchContent = content)
        writeUpgrades(all); all[i]
    }

    fun updateUpgrade(id: String, status: String): Upgrade? = synchronized(lock) {
        if (status !in setOf("approved", "denied")) return@synchronized null
        val all = readUpgrades().toMutableList(); val i = all.indexOfFirst { it.id == id }
        if (i < 0) return@synchronized null
        all[i] = all[i].copy(status = status); writeUpgrades(all); all[i]
    }

    private fun readArray(key: String) = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
    private fun readUpgrades(): List<Upgrade> {
        val a = readArray("pending_upgrades")
        return (0 until a.length()).mapNotNull { i ->
            try {
                val o = a.getJSONObject(i)
                Upgrade(o.optString("id"), o.optString("title"), o.optString("reason"), o.optString("description"),
                    o.optString("risk"), o.optString("location"), o.optString("source"), o.optString("status", "pending"),
                    o.optString("patchPath"), o.optString("patchContent"))
            } catch (_: Exception) { null }
        }
    }
    private fun writeUpgrades(items: List<Upgrade>) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("reason", it.reason)
            .put("description", it.description).put("risk", it.risk).put("location", it.location).put("source", it.source)
            .put("status", it.status).put("patchPath", it.patchPath).put("patchContent", it.patchContent)) }
        prefs.edit().putString("pending_upgrades", a.toString()).apply()
    }
}
