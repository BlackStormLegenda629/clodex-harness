package app.clodex.harness.data.agent

import app.clodex.harness.data.ClaudeException
import app.clodex.harness.data.openAiRoot
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

internal fun isOpenRouterSearchEndpoint(base: String): Boolean =
    openAiRoot(base) == "https://openrouter.ai/api/v1"

/**
 * OpenAI-compatible Chat Completions with streamed text and function tools.
 * `reasoning_content`/`reasoning` (raw reasoning, e.g. DeepSeek) is dropped; only explicit
 * summary fields (`reasoning_summary`, `reasoning_details[type=reasoning.summary]`) become [ProviderEvent.SummaryDelta].
 */
class OpenAiAgentClient(
    private val http: HttpClient,
    private val base: String,
    private val apiKey: String,
    private val headers: Map<String, String> = emptyMap(),
    private val limits: AgentLimits = AgentLimits(),
) : ProviderAgentClient {
    private class Call { var id = ""; var name = ""; val args = StringBuilder(); var announced = false }

    override fun stream(request: ProviderRequest): Flow<ProviderEvent> = flow {
        val openRouter = isOpenRouterSearchEndpoint(base)
        if (request.webSearch && !openRouter) {
            throw ClaudeException("Веб-поиск недоступен для этого провайдера. Выключите «Веб-поиск» в меню «+» или смените провайдера.")
        }
        val calls = sortedMapOf<Int, Call>()
        val sources = WebSources()
        var finish: String? = null
        val auth = mapOf("Authorization" to "Bearer $apiKey")
        http.postSse(openAiRoot(base) + "/chat/completions", auth + headers, openAiBody(request, webPlugin = request.webSearch).toString(), limits.maxStreamBytes) { data ->
            if (data.trim() == "[DONE]") return@postSse false
            val ev = runCatching { wireJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@postSse true
            ev.obj("error")?.let { throw ClaudeException(it.str("message") ?: "Ошибка потока") }
            val choice = ev.arr("choices")?.firstOrNull() as? JsonObject ?: return@postSse true
            val delta = choice.obj("delta")
            if (delta != null) {
                delta.str("content")?.takeIf { it.isNotEmpty() }?.let { emit(ProviderEvent.TextDelta(it)) }
                if (openRouter) delta.arr("annotations")?.forEach { el ->
                    val annotation = el as? JsonObject
                    if (annotation?.str("type") == "url_citation") sources.add(annotation.obj("url_citation")?.str("url"))
                }
                delta.str("reasoning_summary")?.takeIf { it.isNotBlank() }?.let { emit(ProviderEvent.SummaryDelta(it)) }
                delta.arr("reasoning_details")?.forEach { d ->
                    val o = d as? JsonObject ?: return@forEach
                    if (o.str("type") == "reasoning.summary") o.str("summary")?.takeIf { it.isNotBlank() }?.let { emit(ProviderEvent.SummaryDelta(it)) }
                }
                delta.arr("tool_calls")?.forEachIndexed { pos, el ->
                    val o = el as? JsonObject ?: return@forEachIndexed
                    val idx = (o["index"] as? JsonPrimitive)?.intOrNull ?: pos
                    val c = calls.getOrPut(idx) { Call() }
                    o.str("id")?.takeIf { it.isNotEmpty() }?.let { c.id = it }
                    val fn = o.obj("function")
                    fn?.str("name")?.takeIf { it.isNotEmpty() }?.let { c.name = it }
                    fn?.str("arguments")?.let { c.args.append(it) }
                    if (!c.announced && c.name.isNotEmpty()) {
                        c.announced = true
                        emit(ProviderEvent.ToolCallDelta(c.id, c.name))
                    }
                }
            }
            choice.str("finish_reason")?.let { finish = it }
            true
        }
        calls.forEach { (idx, c) ->
            if (c.name.isNotEmpty()) {
                emit(ProviderEvent.ToolCallComplete(ProviderToolCall(c.id.ifEmpty { "call_$idx" }, c.name, parseToolArguments(c.args.toString()))))
            }
        }
        sources.markdown()?.let { emit(ProviderEvent.TextDelta(it)) }
        emit(ProviderEvent.Completed(finish))
    }
}

internal fun openAiBody(r: ProviderRequest, webPlugin: Boolean = false): JsonObject = buildJsonObject {
    put("model", r.model)
    put("stream", true)
    put("max_tokens", r.maxTokens)
    if (webPlugin) putJsonArray("plugins") { addJsonObject { put("id", "web") } }
    putJsonArray("messages") {
        r.system?.takeIf { it.isNotBlank() }?.let { s -> addJsonObject { put("role", "system"); put("content", s) } }
        r.messages.forEach { m ->
            when (m) {
                is AgentMessage.User -> addJsonObject { put("role", "user"); put("content", m.text) }
                is AgentMessage.Assistant -> addJsonObject {
                    put("role", "assistant")
                    if (m.toolCalls.isEmpty()) put("content", m.text)
                    else {
                        if (m.text.isNotBlank()) put("content", m.text) else put("content", JsonNull)
                        putJsonArray("tool_calls") {
                            m.toolCalls.forEach { c ->
                                addJsonObject {
                                    put("id", c.id); put("type", "function")
                                    putJsonObject("function") {
                                        put("name", c.name)
                                        put("arguments", if (c.invalidArguments) "{}" else c.arguments.toString())
                                    }
                                }
                            }
                        }
                    }
                }
                is AgentMessage.ToolResult -> addJsonObject {
                    put("role", "tool"); put("tool_call_id", m.callId); put("content", m.result.text)
                }
            }
        }
    }
    if (r.tools.isNotEmpty()) putJsonArray("tools") {
        r.tools.forEach { t ->
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", t.name)
                    if (t.description.isNotBlank()) put("description", t.description)
                    put("parameters", objectSchema(t.inputSchema))
                }
            }
        }
    }
}
