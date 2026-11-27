package com.example.assistant

/** Shared approval queue for internet and repo suggestions. */
class UpgradeManager(private val memory: MemoryManager) {
    fun propose(title: String, reason: String, description: String, risk: String, location: String, source: String, patchPath: String = "", patchContent: String = ""): MemoryManager.Upgrade {
        require(listOf(title, reason, description, risk, location).all { it.isNotBlank() }) { "Upgrade proposal needs title, reason, description, risk and location." }
        require(source in setOf("Internet se seekha", "Repo scan se mila", "dono"))
        return memory.saveUpgrade(MemoryManager.Upgrade("", title.take(120), reason.take(500), description.take(1200), risk.take(500), location.take(240), source, patchPath = patchPath.take(240), patchContent = patchContent.take(120_000)))
    }
    fun attachDraft(id: String, path: String, content: String) = memory.attachDraft(id, path, content)
    fun deny(id: String): Boolean { memory.updateUpgrade(id, "denied") ?: return false; memory.log("upgrade_denied", id); return true }
    fun approved(id: String): MemoryManager.Upgrade? {
        val proposal = memory.updateUpgrade(id, "approved") ?: return null
        memory.log("upgrade_approved", "${proposal.title}; approved by user via app at ${System.currentTimeMillis()}")
        return proposal
    }
    fun pending() = memory.pendingUpgrades()
}
