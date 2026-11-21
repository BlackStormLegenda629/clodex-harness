package app.clodex.harness.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*

val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class WorkDir(val path: String, val tag: String = "git")

val TOGGLE_DEFAULTS: Map<String, Boolean> = mapOf(
    "ask" to true, "edit" to false, "agentWeb" to false, "web" to false,
    "n1" to true, "n2" to true, "n3" to false, "p1" to true, "p2" to false,
)

/** A provider endpoint. Built-ins (Anthropic/OpenAI/DeepSeek) show their own model catalogs. */
data class ProviderCfg(
    val id: String,
    /** "anthropic" | "openai" | "deepseek" | "custom" */
    val kind: String,
    val name: String,
    val url: String = "",
    val key: String = "",
    /** Custom providers: model ids returned by /models or entered by hand. */
    val models: List<String> = emptyList(),
    /** "OpenAI" | "Anthropic" — request/response shape for custom servers. */
    val format: String = "OpenAI",
    val headers: String = "",
) {
    val builtin: Boolean get() = kind == "anthropic" || kind == "openai" || kind == "deepseek"
    val openAi: Boolean get() = kind != "anthropic" && format != "Anthropic"
    val ready: Boolean get() = key.isNotBlank() && (kind != "custom" || url.isNotBlank())
}

val DefaultProviders = listOf(
    ProviderCfg("anthropic", "anthropic", "Anthropic", "https://api.anthropic.com"),
    ProviderCfg("openai", "openai", "OpenAI", "https://api.openai.com/v1"),
    ProviderCfg("deepseek", "deepseek", "DeepSeek", "https://api.deepseek.com/v1"),
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun encodeProviders(list: List<ProviderCfg>): String = buildJsonArray {
    list.forEach { p ->
        addJsonObject {
            put("id", p.id); put("kind", p.kind); put("name", p.name); put("url", p.url)
            put("key", p.key); put("format", p.format); put("headers", p.headers)
            putJsonArray("models") { p.models.forEach { add(it) } }
        }
    }
}.toString()

private fun decodeProviders(raw: String?): List<ProviderCfg>? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        json.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val kind = o["kind"]?.jsonPrimitive?.contentOrNull ?: "custom"
            ProviderCfg(
                id = id, kind = kind,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: id,
                url = o["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                key = o["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                models = o["models"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                format = o["format"]?.jsonPrimitive?.contentOrNull ?: "OpenAI",
                headers = o["headers"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }.getOrNull()
}

/** Older installs kept a single custom provider in own* fields; fold it into the new list. */
private fun migrateProviders(p: Preferences): List<ProviderCfg> {
    val base = DefaultProviders.map { d ->
        when (d.id) {
            "anthropic" -> d.copy(key = p[K.anthropicKey] ?: "")
            else -> d
        }
    }
    val ownUrl = p[K.ownUrl] ?: ""
    if (ownUrl.isBlank()) return base
    val name = p[K.ownName]?.takeIf { it.isNotBlank() } ?: "Свой провайдер"
    val custom = ProviderCfg(
        id = "own", kind = "custom", name = name, url = ownUrl,
        key = p[K.ownKey] ?: "", format = p[K.ownFormat] ?: "OpenAI", headers = p[K.ownHeaders] ?: "",
        models = p[K.ownModels]?.split(',', '\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
    )
    return base + custom
}

enum class AppStyle { Classic, Studio, Material }
enum class ProgressStyle(val label: String) { Straight("Прямой"), Wave("Волна") }

data class AppSettings(
    val theme: String = "Авто",
    val accent: String = "Терракота",
    val textSize: String = "Обычный",
    val uiFont: String = "Inter",
    val codeFont: String = "JetBrains Mono",
    /** Active provider id. */
    val providerId: String = "anthropic",
    val providers: List<ProviderCfg> = DefaultProviders,
    val model: String = "Sonnet 5.5",
    val effort: String = "Средний",
    val mode: String = "Правки",
    val tab: String = "chat",
    val currentChatId: Long = 0,
    val workspace: String = "Все",
    val githubToken: String = "",
    val instructions: String = "Отвечай кратко по-русски. Объясняй изменения. Не утверждай, что запускал команды или тесты, если не запускал.",
    val alwaysAllowed: List<String> = listOf("npm test", "git status", "ls"),
    val workDirs: List<WorkDir> = listOf(WorkDir("~/api-server"), WorkDir("~/web-app"), WorkDir("~/cli-tools")),
    val seeded: Boolean = false,
    val toggles: Map<String, Boolean> = TOGGLE_DEFAULTS,
    val style: AppStyle = AppStyle.Material,
    val progressStyle: ProgressStyle = ProgressStyle.Straight,
    val scrollBlur: Boolean = false,
) {
    fun on(key: String): Boolean = toggles[key] ?: false

    fun provider(id: String): ProviderCfg? = providers.firstOrNull { it.id == id }

    fun activeProvider(): ProviderCfg = provider(providerId) ?: providers.firstOrNull() ?: DefaultProviders.first()

    fun providerForModel(display: String): ProviderCfg? {
        val known = ModelCatalog.find(display)?.providerId
        if (known != null) return provider(known)
        return provider(providerId)?.takeIf { display in it.models } ?: providers.firstOrNull { display in it.models }
    }

    /** Catalog aliases merged with ids fetched from the provider's /models. */
    fun modelsFor(id: String): List<ModelInfo> = provider(id)?.let { ModelCatalog.merged(it) } ?: emptyList()
}

object K {
    val theme = stringPreferencesKey("theme")
    val style = stringPreferencesKey("style")
    val progressStyle = stringPreferencesKey("progressStyle")
    val scrollBlur = booleanPreferencesKey("scrollBlur")
    val accent = stringPreferencesKey("accent")
    val textSize = stringPreferencesKey("textSize")
    val uiFont = stringPreferencesKey("uiFont")
    val codeFont = stringPreferencesKey("codeFont")
    val providerId = stringPreferencesKey("providerId")
    val providers = stringPreferencesKey("providers")
    /** legacy keys, kept for migration only */
    val provider = stringPreferencesKey("provider")
    val anthropicKey = stringPreferencesKey("anthropicKey")
    val ownName = stringPreferencesKey("ownName")
    val ownUrl = stringPreferencesKey("ownUrl")
    val ownKey = stringPreferencesKey("ownKey")
    val ownFormat = stringPreferencesKey("ownFormat")
    val ownModels = stringPreferencesKey("ownModels")
    val ownHeaders = stringPreferencesKey("ownHeaders")
    val model = stringPreferencesKey("model")
    val effort = stringPreferencesKey("effort")
    val mode = stringPreferencesKey("mode")
    val tab = stringPreferencesKey("tab")
    val currentChat = longPreferencesKey("currentChat")
    val workspace = stringPreferencesKey("workspace")
    val githubToken = stringPreferencesKey("githubToken")
    val instructions = stringPreferencesKey("instructions")
    val alwaysAllowed = stringPreferencesKey("alwaysAllowed")
    val workDirs = stringPreferencesKey("workDirs")
    val seeded = booleanPreferencesKey("seeded")
    fun toggle(name: String) = booleanPreferencesKey("tg_$name")
}

interface CodePrefs {
    val mode: Flow<String>
    val workspace: Flow<String>
    suspend fun setMode(v: String)
    suspend fun setWorkspace(v: String)
}

class SettingsRepository(private val store: DataStore<Preferences>) : CodePrefs {
    val flow: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        val providers = decodeProviders(p[K.providers]) ?: migrateProviders(p)
        val providerId = p[K.providerId] ?: when (p[K.provider]) {
            "OpenAI-совместимый" -> "openai"
            "Свой провайдер" -> "own"
            else -> "anthropic"
        }
        AppSettings(
            theme = p[K.theme] ?: d.theme,
            style = p[K.style]?.let { saved -> AppStyle.entries.firstOrNull { it.name == saved } } ?: d.style,
            progressStyle = ProgressStyle.entries.firstOrNull { it.name == p[K.progressStyle] } ?: d.progressStyle,
            scrollBlur = p[K.scrollBlur] ?: d.scrollBlur,
            accent = p[K.accent] ?: d.accent,
            textSize = p[K.textSize] ?: d.textSize,
            uiFont = p[K.uiFont] ?: d.uiFont,
            codeFont = p[K.codeFont] ?: d.codeFont,
            providerId = if (providers.any { it.id == providerId }) providerId else providers.first().id,
            providers = providers,
            model = p[K.model] ?: d.model,
            effort = p[K.effort] ?: d.effort,
            mode = p[K.mode] ?: d.mode,
            tab = p[K.tab] ?: d.tab,
            currentChatId = p[K.currentChat] ?: 0L,
            workspace = p[K.workspace] ?: d.workspace,
            githubToken = p[K.githubToken] ?: "",
            instructions = p[K.instructions] ?: d.instructions,
            alwaysAllowed = p[K.alwaysAllowed]?.split('\n')?.filter { it.isNotBlank() } ?: d.alwaysAllowed,
            workDirs = p[K.workDirs]?.split('\n')?.filter { it.isNotBlank() }
                ?.map { l -> l.split('\t').let { WorkDir(it[0], it.getOrElse(1) { "git" }) } } ?: d.workDirs,
            seeded = p[K.seeded] ?: false,
            toggles = TOGGLE_DEFAULTS.mapValues { (k, v) -> p[K.toggle(k)] ?: v },
        )
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) { store.edit { it[key] = value } }
    suspend fun setToggle(name: String, value: Boolean) { store.edit { it[K.toggle(name)] = value } }
    suspend fun setAlwaysAllowed(list: List<String>) { store.edit { it[K.alwaysAllowed] = list.joinToString("\n") } }
    suspend fun setWorkDirs(list: List<WorkDir>) { store.edit { it[K.workDirs] = list.joinToString("\n") { d -> d.path + "\t" + d.tag } } }
    suspend fun setProviders(list: List<ProviderCfg>) { store.edit { it[K.providers] = encodeProviders(list) } }
    suspend fun clearAll() { store.edit { it.clear() } }

    override val mode: Flow<String> = flow.map { it.mode }
    override val workspace: Flow<String> = flow.map { it.workspace }
    override suspend fun setMode(v: String) = set(K.mode, v)
    override suspend fun setWorkspace(v: String) = set(K.workspace, v)
}
