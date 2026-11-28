package com.example.assistant

/** READ-ONLY repository audit through an injected reader; cannot write files or policies. */
class SelfAuditor(private val memory: MemoryManager) {
    interface RepoReader { fun read(path: String): String? }
    data class Report(val createdAt: Long, val findings: List<String>, val locationHint: String) {
        fun render(): String = (listOf("Self-Audit — $createdAt") + findings.map { "• $it" }).joinToString("\n")
    }
    fun shouldRun(now: Long = System.currentTimeMillis(), lastRun: Long): Boolean = lastRun == 0L || now - lastRun >= 7L * 24 * 60 * 60 * 1000
    fun audit(reader: RepoReader): Report {
        val inspect = listOf("app/src/main/java/com/example/assistant/Assistant.kt", "app/src/main/java/com/example/assistant/SecurityManager.kt", "app/src/main/java/com/example/assistant/ModelRouter.kt", "app/src/main/AndroidManifest.xml", "app/build.gradle", "core/security.py")
        val findings = mutableListOf<String>(); var sawAssistant = false; var sawGuardrail = false
        inspect.forEach { path ->
            val content = try { reader.read(path) } catch (_: Exception) { null }
            if (content == null) findings += "$path: unavailable to read (check repo access)." else {
                if (path.endsWith("Assistant.kt")) sawAssistant = true
                if (path.endsWith("SecurityManager.kt") || path.endsWith("security.py")) sawGuardrail = true
                if (content.length > 500_000) findings += "$path: unusually large; audit sampled only metadata."
            }
        }
        if (!sawAssistant) findings += "Assistant entry point could not be verified."
        if (!sawGuardrail) findings += "Guardrail source unavailable; do not broaden command permissions."
        if (findings.isEmpty()) findings += "Target files were readable. This is a lightweight inventory, not a full security assessment."
        val report = Report(System.currentTimeMillis(), findings, "app/src/main/java/com/example/assistant/ (new isolated module; do not edit allowlists automatically)")
        memory.log("self_audit", report.render().take(800))
        return report
    }
    fun submit(report: Report, upgrades: UpgradeManager): MemoryManager.Upgrade? = if (report.findings.any { it.contains("could not") || it.contains("unavailable") }) upgrades.propose("Review self-audit gaps", "Self-audit identified missing or unreadable files.", report.render(), "May be an access/configuration issue; inspect before changes.", report.locationHint, "Repo scan se mila") else null

    companion object {
        /** Read-only adapter over the existing GitHub.kt bridge (same repo this app already talks to). */
        fun githubReader(prefs: android.content.SharedPreferences): RepoReader = object : RepoReader {
            override fun read(path: String): String? {
                val r = GitHub.readFile(prefs, path)
                return if (r.ok) r.text else null
            }
        }
    }
}
