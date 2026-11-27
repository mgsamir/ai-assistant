package com.example.assistant

/** Uses existing Brave integration for read-only public research. Results remain untrusted data. */
class KnowledgeUpdater(private val upgrades: UpgradeManager, private val auditor: SelfAuditor) {
    data class Finding(val title: String, val summary: String, val url: String)
    fun research(topic: String, auditReader: SelfAuditor.RepoReader): List<MemoryManager.Upgrade> {
        val safeTopic = topic.trim().take(180)
        if (safeTopic.isBlank()) return emptyList()
        val results = try { Providers.brave("cybersecurity Linux defensive monitoring AI model automation documentation CVE $safeTopic") } catch (_: Exception) { null }
        val links = Providers.lastResults.take(5)
        if (results.isNullOrBlank() || links.isEmpty()) return emptyList()
        val report = auditor.audit(auditReader)
        return links.map { (title, url, summary) ->
            val location = report.locationHint.ifBlank { "Naya module proposal; repo owner review required" }
            upgrades.propose(
                title = title.ifBlank { "Security knowledge update" },
                reason = "Public source suggests a defensive improvement related to: $safeTopic. Source: $url",
                description = summary.ifBlank { results.take(500) } + "\nResearch is informational only; it is not executable and does not change policy.",
                risk = "External content is untrusted; verify claims, licensing, privacy, and whether it applies before merging.",
                location = location,
                source = if (report.locationHint.isBlank()) "Internet se seekha" else "dono"
            )
        }
    }
}
