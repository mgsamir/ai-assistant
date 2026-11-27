package com.example.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.StatFs
import java.io.File
import java.util.concurrent.TimeUnit

/** Read-only Android inventory plus explicitly gated local commands and existing Lab bridge. */
class SystemRunner(private val context: Context, private val prefs: android.content.SharedPreferences, private val memory: MemoryManager) {
    data class Result(val ok: Boolean, val output: String, val level: SecurityManager.Level)

    fun battery(): Result {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return Result(true, "Battery: $pct%; charging=${status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL}", SecurityManager.Level.SAFE)
    }
    fun network(): Result {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return Result(true, "Network: offline", SecurityManager.Level.SAFE)
        val caps = cm.getNetworkCapabilities(n)
        val kind = when { caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"; caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"; else -> "other" }
        return Result(true, "Network: $kind; validated=${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true}", SecurityManager.Level.SAFE)
    }
    fun storage(): Result {
        val stat = StatFs(context.filesDir.path); val total = stat.totalBytes; val free = stat.availableBytes
        return Result(true, "App storage: ${format(free)} free of ${format(total)}", SecurityManager.Level.SAFE)
    }
    fun apps(): Result {
        val names = context.packageManager.getInstalledApplications(0).map { context.packageManager.getApplicationLabel(it).toString() }.distinct().sorted().take(250)
        return Result(true, names.joinToString("\n"), SecurityManager.Level.SAFE)
    }
    fun listDownloadFiles(): Result {
        val dir = File("/sdcard/Download")
        if (!SecurityManager.isAllowedPath(dir.path, SecurityManager.allowedRoots(context))) return Result(false, "Download folder is unavailable or not approved.", SecurityManager.Level.BLOCKED)
        return Result(true, (dir.listFiles()?.take(200)?.joinToString("\n") { it.name } ?: "No files / permission unavailable").take(8000), SecurityManager.Level.SAFE)
    }

    fun runLocal(command: String, confirmed: Boolean = false): Result {
        val decision = SecurityManager.decide(command, context, prefs, confirmed)
        memory.log("command_${decision.level.name.lowercase()}", command)
        if (decision.level == SecurityManager.Level.BLOCKED || decision.level == SecurityManager.Level.CONFIRM) return Result(false, decision.reason, decision.level)
        if (decision.command.size == 1) return when (decision.command[0]) { "battery" -> battery(); "wifi" -> network(); "storage" -> storage(); "apps" -> apps(); else -> runProcess(decision.command) }
        return runProcess(decision.command)
    }
    fun runPc(command: String, confirmed: Boolean = false): Result {
        val decision = SecurityManager.decide(command, context, prefs, confirmed)
        memory.log("pc_command_${decision.level.name.lowercase()}", command)
        if (decision.level != SecurityManager.Level.SAFE) return Result(false, decision.reason, decision.level)
        val response = Lab.runCommand(prefs, command)
        return Result(!response.startsWith("Error:") && !response.startsWith("Lab backend"), response.take(8000), decision.level)
    }
    private fun runProcess(args: List<String>): Result {
        return try {
            val process = ProcessBuilder(args).redirectErrorStream(true).start()
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) { process.destroyForcibly(); return Result(false, "Command timed out.", SecurityManager.Level.SAFE) }
            val output = process.inputStream.bufferedReader().use { it.readText().take(8000) }
            Result(process.exitValue() == 0, output, SecurityManager.Level.SAFE)
        } catch (e: Exception) { Result(false, "Command unavailable: ${e.javaClass.simpleName}", SecurityManager.Level.SAFE) }
    }
    private fun format(bytes: Long): String = "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
