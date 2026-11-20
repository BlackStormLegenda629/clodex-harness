package app.clodex.harness.data.agent

import app.clodex.harness.data.ClaudeException
import app.clodex.harness.data.anthropicRoot
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/** Anthropic Messages API with streamed text and client tools. Raw `thinking` deltas are dropped. */
class AnthropicAgentClient(
    private val http: HttpClient,
    private val base: String,
    private val apiKey: String,
    private val headers: Map<String, String> = emptyMap(),
    private val limits: AgentLimits = AgentLimits(),
) : ProviderAgentClient {
    private class ToolBlock(val id: String, val name: String) { val json = StringBuilder() }

    override fun stream(request: ProviderRequest): Flow<ProviderEvent> = flow {
        val blocks = mutableMapOf<Int, ToolBlock>()
        val sources = WebSources()
        var stop: String? = null
        val auth = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")
        http.postSse(anthropicRoot(base) + "/messages", auth + headers, anthropicBody(request).toString(), limits.maxStreamBytes) { data ->
            val ev = runCatching { wireJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@postSse true
            val index = (ev["index"] as? JsonPrimitive)?.intOrNull ?: -1
            when (ev.str("type")) {
                "content_block_start" -> {
                    val b = ev.obj("content_block")
                    val id = b?.str("id").orEmpty(); val name = b?.str("name").orEmpty()
                    when (b?.str("type")) {
                        "tool_use" -> { blocks[index] = ToolBlock(id, name); emit(ProviderEvent.ToolCallDelta(id, name)) }
                        "server_tool_use" -> emit(ProviderEvent.ToolCallDelta(id, name))
                        "web_search_tool_result" -> b.obj("content")?.let { error ->
                            if (error.str("type") == "web_search_tool_result_error") {
                                throw ClaudeException("Веб-поиск Anthropic: ${error.str("error_code") ?: "ошибка поиска"}")
                            }
                        }
                    }
                }
                "content_block_delta" -> {
                    val d = ev.obj("delta")
                    when (d?.str("type")) {
                        "text_delta" -> d.str("text")?.takeIf { it.isNotEmpty() }?.let { emit(ProviderEvent.TextDelta(it)) }
                        "input_json_delta" -> blocks[index]?.json?.append(d.str("partial_json").orEmpty())
                        "citations_delta" -> d.obj("citation")?.let { citation ->
                            if (citation.str("type") == "web_search_result_location") sources.add(citation.str("url"))
                        }
                        // thinking_delta / signature_delta: private reasoning, never surfaced.
                    }
                }
                "content_block_stop" -> blocks.remove(index)?.let { b ->
                    emit(ProviderEvent.ToolCallComplete(ProviderToolCall(b.id, b.name, parseToolArguments(b.json.toString()))))
                }
                "message_delta" -> ev.obj("delta")?.str("stop_reason")?.let { stop = it }
                "message_stop" -> return@postSse false
                "error" -> throw ClaudeException(ev.obj("error")?.str("message") ?: "Ошибка потока")
            }
            true
        }
        sources.markdown()?.let { emit(ProviderEvent.TextDelta(it)) }
        emit(ProviderEvent.Completed(stop))
    }
}

internal fun anthropicBody(r: ProviderRequest): JsonObject = buildJsonObject {
    put("model", r.model)
    put("max_tokens", r.maxTokens)
    put("stream", true)
    r.system?.takeIf { it.isNotBlank() }?.let { put("system", it) }
    putJsonArray("messages") {
        var pendingResults = mutableListOf<AgentMessage.ToolResult>()
        fun flushResults() {
            if (pendingResults.isEmpty()) return
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    pendingResults.forEach { t ->
                        addJsonObject {
                            put("type", "tool_result"); put("tool_use_id", t.callId)
                            put("content", t.result.text)
                            if (t.result.isError) put("is_error", true)
                        }
                    }
                }
            }
            pendingResults = mutableListOf()
        }
        r.messages.forEach { m ->
            when (m) {
                is AgentMessage.ToolResult -> pendingResults += m
                is AgentMessage.User -> { flushResults(); addJsonObject { put("role", "user"); put("content", m.text) } }
                is AgentMessage.Assistant -> {
                    flushResults()
                    addJsonObject {
                        put("role", "assistant")
                        if (m.toolCalls.isEmpty()) put("content", m.text)
                        else putJsonArray("content") {
                            if (m.text.isNotBlank()) addJsonObject { put("type", "text"); put("text", m.text) }
                            m.toolCalls.forEach { c ->
                                addJsonObject {
                                    put("type", "tool_use"); put("id", c.id); put("name", c.name)
                                    put("input", if (c.invalidArguments) JsonObject(emptyMap()) else c.arguments)
                                }
                            }
                        }
                    }
                }
            }
        }
        flushResults()
    }
    if (r.tools.isNotEmpty() || r.webSearch) putJsonArray("tools") {
        if (r.webSearch) addJsonObject { put("type", "web_search_20250305"); put("name", "web_search") }
        r.tools.forEach { t ->
            addJsonObject {
                put("name", t.name)
                if (t.description.isNotBlank()) put("description", t.description)
                put("input_schema", objectSchema(t.inputSchema))
            }
        }
    }
}
