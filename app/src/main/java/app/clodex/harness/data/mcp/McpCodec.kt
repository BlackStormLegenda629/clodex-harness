package app.clodex.harness.data.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object McpCodec {
    const val MAX_BODY_CHARS = 1_000_000
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun request(id: Long, method: String, params: JsonElement? = null): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", method)
        if (params != null) put("params", params)
    }.toString()

    fun notification(method: String, params: JsonElement? = null): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", method)
        if (params != null) put("params", params)
    }.toString()

    fun parsePayload(contentType: String?, body: String, expectedId: Long): JsonObject {
        if (body.length > MAX_BODY_CHARS) throw McpProtocolException("Ответ MCP слишком большой")
        val payloads = if (contentType.orEmpty().startsWith("text/event-stream", ignoreCase = true)) {
            ssePayloads(body)
        } else listOf(body.trim())

        var sawJson = false
        payloads.filter { it.isNotBlank() }.forEach { payload ->
            val envelope = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return@forEach
            sawJson = true
            if (envelope["id"]?.jsonPrimitive?.longOrNull != expectedId) return@forEach
            envelope["error"]?.let { raw ->
                val error = runCatching { raw.jsonObject }.getOrNull()
                val code = error?.get("code")?.jsonPrimitive?.contentOrNull ?: "error"
                val message = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "Ошибка протокола"
                throw McpProtocolException("MCP $code: $message")
            }
            val result = envelope["result"] ?: throw McpProtocolException("MCP не вернул result")
            return runCatching { result.jsonObject }.getOrElse { throw McpProtocolException("Некорректный result MCP") }
        }
        if (!sawJson) throw McpProtocolException("Сервер MCP вернул некорректный ответ")
        throw McpProtocolException("Ответ MCP не совпадает с запросом $expectedId")
    }

    private fun ssePayloads(body: String): List<String> {
        val events = ArrayList<String>()
        val data = ArrayList<String>()
        fun flush() {
            if (data.isNotEmpty()) events += data.joinToString("\n")
            data.clear()
        }
        body.lineSequence().forEach { line ->
            when {
                line.isEmpty() -> flush()
                line.startsWith("data:") -> data += line.removePrefix("data:").removePrefix(" ")
            }
        }
        flush()
        return events
    }
}
