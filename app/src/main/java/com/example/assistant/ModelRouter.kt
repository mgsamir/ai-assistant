package com.example.assistant

/** Deterministic routing policy; provider calls remain in the existing Providers/WebSearch layers. */
object ModelRouter {
    enum class Tier { FAST, DEEP }
    data class ModelChoice(val provider: String, val model: String, val requiresKey: String)
    data class Route(val tier: Tier, val choices: List<ModelChoice>, val reason: String)

    private val deepHints = Regex("(?i)\\b(code|debug|linux|command|security|audit|vulnerability|network|firewall|architecture|review|analyse|analyze|pentest)\\b")
    private val newsHints = Regex("(?i)\\b(news|headline|weather|temperature|score|price|today|latest)\\b")

    fun classify(input: String): Tier = if (deepHints.containsMatchIn(input) || input.length > 600) Tier.DEEP else Tier.FAST

    /** Only free-capable providers/models; never returns OpenAI/Claude or paid OpenRouter IDs. */
    fun route(input: String): Route {
        val tier = classify(input)
        val news = newsHints.containsMatchIn(input)
        val choices = if (tier == Tier.DEEP) listOf(
            ModelChoice("openrouter", "qwen/qwen3-coder:free", "OPENROUTER_KEY"),
            ModelChoice("groq", "openai/gpt-oss-120b", "GROQ_KEY"),
            ModelChoice("groq", "llama-3.3-70b-versatile", "GROQ_KEY"),
            ModelChoice("gemini", "gemini-2.5-flash", "GEMINI_API_KEY")
        ) else listOf(
            ModelChoice("gemini", if (news) "gemini-2.5-flash" else "gemini-2.5-flash-lite", "GEMINI_API_KEY"),
            ModelChoice("groq", "llama-3.3-70b-versatile", "GROQ_KEY")
        )
        return Route(tier, choices, if (tier == Tier.DEEP) "Code, system, or security analysis" else "Short everyday request")
    }

    /** Try candidates in order; provider errors/empty results move to the next free candidate. */
    fun <T : Any> firstSuccessful(route: Route, hasKey: (String) -> Boolean, request: (ModelChoice) -> T?): T? {
        for (choice in route.choices) {
            if (!hasKey(choice.requiresKey)) continue
            try { request(choice)?.let { return it } } catch (_: Exception) { /* continue to next configured free provider */ }
        }
        return null
    }
}
