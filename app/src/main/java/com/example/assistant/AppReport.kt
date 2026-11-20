package com.example.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

// Ek app ki poori jaankari ek text mein: copy karke kisi ko bhej sakte ho (asli antivirus nahi, sirf saboot dikhata hai)
object AppReport {
    private val NEEDLES = listOf(
        "api.telegram.org", "discord.com/api/webhooks", "discordapp.com/api/webhooks",
        "MediaProjection", "ImageReader", "VirtualDisplay", "sendTextMessage", "sendMultipartTextMessage",
        "TYPE_VIEW_TEXT_CHANGED", "dispatchGesture", "DexClassLoader", "PathClassLoader", "/system/bin/su", "/system/xbin/su",
        "wipeData", "lockNow", "VOICE_CALL", "PackageInstaller", "REQUEST_INSTALL_PACKAGES",
        "TYPE_APPLICATION_OVERLAY", "SYSTEM_ALERT_WINDOW", "getDeviceId", "getSubscriberId", "getLine1Number",
        "content://sms", "content://contacts", "content://call_log", "AudioRecord", "MediaRecorder", "Runtime.exec", "getRuntime")
    private val KEYS = listOf("record", "sms", "keylog", "overlay", "telegram", "inject", "stealer", "steal", "spy", "admin",
        "accessib", "payload", "dropper", "hook", "obfus", "crypt", "bank", "upi", "otp", "phish", "remote", "rat", "sniff", "capture")
    private const val MAX = 70_000

    private fun sha(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    fun build(c: Context, pkg: String): String {
        val pm = c.packageManager
        val sb = StringBuilder()
        fun h(t: String) { sb.append("\n== ").append(t).append(" ==\n") }
        val pi = try {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
        } catch (e: Exception) { return "This app is no longer installed: $pkg" }
        val ai = pi.applicationInfo
        val apk = ai?.sourceDir
        sb.append("APP REPORT (not an antivirus, evidence only)\n")
        sb.append("Naam: ").append(ai?.loadLabel(pm)).append("\nPackage: ").append(pkg)
        sb.append("\nVersion: ").append(pi.versionName).append("\nTarget SDK: ").append(ai?.targetSdkVersion)
        sb.append("\nInstall source: ").append(try { if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName else pm.getInstallerPackageName(pkg) } catch (e: Exception) { null } ?: "none (sideloaded)")
        val df = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ENGLISH)
        sb.append("\nInstall: ").append(df.format(java.util.Date(pi.firstInstallTime))).append("  Update: ").append(df.format(java.util.Date(pi.lastUpdateTime)))
        if (apk != null) {
            val f = File(apk)
            sb.append("\nAPK size: ").append(f.length() / 1024).append(" KB")
            try {
                val md = MessageDigest.getInstance("SHA-256")
                f.inputStream().use { ins -> val b = ByteArray(65536); while (true) { val n = ins.read(b); if (n < 0) break; md.update(b, 0, n) } }
                sb.append("\nAPK SHA-256: ").append(md.digest().joinToString("") { "%02x".format(it) })
            } catch (e: Exception) { }
        }
        try {
            val sigs = if (Build.VERSION.SDK_INT >= 28)
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            else pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
            sigs?.forEach { sb.append("\nSigning cert SHA-256: ").append(sha(it.toByteArray())) }
        } catch (e: Exception) { }
        val vt = VtScan.cached(c, pkg, pi.lastUpdateTime)
        sb.append("\nVirusTotal: ").append(if (vt == null) "not scanned yet" else if (!vt.known) "file not found on VirusTotal (new / unique)" else "${vt.mal} antivirus virus, ${vt.susp} shak" + (if (vt.label.isNotBlank()) " (${vt.label})" else ""))

        val p = c.getSharedPreferences("p", Context.MODE_PRIVATE)
        val log = Security.logs(p).firstOrNull { it.pkg == pkg }
        if (log != null) { h("Scanner findings"); sb.append(log.text.substringAfter(" — ")).append("\n") }

        h("Permissions (" + (pi.requestedPermissions?.size ?: 0) + ")")
        pi.requestedPermissions?.forEach { sb.append(it.removePrefix("android.permission.")).append("\n") }

        fun comp(name: String, arr: Array<out android.content.pm.ComponentInfo>?) {
            if (arr.isNullOrEmpty()) return
            h("$name (${arr.size})")
            arr.take(120).forEach { sb.append(it.name).append(if (it.exported) "  [exported]" else "").append("\n") }
        }
        comp("Services", pi.services); comp("Receivers", pi.receivers); comp("Activities", pi.activities); comp("Providers", pi.providers)

        if (apk != null) try {
            val hosts = LinkedHashMap<String, Int>()
            val classes = LinkedHashSet<String>()
            val pkgs = HashMap<String, Int>()
            val ev = StringBuilder()
            val files = StringBuilder()
            val libs = ArrayList<String>()
            var n = 0
            ZipFile(apk).use { z ->
                for (e in z.entries()) {
                    if (e.name.startsWith("lib/")) libs.add(e.name)
                    if (n < 250 && !e.isDirectory && (e.name.startsWith("assets/") || e.name.startsWith("res/raw/") || e.name.endsWith(".dex") || e.name.endsWith(".so") == false && !e.name.startsWith("res/"))) {
                        files.append(e.name).append("  ").append(e.size / 1024).append(" KB")
                        if (e.name.endsWith(".apk") || e.name.endsWith(".dex") || e.name.endsWith(".jar")) files.append("  <-- andar chhupa code")
                        files.append("\n"); n++
                    }
                }
                val urlRe = Regex("https?://[A-Za-z0-9._~:/?#@!$&'()*+,;=%-]{4,110}")
                val clsRe = Regex("L[a-zA-Z0-9_/$]{4,110};")
                var total = 0L
                for (e in z.entries()) {
                    if (!(e.name.startsWith("classes") && e.name.endsWith(".dex"))) continue
                    if (e.size > 30_000_000L || total > 60_000_000L) continue
                    total += e.size
                    val t = String(z.getInputStream(e).use { it.readBytes() }, Charsets.ISO_8859_1)
                    for (nd in NEEDLES) {
                        val i = t.indexOf(nd)
                        if (i >= 0 && ev.length < 14000) {
                            val s = t.substring(maxOf(0, i - 70), minOf(t.length, i + nd.length + 90)).map { if (it.code in 32..126) it else '.' }.joinToString("")
                            ev.append("[").append(nd).append("] (").append(e.name).append(")\n  ").append(s).append("\n")
                        }
                    }
                    for (m in urlRe.findAll(t)) { val u = m.value; val host = u.substringAfter("://").substringBefore("/").substringBefore(":"); if (host.contains('.')) hosts[host] = (hosts[host] ?: 0) + 1 }
                    for (m in clsRe.findAll(t)) {
                        val cn = m.value.drop(1).dropLast(1).replace('/', '.')
                        val pp = cn.split('.').dropLast(1).take(3).joinToString(".")
                        pkgs[pp] = (pkgs[pp] ?: 0) + 1
                        if (classes.size < 160 && KEYS.any { cn.lowercase().contains(it) }) classes.add(cn)
                    }
                }
            }
            if (ev.isNotEmpty()) { h("Code evidence (strings found inside dex)"); sb.append(ev) }
            if (classes.isNotEmpty()) { h("Suspiciously named classes"); classes.forEach { sb.append(it).append("\n") } }
            if (hosts.isNotEmpty()) { h("Websites referenced in code"); hosts.entries.sortedByDescending { it.value }.take(80).forEach { sb.append(it.key).append("  x").append(it.value).append("\n") } }
            if (pkgs.isNotEmpty()) { h("Code by library / package (class count)"); pkgs.entries.sortedByDescending { it.value }.take(45).forEach { sb.append(it.key).append("  ").append(it.value).append("\n") } }
            if (libs.isNotEmpty()) { h("Native libs"); libs.distinct().take(60).forEach { sb.append(it).append("\n") } }
            if (files.isNotEmpty()) { h("APK files"); sb.append(files) }
        } catch (e: Throwable) { sb.append("\n(Couldn't read APK: ").append(e.message).append(")\n") }

        var out = sb.toString()
        if (out.length > MAX) out = out.take(MAX) + "\n...(chhota kiya)"
        return out
    }
}
