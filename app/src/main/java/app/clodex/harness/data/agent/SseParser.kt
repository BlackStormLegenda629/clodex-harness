package app.clodex.harness.data.agent

import app.clodex.harness.data.ClaudeException
import app.clodex.harness.data.providerErrorText
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Line-oriented Server-Sent Events: returns each complete `data` payload when its event ends. */
class SseParser {
    private val data = StringBuilder()
    private var hasData = false

    fun feed(line: String): List<String> {
        val l = line.removeSuffix("\r")
        if (l.isEmpty()) return flush()
        if (l.startsWith(":")) return emptyList()
        val field = l.substringBefore(':')
        if (field != "data") return emptyList()
        val value = if (':' in l) l.substringAfter(':').removePrefix(" ") else ""
        if (hasData) data.append('\n')
        data.append(value)
        hasData = true
        return emptyList()
    }

    /** End of stream: a trailing event without a blank line still counts. */
    fun flush(): List<String> {
        if (!hasData) return emptyList()
        val out = data.toString()
        data.setLength(0); hasData = false
        return listOf(out)
    }
}

internal val wireJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** POSTs a streaming request and hands every SSE payload to [onData] until it returns false. */
internal suspend fun HttpClient.postSse(
    url: String,
    headers: Map<String, String>,
    body: String,
    maxBytes: Int,
    onData: suspend (String) -> Boolean,
) {
    preparePost(url) {
        headers.forEach { (k, v) -> header(k, v) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }.execute { resp ->
        if (!resp.status.isSuccess()) throw ClaudeException(providerErrorText(resp.status.value, resp.bodyAsText()))
        val ch = resp.bodyAsChannel()
        val sse = SseParser()
        var total = 0L
        while (true) {
            val line = ch.readUTF8Line() ?: break
            total += line.toByteArray(Charsets.UTF_8).size + 1
            if (total > maxBytes) throw AgentException("Ответ провайдера превысил ${maxBytes / 1024} КБ — поток остановлен")
            for (d in sse.feed(line)) if (!onData(d)) return@execute
        }
        for (d in sse.flush()) if (!onData(d)) return@execute
    }
}

/** Tool arguments arrive as a JSON string; malformed JSON is kept so the runner can report it to the model. */
internal fun parseToolArguments(raw: String): JsonObject {
    if (raw.isBlank()) return JsonObject(emptyMap())
    return runCatching { wireJson.parseToJsonElement(raw).jsonObject }
        .getOrElse { JsonObject(mapOf(ProviderToolCall.INVALID_ARGUMENTS to JsonPrimitive(raw.take(2000)))) }
}

/** Providers require an object schema; MCP servers sometimes omit `type`/`properties`. */
internal fun objectSchema(schema: JsonObject): JsonObject = JsonObject(
    buildMap {
        putAll(schema)
        if ("type" !in schema) put("type", JsonPrimitive("object"))
        if ("properties" !in schema) put("properties", JsonObject(emptyMap()))
    },
)

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
