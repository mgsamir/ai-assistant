package com.example.assistant

/** JVM-friendly routing policy checks; invoke main in a local test harness. */
object ModelRouterTest {
    @JvmStatic fun main(args: Array<String>) {
        check(ModelRouter.classify("weather in Delhi") == ModelRouter.Tier.FAST)
        check(ModelRouter.classify("security audit this Kotlin code") == ModelRouter.Tier.DEEP)
        check(ModelRouter.classify("run Linux command safely") == ModelRouter.Tier.DEEP)
        check(ModelRouter.route("code review").choices.none { it.provider == "openai" || it.model.contains("claude", true) })
        check(ModelRouter.route("code review").choices.any { it.provider == "groq" || it.provider == "openrouter" })
        var attempts = 0
        val answer = ModelRouter.firstSuccessful(ModelRouter.route("weather"), { true }) { attempts++; if (attempts == 1) null else "fallback" }
        check(answer == "fallback" && attempts == 2)
    }
}
