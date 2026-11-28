package com.example.assistant

/**
 * Uses existing Brave integration for read-only public research. Results remain untrusted data:
 * nothing here executes a command or changes the allowlist/capabilities by itself — every finding
 * only ever turns into a pending UpgradeManager proposal that waits for the user's own Approve/Deny.
 *
 * "Duniya bhar ka public knowledge" matlab: news, docs, CVEs, aur jo bhi public tutorial content
 * (YouTube/Instagram samet) sabhi search engines ki indexing se, text ke roop mein milta hai -- koi
 * site-specific API/scraping/login nahi, sirf wahi jo har koi web search se dekh sakta hai.
 */
class KnowledgeUpdater(private val upgrades: UpgradeManager, private val auditor: SelfAuditor) {
    data class Finding(val title: String, val summary: String, val url: String, val source: String)

    // Har ek "lane" ek alag public source-shape ko target karta hai (query modifier se) --
    // koi extra API key ya login nahi chahiye, sab Brave web search ke upar se hi.
    private val LANES = listOf(
        "web" to "",
        "YouTube tutorial" to " site:youtube.com tutorial OR howto",
        "Instagram tutorial" to " site:instagram.com tutorial OR reel",
    )

    fun research(topic: String, auditReader: SelfAuditor.RepoReader): List<MemoryManager.Upgrade> {
        val safeTopic = topic.trim().take(180)
        if (safeTopic.isBlank()) return emptyList()
        val base = "cybersecurity Linux defensive monitoring AI model automation documentation CVE $safeTopic"

        val findings = ArrayList<Finding>()
        for ((laneName, modifier) in LANES) {
            val results = try { Providers.brave(base + modifier) } catch (_: Exception) { null }
            val links = Providers.lastResults.take(3)
            if (results.isNullOrBlank() || links.isEmpty()) continue
            for ((title, url, summary) in links) {
                findings.add(Finding(
                    title = title.ifBlank { "Security knowledge update" },
                    summary = summary.ifBlank { results.take(500) },
                    url = url,
                    source = laneName
                ))
            }
            if (findings.size >= 5) break
        }
        if (findings.isEmpty()) return emptyList()

        val report = auditor.audit(auditReader)
        return findings.distinctBy { it.url }.take(5).map { f ->
            val location = report.locationHint.ifBlank { "Naya module proposal; repo owner review required" }
            upgrades.propose(
                title = f.title,
                reason = "Public ${f.source} source suggests a defensive improvement related to: $safeTopic. Source: ${f.url}",
                description = f.summary + "\nResearch is informational only; it is not executable and does not change policy. Found via: ${f.source}.",
                risk = "External content is untrusted; verify claims, licensing, privacy, and whether it applies before merging.",
                location = location,
                source = if (report.locationHint.isBlank()) "Internet se seekha" else "dono"
            )
        }
    }
}
