package app.clodex.harness.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import app.clodex.harness.data.agent.AgentLimits
import app.clodex.harness.data.agent.AnthropicAgentClient
import app.clodex.harness.data.agent.OpenAiAgentClient
import app.clodex.harness.data.agent.ProviderAgentClient
import app.clodex.harness.data.agent.ProviderEvent
import app.clodex.harness.data.agent.ProviderRequest
import java.net.URLEncoder
import java.util.Base64

// ---------------------------------------------------------------- chat

open class ClaudeException(message: String) : Exception(message)

/** Wraps a provider error. [quota] is true for limits, zero balance, expired keys and rate limits. */
class ProviderFailure(val providerId: String, val providerName: String, val quota: Boolean, message: String) : ClaudeException(message)

fun isQuotaMessage(message: String): Boolean {
    val m = message.lowercase()
    return listOf(
        "quota", "insufficient", "balance", "billing", "credit", "payment", "too many requests",
        "rate limit", "429", "402", "401", "unauthorized", "invalid api key", "no access", "limit reached",
    ).any { m.contains(it) }
}

const val PROVIDER_MISSING = "Провайдер пока не настроен. Откройте «Настройки → Провайдер», выберите Anthropic, OpenAI, DeepSeek или свой сервер и добавьте API-ключ."

/** The effort level only scales max_tokens: no dedicated API field is sent (not verified for these models). */
fun maxTokens(effort: String): Int = when (effort) {
    "Низкий" -> 1024; "Средний" -> 2048; "Высокий" -> 4096; "Сверхвысокий" -> 8192; "Максимальный" -> 16000; else -> 2048
}

private val lenient = Json { ignoreUnknownKeys = true }

internal fun providerErrorText(status: Int, body: String): String {
    val msg = runCatching { lenient.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
    return "HTTP $status" + (msg?.let { ": $it" } ?: "")
}

fun anthropicRoot(base: String): String {
    val b = base.trim().trimEnd('/')
    return if (b.endsWith("/v1")) b else "$b/v1"
}

fun openAiRoot(base: String): String = base.trim().trimEnd('/')

fun parseHeaders(raw: String): Map<String, String> = raw.split('\n', ';').mapNotNull { line ->
    val i = line.indexOf(':')
    if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
}.filter { it.first.isNotEmpty() }.toMap()

/**
 * The concrete model id for a provider. An exact id fetched from the provider's /models wins over
 * a catalog alias; custom providers fall back to their configured ids.
 */
fun resolveModel(s: AppSettings, p: ProviderCfg, display: String): String {
    if (display in p.models) return display
    val known = ModelCatalog.all.firstOrNull { it.display == display && it.providerId == p.id }
    if (known != null) return known.apiId
    if (!p.builtin && p.models.isNotEmpty()) return p.models.first()
    val fallback = ModelCatalog.merged(p).firstOrNull { it.verified != false }
    val other = ModelCatalog.find(display)
    if (fallback != null && (other != null || display.contains(' '))) return fallback.apiId
    return display.trim().ifEmpty { fallback?.apiId ?: "default" }
}

/**
 * Routes each request to the active provider. [ProviderRequest.model] is the display name from the
 * model sheet; it is resolved here. Upstream errors become [ProviderFailure] for quota fallback.
 */
class RoutedProviderClient(
    private val settings: SettingsRepository,
    private val http: HttpClient,
    private val limits: AgentLimits = AgentLimits(),
) : ProviderAgentClient {
    override fun stream(request: ProviderRequest): Flow<ProviderEvent> = flow {
        val s = settings.flow.first()
        val p = s.activeProvider()
        emitAll(streamPinned(p, ProviderRequest(resolveModel(s, p, request.model), request.maxTokens,
            request.system, request.messages, request.tools, request.webSearch)))
    }

    internal fun streamPinned(p: ProviderCfg, request: ProviderRequest): Flow<ProviderEvent> = flow {
        if (!p.ready) throw ProviderFailure(p.id, p.name, false, PROVIDER_MISSING)
        val extra = parseHeaders(p.headers)
        val client = if (p.openAi) OpenAiAgentClient(http, p.url, p.key, extra, limits) else AnthropicAgentClient(http, p.url, p.key, extra, limits)
        emitAll(
            client.stream(request).catch { e ->
                if (e is CancellationException || e is ProviderFailure) throw e
                throw ProviderFailure(p.id, p.name, isQuotaMessage(e.message ?: ""), e.message ?: "Ошибка сети")
            },
        )
    }
}

/** "Проверить" and "Загрузить модели" in the provider screen. */
class ProviderChecker(private val http: HttpClient) {
    /** Uses the same base as chat requests, so a green check means chat will work too. */
    private suspend fun request(base: String, key: String, format: String, headers: String, suffix: String) =
        http.get((if (format == "Anthropic") anthropicRoot(base) else openAiRoot(base)) + suffix) {
            if (format == "Anthropic") { header("x-api-key", key); header("anthropic-version", "2023-06-01") }
            else header("Authorization", "Bearer $key")
            parseHeaders(headers).forEach { (k, v) -> header(k, v) }
        }

    suspend fun check(base: String, key: String, format: String, headers: String): Result<String> {
        if (base.isBlank()) return Result.failure(ClaudeException("Укажите базовый URL"))
        if (key.isBlank()) return Result.failure(ClaudeException("Укажите ключ API"))
        return try {
            val r = request(base, key, format, headers, "/models")
            if (r.status.isSuccess()) Result.success("Подключение работает") else Result.failure(ClaudeException(providerErrorText(r.status.value, r.bodyAsText())))
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { Result.failure(ClaudeException("Нет соединения: ${e.message ?: "ошибка сети"}")) }
    }

    /** GET {base}/models -> ids. Handles OpenAI (`data[].id`) and Anthropic-shaped responses. */
    suspend fun models(base: String, key: String, format: String, headers: String): Result<List<String>> {
        if (base.isBlank()) return Result.failure(ClaudeException("Укажите базовый URL"))
        if (key.isBlank()) return Result.failure(ClaudeException("Укажите ключ API"))
        return try {
            val r = request(base, key, format, headers, "/models")
            if (!r.status.isSuccess()) return Result.failure(ClaudeException(providerErrorText(r.status.value, r.bodyAsText())))
            val obj = runCatching { lenient.parseToJsonElement(r.bodyAsText()).jsonObject }.getOrNull()
                ?: return Result.failure(ClaudeException("Сервер вернул не JSON"))
            val arr = (obj["data"] ?: obj["models"])?.jsonArray
                ?: return Result.failure(ClaudeException("В ответе нет списка моделей"))
            val ids = arr.mapNotNull { el ->
                val o = runCatching { el.jsonObject }.getOrNull()
                o?.get("id")?.jsonPrimitive?.contentOrNull ?: o?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: runCatching { el.jsonPrimitive.contentOrNull }.getOrNull()
            }.distinct()
            if (ids.isEmpty()) Result.failure(ClaudeException("Сервер не вернул ни одной модели"))
            else Result.success(ids)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { Result.failure(ClaudeException("Нет соединения: ${e.message ?: "ошибка сети"}")) }
    }
}

// ---------------------------------------------------------------- GitHub skills

data class RemoteSkill(val name: String, val description: String, val body: String)

interface SkillFetcher { suspend fun fetch(repo: String, path: String, ref: String): Result<RemoteSkill> }

object SkillParser {
    /** SKILL.md with optional `---` frontmatter holding name/description. */
    fun parse(raw: String, fallbackName: String): RemoteSkill {
        var name = fallbackName
        var desc = ""
        var body = raw
        if (raw.trimStart().startsWith("---")) {
            val t = raw.trimStart().removePrefix("---").trimStart('\r', '\n')
            val end = t.indexOf("\n---")
            if (end >= 0) {
                t.substring(0, end).lines().forEach { l ->
                    val i = l.indexOf(':')
                    if (i > 0) {
                        val k = l.substring(0, i).trim().lowercase()
                        val v = l.substring(i + 1).trim().trim('"', '\'')
                        if (k == "name" && v.isNotEmpty()) name = v
                        if (k == "description" && v.isNotEmpty()) desc = v
                    }
                }
                body = t.substring(end + 4).trimStart('\r', '\n')
            }
        }
        if (desc.isEmpty()) desc = body.lines().firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("#") }?.trim()?.take(60) ?: "Без описания"
        return RemoteSkill(name, desc.take(120), raw.trim())
    }

    /** owner/repo or a github.com link -> "owner/repo", else null. */
    fun parseRepo(input: String): String? {
        val t = input.trim()
        val isUrl = Regex("^https?://(www\\.)?github\\.com/", RegexOption.IGNORE_CASE).containsMatchIn(t)
        val parts = t.replace(Regex("^https?://(www\\.)?github\\.com/", RegexOption.IGNORE_CASE), "")
            .removeSuffix("/").removeSuffix(".git").split('/').filter { it.isNotEmpty() }
        val ok = if (isUrl) parts.size >= 2 else parts.size == 2
        if (!ok) return null
        val r = parts[0] + "/" + parts[1].removeSuffix(".git")
        return if (Regex("^[\\w.-]+/[\\w.-]+$").matches(r)) r else null
    }
}

class GitHubSkillFetcher(private val http: HttpClient, private val settings: SettingsRepository) : SkillFetcher {
    override suspend fun fetch(repo: String, path: String, ref: String): Result<RemoteSkill> {
        val clean = path.trim().trim('/')
        val file = when {
            clean.isEmpty() || clean == "/" -> "SKILL.md"
            clean.endsWith(".md", ignoreCase = true) -> clean
            else -> "$clean/SKILL.md"
        }
        return try {
            val token = settings.flow.first().githubToken
            val enc = file.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            val r = http.get("https://api.github.com/repos/$repo/contents/$enc") {
                parameter("ref", ref)
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header("User-Agent", "ClodexHarness")
                if (token.isNotBlank()) header("Authorization", "Bearer $token")
            }
            when (r.status.value) {
                200 -> {
                    val obj = lenient.parseToJsonElement(r.bodyAsText()).jsonObject
                    val content = obj["content"]?.jsonPrimitive?.contentOrNull
                        ?: return Result.failure(ClaudeException("$file в $repo — это папка, а не файл"))
                    val text = String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
                    Result.success(SkillParser.parse(text, file.substringBeforeLast('/', "").substringAfterLast('/').ifEmpty { repo.substringAfter('/') }))
                }
                404 -> Result.failure(ClaudeException("Не найден $file в $repo (ветка $ref). Проверьте адрес, папку и ветку"))
                401 -> Result.failure(ClaudeException("Токен GitHub недействителен"))
                403, 429 -> Result.failure(ClaudeException("Лимит GitHub API. Добавьте токен в настройках"))
                else -> Result.failure(ClaudeException("GitHub вернул ${r.status.value}"))
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { Result.failure(ClaudeException("Нет соединения с GitHub")) }
    }
}
