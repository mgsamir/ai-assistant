package com.example.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File

// Phone ki suraksha jaanch. Ye asli antivirus nahi hai: virus ke pakke fingerprint nahi, sirf shak wali nishaniyan pakadta hai
// (app ke code mein bhi, CodeScan.kt) (chhupa app, fake naam, accessibility, device admin, SMS/call ki ijazat...)
object Security {
    class Finding(val score: Int, val label: String, val pkg: String, val reasons: List<String>)

    private val STORES = setOf("com.android.vending", "com.sec.android.app.samsungapps", "com.xiaomi.mipicks",
        "com.xiaomi.market", "com.heytap.market", "com.vivo.appstore", "com.amazon.venezia", "com.google.android.feedback")

    // jaane-maane app: asli package. Naam yahi ho par package alag ho to fake hai
    private val REAL = mapOf(
        "whatsapp" to "com.whatsapp", "phonepe" to "com.phonepe.app", "paytm" to "net.one97.paytm",
        "google pay" to "com.google.android.apps.nbu.paisa.user", "gpay" to "com.google.android.apps.nbu.paisa.user",
        "instagram" to "com.instagram.android", "facebook" to "com.facebook.katana", "youtube" to "com.google.android.youtube",
        "telegram" to "org.telegram.messenger", "chrome" to "com.android.chrome", "gmail" to "com.google.android.gm",
        "truecaller" to "com.truecaller", "yono sbi" to "com.sbi.lotusintouch", "bhim" to "in.org.npci.upiapp",
        "amazon" to "in.amazon.mShop.android.shopping", "flipkart" to "com.flipkart.android", "snapchat" to "com.snapchat.android",
        "play store" to "com.android.vending", "google" to "com.google.android.googlequicksearchbox")

    private val SPY = listOf("mspy", "flexispy", "spyic", "hoverwatch", "cocospy", "thetruthspy", "spyzie", "eyezy",
        "xnspy", "spyera", "keylogger", "stalker", "spy phone", "phone spy", "hidden call recorder", "mobile tracker secret")

    private val SENS = listOf("READ_SMS", "RECEIVE_SMS", "READ_CALL_LOG", "RECORD_AUDIO", "CAMERA", "ACCESS_FINE_LOCATION",
        "READ_CONTACTS", "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES")

    @Suppress("DEPRECATION")
    private fun installer(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName else pm.getInstallerPackageName(pkg)
    } catch (e: Exception) { null }

    private fun enabledPkgs(c: Context, key: String): Set<String> = try {
        (Settings.Secure.getString(c.contentResolver, key) ?: "").split(":")
            .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }.toSet()
    } catch (e: Exception) { emptySet() }

    private fun admins(c: Context): Set<String> = try {
        val d = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        (d.activeAdmins ?: emptyList<ComponentName>()).map { it.packageName }.toSet()
    } catch (e: Exception) { emptySet() }

    // phone ke saath aaye / system ke app (update hue ho tab bhi) shak ki list mein nahi aate
    private fun isSystem(i: ApplicationInfo): Boolean {
        if ((i.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) return true
        val d = i.sourceDir ?: return false
        return listOf("/system", "/product", "/vendor", "/system_ext", "/apex", "/odm", "/my_").any { d.startsWith(it) }
    }

    // company ke apne naam wale app (phone ke saath aaye, par /data mein hote hain)
    private val OWN = listOf("com.miui.", "com.xiaomi.", "com.mi.", "com.google.android.", "com.android.",
        "com.qualcomm.", "com.microsoft.appmanager", "com.preff.", "android.")

    private fun hasLauncher(pm: PackageManager, pkg: String): Boolean =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), 0).isNotEmpty()

    @Suppress("DEPRECATION")
    private fun judge(c: Context, pm: PackageManager, pi: PackageInfo, acc: Set<String>, nl: Set<String>, ad: Set<String>): Finding? {
        val ai = pi.applicationInfo ?: return null
        val pkg = pi.packageName
        if (pkg == c.packageName || isSystem(ai)) return null
        if (try { pm.checkSignatures("android", pkg) == PackageManager.SIGNATURE_MATCH } catch (e: Exception) { false }) return null
        if (try { pm.checkSignatures(pkg, "android") == PackageManager.SIGNATURE_MATCH } catch (e: Exception) { false }) return null
        val label = ai.loadLabel(pm).toString()
        val low = label.lowercase()
        var s = 0
        val why = ArrayList<String>()
        val src = installer(pm, pkg)
        val trusted = src != null && src in STORES
        // phone ke saath aaye company ke app: halke nishaan (icon, install ka raasta) ginte hi nahi
        val soft = !(src == null && OWN.any { pkg.startsWith(it) }) && !trusted
        val perms = (pi.requestedPermissions ?: emptyArray()).map { it.substringAfterLast('.') }
        val sms = "READ_SMS" in perms || "RECEIVE_SMS" in perms
        val sens = SENS.count { it in perms }
        val risky = sms || sens >= 2 || pkg in acc || pkg in ad || pkg in nl
        if (soft) {
            s += 1; why.add("Play Store ke bahar se aaya (file se install)")
            if (!hasLauncher(pm, pkg) && risky) { s += 3; why.add("Icon chhupa hua hai, app list mein nahi dikhta") }
            if (sms) { s += 2; why.add("SMS padhne ki ijazat (OTP chura sakta hai)") }
            if (sens >= 4) { s += 2; why.add("Bahut saari nijee ijazatein maangta hai ($sens)") }
        } else if (trusted && sms) { s += 1; why.add("SMS padhne ki ijazat (OTP chura sakta hai)") }
        if (pkg in acc) { s += 4; why.add("Accessibility chalu hai, ye screen padh sakta hai aur khud tap kar sakta hai") }
        if (pkg in ad) { s += 4; why.add("Device admin bana hua hai, hatana mushkil kar sakta hai") }
        if (pkg in nl) { s += 2; why.add("Tumhare saare notification (OTP bhi) padh sakta hai") }
        for ((nm, real) in REAL) {
            if ((low == nm || low.replace(" ", "") == nm.replace(" ", "")) && pkg != real) {
                s += 5; why.add("Naam '$label' jaisa hai par asli app ka package nahi hai (fake ho sakta hai)")
                break
            }
        }
        if (SPY.any { low.contains(it) || pkg.lowercase().contains(it) }) { s += 5; why.add("Naam jasoosi (spy / tracking) app jaisa hai") }
        // code ke andar jhaanko: bahar se aaye ya pehle se shak wale app ke liye
        if (soft || pkg in acc || pkg in ad || pkg in nl) {
            val cs = CodeScan.inspect(c, pkg, ai.sourceDir, perms, pi.lastUpdateTime, pkg in acc)
            if (cs != null && cs.score > 0) { s += cs.score; why.addAll(cs.why) }
        }
        if (why.isEmpty()) return null
        return Finding(s, label, pkg, why)
    }

    @Suppress("DEPRECATION")
    fun scan(c: Context): List<Finding> {
        val pm = c.packageManager
        val acc = enabledPkgs(c, "enabled_accessibility_services")
        val nl = enabledPkgs(c, "enabled_notification_listeners")
        val ad = admins(c)
        val out = ArrayList<Finding>()
        CodeScan.deadline = System.currentTimeMillis() + 25000
        try {
            for (pi in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) judge(c, pm, pi, acc, nl, ad)?.let { out.add(it) }
        } catch (e: Exception) { }
        return out.sortedByDescending { it.score }
    }

    private fun rooted(): Boolean =
        (Build.TAGS ?: "").contains("test-keys") ||
            listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk").any { File(it).exists() }

    fun report(c: Context): String {
        val f = scan(c)
        val hi = f.filter { it.score >= 5 }
        val mid = f.filter { it.score in 3..4 }
        val sb = StringBuilder()
        if (hi.isEmpty() && mid.isEmpty()) sb.append("Jaanch poori: koi bada khatra nahi mila. ")
        if (hi.isNotEmpty()) {
            sb.append("ZYADA SHAK wale app:\n")
            hi.take(6).forEach { sb.append("• ${it.label} (${it.pkg}): ${it.reasons.joinToString("; ")}\n") }
        }
        if (mid.isNotEmpty()) {
            sb.append("\nThoda shak wale app:\n")
            mid.take(6).forEach { sb.append("• ${it.label}: ${it.reasons.joinToString("; ")}\n") }
        }
        if (rooted()) sb.append("\nPhone ke system mein badlaav (root / test-keys) dikh raha hai, ye khatarnak ho sakta hai.\n")
        sb.append("\nYe asli antivirus nahi hai. Sirf shak wali nishaniyan dekhta hai (app ke code mein bhi), virus ka pakka fingerprint nahi milata. ")
        sb.append("Shak wala app tum jaante ho to rehne do. Nahi jaante to Settings > Apps mein jaakar hata do, aur Play Store > Play Protect > Scan bhi chalao. ")
        sb.append("Phone ka data dark web par bika ya nahi, ye koi app phone se nahi bata sakta.")
        return sb.toString().trim()
    }

    // ---- history: kab kaun sa app khatarnak laga (Security log screen ke liye) ----
    class Entry(val t: Long, val pkg: String, val label: String, val score: Int, val fresh: Boolean, val text: String)

    private fun spoken(f: Finding): String =
        "${f.label} par shak hai. " + f.reasons.joinToString(". ") + ". Jaante ho to rehne do, nahi jaante to Settings se hata do."

    fun addLog(p: android.content.SharedPreferences, list: List<Finding>, fresh: Set<String>) {
        if (list.isEmpty()) return
        try {
            synchronized(this) {
                val a = org.json.JSONArray(p.getString("sec_log", "[]"))
                val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                val today = day.format(java.util.Date())
                val keep = ArrayList<org.json.JSONObject>()
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    // aaj usi app ki purani entry hata do, nayi daalenge
                    if (day.format(java.util.Date(o.getLong("t"))) == today && list.any { it.pkg == o.getString("pkg") }) continue
                    keep.add(o)
                }
                val now = System.currentTimeMillis()
                for (f in list) keep.add(org.json.JSONObject().put("t", now).put("pkg", f.pkg).put("label", f.label)
                    .put("score", f.score).put("fresh", f.pkg in fresh).put("text", spoken(f)))
                val out = org.json.JSONArray()
                keep.takeLast(300).forEach { out.put(it) }
                p.edit().putString("sec_log", out.toString()).apply()
            }
        } catch (e: Exception) { }
    }

    fun logs(p: android.content.SharedPreferences): List<Entry> = try {
        val a = org.json.JSONArray(p.getString("sec_log", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Entry(it.getLong("t"), it.getString("pkg"), it.getString("label"), it.optInt("score"), it.optBoolean("fresh"), it.getString("text"))
        }.sortedByDescending { it.t }
    } catch (e: Exception) { emptyList() }

    // App khulte hi apne aap jaanch: sirf tab bolta hai jab kuch shak wala mile
    // (zyada shak wale har baar, thode shak wale sirf pehli baar jab dikhe, ya naye aaye app)
    fun autoCheck(c: Context, p: android.content.SharedPreferences): String? {
        if (!p.getBoolean("sec_v2", false)) {
            p.edit().remove("sec_log").remove("sec_seen").remove("sec_known").putBoolean("sec_v2", true).apply()
        }
        if (p.getInt("sec_ver", 0) < 2) {
            // purani galat chetavniyan saaf
            p.edit().remove("sec_log").remove("sec_seen").remove("sec_known").putInt("sec_ver", 2).apply()
        }
        val f = scan(c)
        val pm = c.packageManager
        val known = p.getStringSet("sec_known", null)
        val now = try {
            pm.getInstalledPackages(0).filter { it.applicationInfo != null && !isSystem(it.applicationInfo!!) }.map { it.packageName }.toSet()
        } catch (e: Exception) { emptySet() }
        p.edit().putStringSet("sec_known", now).putLong("sec_last", System.currentTimeMillis()).apply()
        val fresh = if (known == null) emptySet() else now - known
        val seen = p.getStringSet("sec_seen", emptySet()) ?: emptySet()
        val show = f.filter { it.score >= 5 || (it.score >= 3 && (it.pkg !in seen || it.pkg in fresh)) }
        if (show.isEmpty()) return null
        p.edit().putStringSet("sec_seen", seen + show.map { it.pkg }).apply()
        addLog(p, show, fresh)
        val sb = StringBuilder()
        for (x in show.take(6)) {
            val tag = if (x.pkg in fresh) "NAYA APP, " else ""
            sb.append("• ${x.label}: $tag${x.reasons.joinToString("; ")}\n\n")
        }
        if (rooted()) sb.append("• Phone ke system mein badlaav (root / test-keys) dikh raha hai.\n\n")
        sb.append("Ye asli antivirus nahi hai, sirf shak ki nishaniyan hain. Jaante ho to rehne do, nahi jaante to Settings > Apps se hata do.")
        return sb.toString()
    }

    // naya app install hua: turant jaanch, shak ho to notification
    @Suppress("DEPRECATION")
    fun checkNew(c: Context, pkg: String): Finding? {
        return try {
            val pm = c.packageManager
            val pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            CodeScan.deadline = System.currentTimeMillis() + 20000
            judge(c, pm, pi, enabledPkgs(c, "enabled_accessibility_services"), enabledPkgs(c, "enabled_notification_listeners"), admins(c))
                ?.takeIf { it.score >= 3 }
        } catch (e: Exception) { null }
    }

    fun notify(c: Context, f: Finding) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
                NotificationChannel("sec", "Suraksha chetavni", NotificationManager.IMPORTANCE_HIGH))
            val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(c, "sec") else android.app.Notification.Builder(c)
            b.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Naye app par shak: ${f.label}")
                .setStyle(android.app.Notification.BigTextStyle().bigText(f.reasons.joinToString("\n")))
                .setContentText(f.reasons.first())
                .setAutoCancel(true)
            nm.notify(f.pkg.hashCode(), b.build())
        } catch (e: Exception) { }
    }
}
