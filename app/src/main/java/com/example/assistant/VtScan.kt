package com.example.assistant

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// VirusTotal: app ki file ka SHA-256 "fingerprint" bhejkar poochhta hai ki ye pehle se virus ke roop mein jaana gaya hai ya nahi.
// Sirf fingerprint jaata hai, app ki file ya tumhara koi data nahi jaata. Free key: 4 jaanch / minute, 500 / din.
object VtScan {
    class R(val mal: Int, val susp: Int, val known: Boolean, val label: String)

    private val queue = LinkedHashMap<String, Triple<String, Long, String>>()   // pkg -> (apk, update, label)
    @Volatile private var worker: Thread? = null
    @Volatile private var stopUntil = 0L

    private fun parse(s: String?, update: Long): R? {
        if (s == null || !s.startsWith("$update|")) return null
        val p = s.split("|", limit = 6)
        if (p.size < 6) return null
        return R(p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0, p[4] == "1", p[5])
    }

    fun cached(c: Context, pkg: String, update: Long): R? =
        parse(c.getSharedPreferences("p", Context.MODE_PRIVATE).getString("vt_$pkg", null), update)

    private fun sha256(f: File): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { null }

    // jaanch ki line mein daalo; ek peechhe ek (16 second ke fark se, free limit ke andar)
    fun enqueue(c: Context, pkg: String, apk: String?, update: Long, label: String) {
        if (Keys.vt.isEmpty() || apk == null) return
        if (cached(c, pkg, update) != null) return
        synchronized(queue) { queue[pkg] = Triple(apk, update, label) }
        if (worker?.isAlive == true) return
        val app = c.applicationContext
        worker = Thread {
            try {
                while (true) {
                    val e = synchronized(queue) { queue.entries.firstOrNull() } ?: break
                    synchronized(queue) { queue.remove(e.key) }
                    if (System.currentTimeMillis() < stopUntil) break
                    val res = lookup(File(e.value.first))
                    if (res == null) { try { Thread.sleep(16000) } catch (x: InterruptedException) { break }; continue }
                    app.getSharedPreferences("p", Context.MODE_PRIVATE).edit()
                        .putString("vt_${e.key}", "${e.value.second}|x|${res.mal}|${res.susp}|${if (res.known) 1 else 0}|${res.label}").apply()
                    if (res.mal > 0 || res.susp > 2) {
                        val f = Security.checkNew(app, e.key)
                        if (f != null) {
                            Security.notify(app, f)
                            Security.addLog(app.getSharedPreferences("p", Context.MODE_PRIVATE), listOf(f), emptySet())
                        }
                    }
                    try { Thread.sleep(16000) } catch (x: InterruptedException) { break }
                }
            } catch (t: Throwable) { }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun lookup(f: File): R? {
        val h = sha256(f) ?: return null
        return try {
            val c = URL("https://www.virustotal.com/api/v3/files/$h").openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("x-apikey", Keys.vt)
            c.setRequestProperty("accept", "application/json")
            when (c.responseCode) {
                200 -> {
                    val a = JSONObject(c.inputStream.bufferedReader().readText()).getJSONObject("data").getJSONObject("attributes")
                    val st = a.optJSONObject("last_analysis_stats")
                    val label = a.optJSONObject("popular_threat_classification")?.optString("suggested_threat_label", "") ?: ""
                    R(st?.optInt("malicious") ?: 0, st?.optInt("suspicious") ?: 0, true, label)
                }
                404 -> R(0, 0, false, "")          // VirusTotal ne ye file kabhi dekhi nahi
                429 -> { stopUntil = System.currentTimeMillis() + 60 * 60 * 1000L; null }   // limit khatam: ek ghante ruko
                401, 403 -> { stopUntil = System.currentTimeMillis() + 24 * 3600 * 1000L; null }
                else -> null
            }
        } catch (e: Exception) { null }
    }
}
