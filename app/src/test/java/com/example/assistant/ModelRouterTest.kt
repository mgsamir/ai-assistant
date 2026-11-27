package com.example.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRouterTest {
    @Test fun everydayRequestUsesFastTier() {
        assertEquals(ModelRouter.Tier.FAST, ModelRouter.classify("weather in Delhi"))
    }
    @Test fun securityAndSystemTasksUseDeepTier() {
        assertEquals(ModelRouter.Tier.DEEP, ModelRouter.classify("security audit this Kotlin code"))
        assertEquals(ModelRouter.Tier.DEEP, ModelRouter.classify("run Linux command safely"))
    }
    @Test fun deepCandidatesExcludePaidProviders() {
        val choices = ModelRouter.route("code review").choices
        assertFalse(choices.any { it.provider == "openai" || it.model.contains("claude", true) })
        assertTrue(choices.any { it.provider == "groq" || it.provider == "openrouter" })
    }
    @Test fun providerFailureFallsThroughToNextCandidate() {
        var attempts = 0
        val result = ModelRouter.firstSuccessful(ModelRouter.route("weather"), { true }) {
            attempts++; if (attempts == 1) null else "fallback"
        }
        assertEquals("fallback", result)
        assertEquals(2, attempts)
    }
}
