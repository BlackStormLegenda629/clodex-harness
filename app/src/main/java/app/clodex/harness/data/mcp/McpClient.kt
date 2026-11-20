package app.clodex.harness.data.mcp

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.atomic.AtomicLong

interface McpGateway {
    suspend fun probe(config: McpConnectionConfig): Result<McpProbe>
    suspend fun call(config: McpConnectionConfig, tool: String, arguments: JsonObject): Result<McpCallResult>
}

class KtorMcpGateway(
    private val http: HttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : McpGateway {
    private val ids = AtomicLong(0)
    private data class Session(val id: String?, val version: String)
    private val sessions = mutableMapOf<Pair<String, String>, Session>()
    private val mutex = Mutex()

    override suspend fun probe(config: McpConnectionConfig): Result<McpProbe> = resultOf(config) {
        mutex.withLock { initialize(config) }
    }

    private suspend fun initialize(config: McpConnectionConfig): McpProbe {
            validate(config)
            val initId = ids.incrementAndGet()
            val init = buildJsonObject {
                put("protocolVersion", "2025-06-18")
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") { put("name", "Clodex Harness"); put("version", "1.0") }
            }
            val initResponse = post(config, McpCodec.request(initId, "initialize", init), sessionId = null)
            val initResult = parse(initResponse, initId)
            val session = Session(initResponse.headers["Mcp-Session-Id"], initResult["protocolVersion"]?.jsonPrimitive?.contentOrNull ?: "2025-06-18")

            post(
                config,
                McpCodec.notification("notifications/initialized"),
                sessionId = session,
            )

            val listId = ids.incrementAndGet()
            val listResponse = post(config, McpCodec.request(listId, "tools/list", JsonObject(emptyMap())), session)
            val listResult = parse(listResponse, listId)
            sessions[config.connectorId to config.endpoint] = session
            return McpProbe(
                server = parseServer(initResult),
                tools = parseTools(listResult["tools"]),
                sessionId = session.id,
                checkedAt = clock(),
            )
    }

    override suspend fun call(config: McpConnectionConfig, tool: String, arguments: JsonObject): Result<McpCallResult> = resultOf(config) {
        validate(config)
        mutex.withLock {
            val key = config.connectorId to config.endpoint
            if (key !in sessions) initialize(config)
            val id = ids.incrementAndGet()
            val params = buildJsonObject {
                put("name", tool)
                put("arguments", arguments)
            }
            val body = McpCodec.request(id, "tools/call", params)
            val response = try { post(config, body, sessions[key]) } catch (e: McpHttpException) {
                if (e.status != 404 || sessions[key]?.id == null) throw e
                sessions.remove(key)
                initialize(config)
                post(config, body, sessions[key])
            }
            parseCall(parse(response, id))
        }
    }

    private suspend fun post(
        config: McpConnectionConfig,
        body: String,
        sessionId: Session?,
    ): HttpResponse {
        val response = withTimeout(60_000) {
            http.post(config.endpoint) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "application/json, text/event-stream")
                sessionId?.id?.let { header("Mcp-Session-Id", it) }
                sessionId?.version?.let { header("MCP-Protocol-Version", it) }
                val token = config.oauth?.access ?: config.bearerToken.takeIf { config.authMode == McpAuthMode.BEARER || config.authMode == McpAuthMode.GOOGLE }.orEmpty()
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                config.headers.forEach { (name, value) -> header(name, value) }
                setBody(body)
            }
        }
        if (!response.status.isSuccess()) throw McpHttpException(response.status.value)
        return response
    }

    private suspend fun parse(response: HttpResponse, id: Long): JsonObject {
        val body = response.bodyAsText()
        return McpCodec.parsePayload(response.headers[HttpHeaders.ContentType], body, id)
    }

    private fun validate(config: McpConnectionConfig) {
        val endpoint = config.endpoint.trim()
        if (!(endpoint.startsWith("https://") || endpoint.startsWith("http://"))) {
            throw McpProtocolException("Некорректный адрес MCP")
        }
    }

    private fun parseServer(result: JsonObject): McpServerInfo {
        val info = result["serverInfo"]?.let { runCatching { it.jsonObject }.getOrNull() }
        return McpServerInfo(
            name = info?.get("name")?.jsonPrimitive?.contentOrNull ?: "MCP server",
            version = info?.get("version")?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private fun parseTools(raw: JsonElement?): List<McpToolSpec> =
        (raw as? JsonArray).orEmpty().mapNotNull { item ->
            val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val annotations = obj["annotations"]?.let { runCatching { it.jsonObject }.getOrNull() }
            McpToolSpec(
                name = name,
                description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                inputSchema = obj["inputSchema"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: JsonObject(emptyMap()),
                readOnlyHint = annotations?.get("readOnlyHint")?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }

    private fun parseCall(result: JsonObject): McpCallResult {
        val text = result["content"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty().mapNotNull { item ->
            val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") obj["text"]?.jsonPrimitive?.contentOrNull else null
        }.joinToString("\n")
        return McpCallResult(
            text = text,
            structured = result["structuredContent"],
            isError = result["isError"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    private suspend fun <T> resultOf(config: McpConnectionConfig, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: McpHttpException) {
        Result.failure(e)
    } catch (e: Exception) {
        val raw = if (e is McpProtocolException) e.message.orEmpty() else "Ошибка MCP: ${e.message ?: "сеть недоступна"}"
        val secrets = listOfNotNull(config.bearerToken, config.oauth?.access, config.oauth?.refresh) + config.headers.values
        val safe = secrets.filter { it.isNotBlank() }.fold(raw) { text, secret -> text.replace(secret, "•••") }
        Result.failure(McpProtocolException(safe))
    }
}
