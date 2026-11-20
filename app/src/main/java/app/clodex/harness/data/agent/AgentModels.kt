package app.clodex.harness.data.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/** Conversation history in a provider-neutral shape. `toString` never prints content. */
sealed interface AgentMessage {
    class User(val text: String) : AgentMessage {
        override fun toString() = "User(${text.length} chars)"
    }

    class Assistant(val text: String, val toolCalls: List<ProviderToolCall> = emptyList()) : AgentMessage {
        override fun toString() = "Assistant(${text.length} chars, ${toolCalls.size} tool calls)"
    }

    class ToolResult(val callId: String, val name: String, val result: AgentToolResult) : AgentMessage {
        override fun toString() = "ToolResult($callId, $name, error=${result.isError})"
    }
}

data class AgentToolSpec(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    /** Only an explicit `readOnlyHint: true` lets a tool run without approval. */
    val readOnly: Boolean = false,
    /** Human label for steps and approval, e.g. "GitHub · search_issues". */
    val title: String = name,
    val permission: app.clodex.harness.data.mcp.McpPermission? = null,
) {
    companion object {
        private val NAME = Regex("^[A-Za-z0-9_-]{1,64}$")
        fun isValidName(name: String) = NAME.matches(name)
    }
}

class AgentToolResult private constructor(val text: String, val isError: Boolean, val truncated: Boolean) {
    override fun toString() = "AgentToolResult(${text.length} chars, error=$isError, truncated=$truncated)"

    companion object {
        const val TRUNCATED_MARK = "\n…[результат обрезан]"
        const val DEFAULT_MAX_BYTES = 64 * 1024

        fun of(text: String, isError: Boolean = false, maxBytes: Int = DEFAULT_MAX_BYTES): AgentToolResult {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size <= maxBytes) return AgentToolResult(text, isError, false)
            val budget = (maxBytes - TRUNCATED_MARK.toByteArray(Charsets.UTF_8).size).coerceAtLeast(0)
            var cut = budget
            // Do not split a multi-byte UTF-8 sequence.
            while (cut > 0 && (bytes[cut].toInt() and 0xC0) == 0x80) cut--
            return AgentToolResult(String(bytes, 0, cut, Charsets.UTF_8) + TRUNCATED_MARK, isError, true)
        }

        fun error(message: String) = of(message, isError = true)
    }
}

/** A complete tool request from the model. Arguments may hold secrets, so `toString` omits them. */
class ProviderToolCall(val id: String, val name: String, val arguments: JsonObject) {
    override fun toString() = "ProviderToolCall($id, $name)"
    override fun equals(other: Any?) = other is ProviderToolCall && other.id == id && other.name == name && other.arguments == arguments
    override fun hashCode() = 31 * id.hashCode() + name.hashCode()

    val invalidArguments: Boolean get() = INVALID_ARGUMENTS in arguments

    companion object {
        /** Set when the model streamed arguments that are not a JSON object. */
        const val INVALID_ARGUMENTS = "__invalid_json"
    }
}

class ProviderRequest(
    val model: String,
    val maxTokens: Int,
    val system: String?,
    val messages: List<AgentMessage>,
    val tools: List<AgentToolSpec>,
    val webSearch: Boolean,
) {
    override fun toString() = "ProviderRequest($model, ${messages.size} messages, ${tools.size} tools, web=$webSearch)"
}

sealed interface ProviderEvent {
    data class TextDelta(val text: String) : ProviderEvent
    /** Only explicit provider summary fields; raw `thinking` content never becomes an event. */
    data class SummaryDelta(val text: String) : ProviderEvent
    /** The model started a tool call; arguments are still streaming. */
    data class ToolCallDelta(val id: String, val name: String) : ProviderEvent
    data class ToolCallComplete(val call: ProviderToolCall) : ProviderEvent
    data class Completed(val stopReason: String?) : ProviderEvent
}

interface ProviderAgentClient {
    fun stream(request: ProviderRequest): Flow<ProviderEvent>
}

data class AgentRequest(
    val model: String,
    val maxTokens: Int,
    val system: String?,
    val messages: List<AgentMessage>,
    val webSearch: Boolean,
)

/** UI-safe events: factual steps, answer text and tool lifecycle. */
sealed interface AgentEvent {
    data class ThinkingStep(val text: String) : AgentEvent
    data class TextDelta(val text: String) : AgentEvent
    data class ToolApprovalRequired(val call: ProviderToolCall, val source: String, val tool: String) : AgentEvent
    data class ToolStarted(val call: ProviderToolCall, val title: String) : AgentEvent
    data class ToolFinished(val call: ProviderToolCall, val title: String, val isError: Boolean) : AgentEvent
    data class Completed(val text: String) : AgentEvent
}

interface AgentTool {
    val spec: AgentToolSpec
    suspend fun invoke(arguments: JsonObject): AgentToolResult
}

data class AgentLimits(
    val maxRounds: Int = 8,
    val maxToolResultBytes: Int = AgentToolResult.DEFAULT_MAX_BYTES,
    val maxStreamBytes: Int = 1024 * 1024,
)

class AgentException(message: String) : Exception(message)

object AgentText {
    const val MAX_STEP = 240

    /** Normalizes an explicit summary/event line for display; blank input yields null. */
    fun summary(raw: String): String? =
        raw.replace(Regex("\\s+"), " ").trim().take(MAX_STEP).ifEmpty { null }
}
