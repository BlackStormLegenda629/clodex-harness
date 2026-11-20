package app.clodex.harness.data

/** One selectable model. [providerId] points at a ProviderCfg, [apiId] is what goes to the API. */
data class ModelInfo(
    val display: String,
    val apiId: String,
    val providerId: String,
    val desc: String = "",
    val badge: String? = null,
    val light: Boolean = false,
    /** null: provider list not fetched yet; false: catalog alias absent from the provider's /models. */
    val verified: Boolean? = null,
)

object ModelCatalog {
    val anthropic = listOf(
        ModelInfo("Fable 5.2", "claude-fable-5-2", "anthropic", "Для самых сложных задач", "Pro, Max или Ultra"),
        ModelInfo("Opus 5.5", "claude-opus-5-5", "anthropic", "Для сложной работы и повседневных задач", "Pro"),
        ModelInfo("Sonnet 5.5", "claude-sonnet-5-5", "anthropic", "Наиболее эффективен для более простых задач"),
        ModelInfo("Haiku 5.5", "claude-haiku-5-5", "anthropic", "Быстрее всего для кратких ответов"),
        ModelInfo("Fable 5.1", "claude-fable-5-1", "anthropic", "Предыдущее поколение Fable"),
        ModelInfo("Fable 5", "claude-fable-5", "anthropic", "Первая Fable"),
        ModelInfo("Opus 5", "claude-opus-5", "anthropic", "Прошлый флагман"),
        ModelInfo("Opus 4.8", "claude-opus-4-8", "anthropic"),
        ModelInfo("Opus 4.7", "claude-opus-4-7", "anthropic"),
        ModelInfo("Opus 4.6", "claude-opus-4-6", "anthropic"),
        ModelInfo("Sonnet 5", "claude-sonnet-5", "anthropic"),
        ModelInfo("Sonnet 4.6", "claude-sonnet-4-6", "anthropic"),
    )

    val openai = listOf(
        ModelInfo("GPT 6.2 Astra", "gpt-6.2-astra", "openai", "Самая сильная модель для агентных задач", "Новейшая"),
        ModelInfo("GPT 6.2 Sol", "gpt-6.2-sol", "openai", "Быстрая и точная для повседневной работы"),
        ModelInfo("GPT 6.1 Terra", "gpt-6.1-terra", "openai", "Баланс скорости и качества"),
        ModelInfo("GPT 6.1 Sol", "gpt-6.1-sol", "openai", "Предыдущая быстрая модель"),
        ModelInfo("GPT 6.0", "gpt-6.0", "openai"),
        ModelInfo("GPT 5.5", "gpt-5.5", "openai"),
        ModelInfo("GPT 5.5 mini", "gpt-5.5-mini", "openai", "Лёгкая и недорогая"),
        ModelInfo("o4", "o4", "openai", "Глубокие рассуждения"),
        ModelInfo("o4-mini", "o4-mini", "openai", "Рассуждения подешевле"),
        ModelInfo("GPT-4.1", "gpt-4.1", "openai"),
    )

    val deepseek = listOf(
        ModelInfo("DeepSeek V4.1 Flash 1017", "deepseek-v4.1-flash", "deepseek", "Для лёгких и быстрых задач", "Лёгкая", light = true),
        ModelInfo("DeepSeek V4.1", "deepseek-v4.1", "deepseek", "Полная модель для кода и текста"),
        ModelInfo("DeepSeek V3.2", "deepseek-v3.2", "deepseek", "Предыдущее поколение"),
    )

    val all: List<ModelInfo> = anthropic + openai + deepseek

    fun find(display: String): ModelInfo? = all.firstOrNull { it.display == display }

    fun forProvider(providerId: String): List<ModelInfo> = when (providerId) {
        "anthropic" -> anthropic
        "openai" -> openai
        "deepseek" -> deepseek
        else -> emptyList()
    }

    /** Three headline models of the active provider plus a cheap model for small tasks. */
    fun featured(providerId: String): List<ModelInfo> {
        val own = forProvider(providerId).take(3)
        val cheap = deepseek.firstOrNull { it.light }?.takeIf { providerId != "deepseek" && own.none { m -> m.light } }
        return own + listOfNotNull(cheap)
    }

    /**
     * Catalog entries plus ids fetched from the provider. After a fetch, catalog aliases the provider
     * did not return are marked unverified and sorted after real ids.
     */
    fun merged(p: ProviderCfg): List<ModelInfo> {
        val built = forProvider(p.id)
        if (p.models.isEmpty()) return built
        if (built.isEmpty()) return p.models.map { ModelInfo(it, it, p.id, verified = true) }
        val fetched = p.models.toSet()
        val known = built.filter { it.apiId in fetched }.map { it.copy(verified = true) }
        val extra = p.models.filter { id -> built.none { it.apiId == id } }.map { ModelInfo(it, it, p.id, verified = true) }
        val missing = built.filter { it.apiId !in fetched }.map { it.copy(verified = false, badge = "Не найдена у провайдера") }
        return known + extra + missing
    }

    /** Keeps [current] when it is a real id for [p]; otherwise the first verified model. */
    fun preferredModel(p: ProviderCfg, current: String): String? {
        val list = merged(p)
        val cur = list.firstOrNull { it.display == current }
        if (cur != null && cur.verified != false) return null
        return list.firstOrNull { it.verified == true }?.display ?: list.firstOrNull()?.display
    }

    fun defaultModel(providerId: String): String =
        forProvider(providerId).firstOrNull()?.display ?: "Sonnet 5.5"
}
