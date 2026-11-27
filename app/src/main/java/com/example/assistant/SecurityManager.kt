package com.example.assistant

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

/** Local policy gate. Unknown actions are BLOCKED; learning never changes this policy. */
object SecurityManager {
    enum class Level { SAFE, CONFIRM, BLOCKED }
    data class Decision(val level: Level, val reason: String, val command: List<String> = emptyList())

    private val shellMeta = setOf(';', '&', '|', '`', '$', '(', ')', '<', '>', '\n', '\r')
    private val safeCommands = setOf("ls", "df", "uptime", "ss", "ping", "traceroute", "nmap", "cat", "head", "tail", "grep", "stat", "du", "file", "wc")
    private val confirmCommands = setOf("pm", "am", "kill", "cp", "mv", "mkdir")
    private val pathCommands = setOf("ls", "cat", "head", "tail", "grep", "stat", "du", "file", "wc")
    private val secretNames = setOf(".ssh", ".env", "id_rsa", "id_ed25519", "id_ecdsa", "id_dsa", "authorized_keys", "private.key")

    fun allowedRoots(context: android.content.Context): List<String> = listOf(
        File("/sdcard/Download"), File("/storage/emulated/0/Download"), context.filesDir, context.cacheDir
    ).map { it.canonicalPath }.distinct()

    /** Exact hosts only; CIDRs/wildcards are deliberately not accepted as scan targets. */
    fun targetsAllowlist(prefs: android.content.SharedPreferences): Set<String> = buildSet {
        add("127.0.0.1")
        add("::1")
        (prefs.getString("security_approved_ips", "") ?: "").split(',', '\n', ' ', '\t')
            .map(String::trim).filter(String::isNotEmpty).forEach { candidate ->
                val ip = parseIp(candidate)
                if (ip != null && ip.isSiteLocalAddress && !ip.isLoopbackAddress && !ip.isAnyLocalAddress) add(ip.hostAddress.substringBefore('%'))
            }
    }

    fun decide(input: String, context: android.content.Context? = null, prefs: android.content.SharedPreferences? = null, confirmed: Boolean = false): Decision {
        if (input.isBlank() || input.length > 512 || input.any { it in shellMeta }) return Decision(Level.BLOCKED, "Empty/oversized input or shell syntax is not allowed.")
        val args = tokenize(input) ?: return Decision(Level.BLOCKED, "Command could not be parsed safely.")
        if (args.isEmpty()) return Decision(Level.BLOCKED, "Empty command.")
        val executable = args[0].substringAfterLast('/')
        if (executable in setOf("rm", "sudo", "su", "mkfs", "dd", "sh", "bash", "busybox", "chmod", "chown", "reboot", "shutdown")) {
            return Decision(Level.BLOCKED, "This command is never allowed.")
        }
        if (executable in setOf("battery", "wifi", "storage", "apps")) return Decision(Level.SAFE, "Read-only Android system query.", listOf(executable))
        if (executable !in safeCommands && executable !in confirmCommands) return Decision(Level.BLOCKED, "Command is not on the explicit allowlist.")
        if (executable in confirmCommands && !confirmed) return Decision(Level.CONFIRM, "User confirmation is required before this change.", args)
        if (executable in confirmCommands && args.drop(1).any { it.equals("-rf", true) || it.contains("/system") || it.contains("/etc") }) return Decision(Level.BLOCKED, "Destructive or system path operation is blocked.")
        if (executable in pathCommands) {
            if (context == null) return Decision(Level.BLOCKED, "A trusted app context is required to validate file paths.")
            val paths = pathOperands(executable, args.drop(1))
            if (paths.any { !isAllowedPath(it, allowedRoots(context)) }) return Decision(Level.BLOCKED, "Path is outside approved app/Download roots or is sensitive.")
        }
        if (executable in setOf("ping", "traceroute", "nmap")) {
            val target = args.drop(1).lastOrNull { !it.startsWith("-") } ?: return Decision(Level.BLOCKED, "An exact approved IP target is required.")
            val ip = parseIp(target) ?: return Decision(Level.BLOCKED, "Network targets must be literal IP addresses; hostnames are blocked.")
            val allowed = prefs?.let(::targetsAllowlist) ?: setOf("127.0.0.1", "::1")
            if (ip.hostAddress.substringBefore('%') !in allowed) return Decision(Level.BLOCKED, "Target is not an exact user-approved private IP.")
            if (executable == "nmap" && args.drop(1).any { it.startsWith("-") && it !in setOf("-sn", "-n") }) return Decision(Level.BLOCKED, "Only a limited host-presence check is permitted.")
        }
        return Decision(Level.SAFE, "Read-only allowlisted command.", args)
    }

    fun isAllowedPath(path: String, roots: List<String>): Boolean {
        if (path == "-") return true
        val expanded = if (path == "~") System.getProperty("user.home") else path.replaceFirst("~/", System.getProperty("user.home") + File.separator)
        val rawParts = expanded.replace('\\', '/').split('/')
        if (rawParts.any { it.lowercase() in secretNames || it.lowercase().startsWith(".env.") || it.lowercase().endsWith(".pem") || it.lowercase().endsWith(".p12") || it.lowercase().endsWith(".pfx") || it.lowercase().endsWith(".keystore") }) return false
        val resolved = try { File(expanded).canonicalFile } catch (_: Exception) { return false }
        return roots.any { root ->
            val canonicalRoot = try { File(root).canonicalFile } catch (_: Exception) { return@any false }
            resolved.toPath().startsWith(canonicalRoot.toPath())
        }
    }

    private fun pathOperands(command: String, args: List<String>): List<String> {
        if (command == "grep") return args.dropWhile { it.startsWith("-") }.drop(1).ifEmpty { listOf(".") }
        if (command == "head" || command == "tail") return args.filterNot { it.startsWith("-") || it.toIntOrNull() != null }.ifEmpty { listOf(".") }
        return args.filterNot { it.startsWith("-") }.ifEmpty { if (command == "ls" || command == "du") listOf(".") else emptyList() }
    }

    private fun tokenize(input: String): List<String>? = try {
        // No shell expansion; quotes only group arguments.
        val tokens = mutableListOf<String>(); val current = StringBuilder(); var quote: Char? = null; var escaped = false
        for (c in input) {
            if (escaped) { current.append(c); escaped = false; continue }
            if (c == '\\' && quote != '\'') { escaped = true; continue }
            if (quote != null) { if (c == quote) quote = null else current.append(c); continue }
            if (c == '\'' || c == '"') { quote = c; continue }
            if (c.isWhitespace()) { if (current.isNotEmpty()) { tokens.add(current.toString()); current.setLength(0) } } else current.append(c)
        }
        if (escaped || quote != null) null else { if (current.isNotEmpty()) tokens.add(current.toString()); tokens }
    } catch (_: Exception) { null }

    private fun parseIp(raw: String): InetAddress? = try {
        if (raw.contains('/') || raw.any { it.isLetter() && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val address = InetAddress.getByName(raw)
        if (address !is Inet4Address && address.address.size != 16) null else address
    } catch (_: Exception) { null }
}
