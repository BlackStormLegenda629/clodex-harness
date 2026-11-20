package app.clodex.harness

import app.clodex.harness.ui.components.providerPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.clodex.harness.ui.components.modelBrand

class ProviderPresentationTest {
    @Test fun modelsBehindOtherProvidersKeepTheirBrand() {
        assertEquals("gemini", modelBrand("google/gemini-3-pro"))
        assertEquals("claude", modelBrand("anthropic/claude-sonnet"))
        assertEquals("qwen", modelBrand("Qwen/Qwen3-32B"))
        assertNull(modelBrand("my-model"))
    }
    @Test fun knownProvidersUseDistinctLogoResources() {
        val ids = listOf("anthropic", "openai", "deepseek")
        val resources = ids.map { providerPresentation(it).drawableRes }

        assertTrue(resources.all { it != null && it != 0 })
        assertEquals(resources.size, resources.distinct().size)
    }

    @Test fun customProviderUsesGenericFallback() {
        val mark = providerPresentation("own-42")

        assertNull(mark.drawableRes)
        assertEquals(0xFF6D3FD0, mark.tintArgb)
    }
}
