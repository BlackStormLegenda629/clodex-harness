package app.clodex.harness.data.mcp

import app.clodex.harness.data.ConnectorCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NamespacedMcpTool(val connectorId: String, val tool: McpToolSpec, val permission: McpPermission) {
    val namespacedName: String get() = "${connectorId}__${tool.name}"
}

/** Facade over MCP config + transport: a connector is "verified" only after a successful probe. */
class McpRepository(
    private val store: McpConfigRepository,
    private val gateway: McpGateway,
    private val oauth: McpOAuth? = null,
) {
    private val operations = Mutex()
    suspend fun authorize(id: String, endpoint: String, openBrowser: (String) -> Unit): Result<McpProbe> {
        val tokens = requireNotNull(oauth).connect(endpoint, openBrowser)
        return probeAndSave(id, McpConnectionConfig(id, endpoint, McpAuthMode.BEARER, oauth = tokens))
    }
    val configs: Flow<Map<String, McpConnectionConfig>> = store.configs

    suspend fun config(id: String): McpConnectionConfig? = store.get(id)

    suspend fun probeAndSave(connectorId: String, config: McpConnectionConfig): Result<McpProbe> = operations.withLock {
        if (connectorId == "gm") {
            return@withLock Result.failure(McpProtocolException("Для Gmail выберите аккаунт Google"))
        }
        val input = config.copy(connectorId = connectorId, endpoint = config.endpoint.trim())
        val result = gateway.probe(input)
        result.fold(
            onSuccess = { p ->
                store.save(
                    input.copy(
                        enabled = true, lastCheckedAt = p.checkedAt, serverName = p.server.name,
                        serverVersion = p.server.version, tools = p.tools, lastError = null,
                    ),
                )
            },
            onFailure = { e -> store.save(input.copy(enabled = false, lastError = e.message ?: "Ошибка MCP")) },
        )
        result
    }

    suspend fun connectGmail(access: String): Result<McpProbe> = operations.withLock {
        if (access.isBlank()) return@withLock Result.failure(McpProtocolException("Google не выдал доступ к Gmail"))
        val input = McpConnectionConfig("gm", requireNotNull(ConnectorCatalog.endpoint("gm")), McpAuthMode.GOOGLE, bearerToken = access)
        val discovery = gateway.probe(input).getOrElse { return@withLock Result.failure(it) }
        val tools = discovery.tools.filter { it.readOnlyHint && it.name in gmailReadTools }
        if (tools.none { it.name == "list_labels" }) return@withLock Result.failure(McpProtocolException("Gmail MCP не предоставил проверку доступа"))
        val check = gateway.call(input, "list_labels", JsonObject(emptyMap())).getOrElse { return@withLock Result.failure(it) }
        if (!check.hasGmailLabels()) return@withLock Result.failure(McpProtocolException("Gmail MCP не подтвердил доступ"))
        store.save(input.copy(lastCheckedAt = discovery.checkedAt, serverName = discovery.server.name, serverVersion = discovery.server.version, tools = tools))
        Result.success(discovery.copy(tools = tools))
    }

    private val gmailReadTools = setOf("list_labels", "search_threads", "get_thread", "get_message", "list_drafts", "get_draft")

    private fun McpCallResult.hasGmailLabels(): Boolean {
        if (isError) return false
        val payload = (structured ?: runCatching { McpCodec.json.parseToJsonElement(text) }.getOrNull()) as? JsonObject ?: return false
        // Gmail MCP documents {} inside the tool payload as a valid zero-result response.
        if (payload.isEmpty()) return true
        val labels = payload["labels"] as? JsonArray ?: return false
        return labels.all { label ->
            label is JsonObject && listOf("labelId", "name").all { field ->
                (label[field] as? JsonPrimitive)?.let { it.isString && it.content.isNotBlank() } == true
            }
        }
    }
    private fun isUsable(config: McpConnectionConfig) = config.enabled && config.lastError == null &&
        (config.connectorId != "gm" || config.authMode == McpAuthMode.GOOGLE)

    suspend fun setEnabled(connectorId: String, enabled: Boolean) = operations.withLock {
        store.get(connectorId)?.let { store.save(it.copy(enabled = enabled, grants = if (enabled) it.grants else emptyList())) }
    }

    suspend fun remove(connectorId: String) = operations.withLock { store.remove(connectorId) }

    suspend fun enabledTools(): List<NamespacedMcpTool> =
        store.configs.first().values.filter(::isUsable)
            .flatMap { c -> c.tools.map { NamespacedMcpTool(c.connectorId, it, c.permission(it)) } }

    private fun McpConnectionConfig.matches(permission: McpPermission) = isUsable(this) &&
        tools.any { it.name == permission.tool && this.permission(it) == permission }

    suspend fun isAllowed(permission: McpPermission, chatId: Long): Boolean = operations.withLock {
        store.get(permission.connectorId)?.let { it.matches(permission) && McpGrant(chatId, permission) in it.grants } == true
    }

    suspend fun remember(permission: McpPermission, chatId: Long): Boolean = operations.withLock {
        val config = store.get(permission.connectorId) ?: return@withLock false
        if (chatId <= 0 || !config.matches(permission)) return@withLock false
        store.save(config.copy(grants = (config.grants + McpGrant(chatId, permission)).distinct()))
        true
    }

    suspend fun revoke(permission: McpPermission, chatId: Long) = operations.withLock {
        store.get(permission.connectorId)?.let { config -> store.save(config.copy(grants = config.grants - McpGrant(chatId, permission))) }
    }

    suspend fun call(namespacedName: String, args: JsonObject, expected: McpPermission? = null): Result<McpCallResult> = operations.withLock {
        val id = namespacedName.substringBefore("__", "")
        val tool = namespacedName.substringAfter("__", "")
        var config = store.get(id)
        if (id.isEmpty() || tool.isEmpty() || config == null || !isUsable(config) || config.tools.none { it.name == tool }) {
            return@withLock Result.failure(McpProtocolException("Инструмент $namespacedName недоступен"))
        }
        if (expected != null && !config.matches(expected)) return@withLock Result.failure(McpProtocolException("Подключение или инструмент изменился. Отправьте запрос заново."))
        if (id == "gm") {
            val result = gateway.call(config, tool, args)
            if ((result.exceptionOrNull() as? McpHttpException)?.status != 401) return@withLock result
            store.save(config.copy(enabled = false, bearerToken = "", lastError = "Войдите в Google снова"))
            return@withLock Result.failure(McpProtocolException("Войдите в Google снова"))
        }
        val tokens = config.oauth ?: return@withLock gateway.call(config, tool, args)
        var refreshed = false
        try {
            if (tokens.expiresAt?.let { it <= System.currentTimeMillis() + 60_000 } == true && tokens.refresh != null) {
                config = config.copy(oauth = requireNotNull(oauth).refresh(tokens)); store.save(config); refreshed = true
            }
            var result = gateway.call(config, tool, args)
            if ((result.exceptionOrNull() as? McpHttpException)?.status == 401) {
                if (!refreshed && tokens.refresh != null) {
                    config = config.copy(oauth = requireNotNull(oauth).refresh(tokens)); store.save(config)
                    result = gateway.call(config, tool, args)
                }
                if ((result.exceptionOrNull() as? McpHttpException)?.status == 401) error("Нужно войти снова")
            }
            result
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            store.save(config.copy(enabled = false, lastError = "Нужно войти снова"))
            Result.failure(McpProtocolException("Нужно войти снова"))
        }
    }
}
