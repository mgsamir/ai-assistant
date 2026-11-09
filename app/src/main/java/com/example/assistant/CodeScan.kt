package com.example.assistant

import android.content.Context
import java.util.zip.ZipFile

// App ke code (APK ke andar ki classes.dex) mein shak wali nishaniyan dhoondhta hai.
// Ye asli antivirus nahi: virus ke pakke "fingerprint" nahi, sirf aise tareeke dhoondhta hai jo chori / jasoosi wale app use karte hain.
object CodeScan {
    class R(val score: Int, val why: List<String>)

    @Volatile var deadline = 0L

    private val BANKS = listOf("com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user", "in.org.npci.upiapp",
        "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus", "com.snapwork.hdfc", "com.csam.icici.bank.imobile", "com.axis.mobile",
        "com.msf.kbank.mobile", "com.infrasofttech.centralbankofindia", "com.canarabank.mobile", "com.bankofbaroda", "com.pnb.",
        "com.freecharge.android", "com.mobikwik_new", "com.dreamplug.androidapp")

    private fun parse(s: String): R? {
        val p = s.split("|", limit = 3)
        if (p.size < 3) return null
        return R(p[1].toIntOrNull() ?: 0, if (p[2].isBlank()) emptyList() else p[2].split("§"))
    }

    // Pehle ki jaanch ka nateeja yaad rakhta hai (app update na hua ho to dobara nahi padhta)
    fun inspect(c: Context, pkg: String, apk: String?, perms: List<String>, update: Long, accOn: Boolean): R? {
        if (apk == null) return null
        val sp = c.getSharedPreferences("p", Context.MODE_PRIVATE)
        val key = "cs_$pkg"
        val old = sp.getString(key, null)
        if (old != null && old.startsWith("$update|")) return parse(old)
        if (System.currentTimeMillis() > deadline) return null   // is baar waqt khatam, agli baar
        val res = try { run(apk, perms, accOn) } catch (e: Throwable) { return null }
        sp.edit().putString(key, "$update|${res.score}|" + res.why.joinToString("§")).apply()
        return res
    }

    private fun run(apk: String, perms: List<String>, accOn: Boolean): R {
        val flags = HashSet<String>()
        var embedded = false
        var total = 0L
        ZipFile(apk).use { z ->
            val en = z.entries()
            while (en.hasMoreElements()) {
                val e = en.nextElement()
                val n = e.name
                if ((n.startsWith("assets/") || n.startsWith("res/raw/")) && (n.endsWith(".apk") || n.endsWith(".dex") || n.endsWith(".jar"))) embedded = true
                if (n.startsWith("classes") && n.endsWith(".dex") && total < 60_000_000L) {
                    if (e.size > 30_000_000L) continue
                    total += e.size
                    val bytes = z.getInputStream(e).use { it.readBytes() }
                    val t = String(bytes, Charsets.ISO_8859_1)
                    fun has(s: String) = t.contains(s)
                    if (has("api.telegram.org")) flags.add("tg")
                    if (has("discord.com/api/webhooks") || has("discordapp.com/api/webhooks")) flags.add("dc")
                    for (b in BANKS) if (has(b)) flags.add("bank:$b")
                    if (has("TYPE_APPLICATION_OVERLAY") || has("SYSTEM_ALERT_WINDOW")) flags.add("overlay")
                    if (has("sendTextMessage") || has("sendMultipartTextMessage")) flags.add("smssend")
                    if (has("DexClassLoader")) flags.add("dyn")
                    if (has("/system/bin/su") || has("/system/xbin/su")) flags.add("su")
                    if (has("MediaProjection") && has("ImageReader")) flags.add("screen")
                    if (has("TYPE_VIEW_TEXT_CHANGED") && has("onAccessibilityEvent")) flags.add("keylog")
                    if (has("wipeData") || has("lockNow")) flags.add("admin")
                }
            }
        }
        var s = 0
        val why = ArrayList<String>()
        if ("tg" in flags) { s += 3; why.add("Code mein Telegram bot ka link hai (chori ka data bhejne ka aam raasta)") }
        if ("dc" in flags) { s += 3; why.add("Code mein Discord webhook ka link hai (chori ka data bhejne ka raasta)") }
        val banks = flags.count { it.startsWith("bank:") }
        if (banks >= 4) {
            s += 3; why.add("Code mein kai bank / UPI apps ke naam hain")
            if ("overlay" in flags) { s += 1; why.add("Dusre app ke upar nakli screen dikha sakta hai (fake login)") }
        }
        if ("smssend" in flags && ("READ_SMS" in perms || "RECEIVE_SMS" in perms)) { s += 3; why.add("SMS padhkar aage SMS bhej bhi sakta hai (OTP chori)") }
        if (embedded) { s += 3; why.add("Andar dusra chhupa app / code rakha hai") }
        if ("dyn" in flags) { s += 2; why.add("Baad mein chhupa code chalane ka tareeka maujood hai") }
        if ("keylog" in flags && accOn) { s += 2; why.add("Keyboard mein likha text pakadne ka code hai") }
        if ("screen" in flags && "overlay" in flags) { s += 2; why.add("Screen record karne ka code hai") }
        if ("admin" in flags) { s += 2; why.add("Phone lock / saaf karne ka code hai") }
        if ("su" in flags) { s += 1; why.add("Root (su) commands ka zikr hai") }
        // ek akeli chhoti nishani (jaise sirf ad SDK) ko gino hi nahi
        return if (s >= 3) R(minOf(s, 7), why) else R(0, emptyList())
    }
}
