package com.example.assistant

import android.content.Context
import java.io.File
import java.net.InetAddress

/** Basic read-only health facts; no remote/network scan is performed here. */
object DefensiveHealth {
    fun report(context: Context): String {
        val root = File(context.filesDir.path)
        val stats = android.os.StatFs(root.path)
        return listOf(
            "App storage free: ${stats.availableBytes / (1024 * 1024)} MB",
            "Wi-Fi/network: ${try { val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager; cm.activeNetwork != null } catch (_: Exception) { false }}",
            "Approved scan targets: configured exact private IPs only; no scan run by health check."
        ).joinToString("\n")
    }
}
