package app.clodex.harness

import app.clodex.harness.data.*
import org.junit.Assert.*
import org.junit.Test

class ModelCatalogTest {
    @Test fun openAiFeaturedMatchesRequestedSheet() {
        val names = ModelCatalog.featured("openai").map { it.display }
        assertEquals(listOf("GPT 6.2 Astra", "GPT 6.2 Sol", "GPT 6.1 Terra", "DeepSeek V4.1 Flash 1017"), names)
    }

    @Test fun lightTaskModelIsDeepSeekFlash() {
        assertTrue(ModelCatalog.deepseek.first().light)
        assertEquals("deepseek-v4.1-flash", ModelCatalog.deepseek.first().apiId)
    }

    @Test fun deepSeekFeaturedHasNoDuplicateCheapRow() {
        assertEquals(listOf("DeepSeek V4.1 Flash 1017", "DeepSeek V4.1", "DeepSeek V3.2"), ModelCatalog.featured("deepseek").map { it.display })
    }

    @Test fun customProviderShowsItsOwnModels() {
        val custom = ProviderCfg("own-1", "custom", "Мой сервер", "https://x/v1", "k", listOf("m-a", "m-b"))
        val settings = AppSettings(providerId = custom.id, providers = DefaultProviders + custom)
        assertEquals(listOf("m-a", "m-b"), settings.modelsFor(custom.id).map { it.display })
        assertEquals(custom.id, settings.providerForModel("m-b")?.id)
    }

    @Test fun selectingBuiltinModelResolvesItsProvider() {
        val settings = AppSettings()
        assertEquals("openai", settings.providerForModel("GPT 6.2 Astra")?.id)
        assertEquals("anthropic", settings.activeProvider().id)
    }

    @Test fun unknownDisplayOnCustomProviderFallsBackToItsFirstModel() {
        val custom = ProviderCfg("own-2", "custom", "X", "https://x/v1", "k", listOf("alpha", "beta"))
        val settings = AppSettings(providerId = custom.id, providers = DefaultProviders + custom)
        val api = resolveModel(settings, custom, "Sonnet 5.5")
        assertTrue(api == "alpha" || api == "beta")
        assertEquals("claude-sonnet-5-5", resolveModel(settings, settings.provider("anthropic")!!, "Sonnet 5.5"))
    }

    @Test fun providerFailuresAreClassifiedAsQuota() {
        assertTrue(isQuotaMessage("HTTP 429: rate limit reached"))
        assertTrue(isQuotaMessage("Insufficient balance"))
        assertFalse(isQuotaMessage("HTTP 500: server error"))
    }
}
