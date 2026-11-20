package app.clodex.harness.data.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/** A tool call waiting for the user's decision. [source] is the connector, [tool] the bare tool name. */
data class PendingToolCall(val call: ProviderToolCall, val title: String, val source: String, val tool: String,
    val permission: app.clodex.harness.data.mcp.McpPermission? = null)

enum class ToolDecision { ALLOW, ALLOW_CHAT, DENY }

interface ToolApprovalGate { suspend fun await(call: PendingToolCall): ToolDecision }

class RememberingApprovalGate(private val chatId: Long, private val repo: app.clodex.harness.data.mcp.McpRepository, private val ui: ToolApprovalGate) : ToolApprovalGate {
    override suspend fun await(call: PendingToolCall): ToolDecision {
        val permission = call.permission
        if (permission != null && repo.isAllowed(permission, chatId)) return ToolDecision.ALLOW
        val decision = ui.await(call)
        if (decision != ToolDecision.ALLOW_CHAT) return decision
        // A failed remember must not retract consent for the call already approved.
        if (permission != null) repo.remember(permission, chatId)
        return ToolDecision.ALLOW
    }
}

/** UI-backed gate: exposes the pending call and suspends until [resolve]; cancellation clears it. */
class InteractiveApprovalGate : ToolApprovalGate {
    private val _pending = MutableStateFlow<PendingToolCall?>(null)
    val pending: StateFlow<PendingToolCall?> = _pending
    private var answer: CompletableDeferred<ToolDecision>? = null

    override suspend fun await(call: PendingToolCall): ToolDecision {
        val d = CompletableDeferred<ToolDecision>()
        answer = d
        _pending.value = call
        try {
            return d.await()
        } finally {
            if (answer === d) answer = null
            if (_pending.value === call) _pending.value = null
        }
    }

    fun resolve(decision: ToolDecision) { answer?.complete(decision) }
}

/**
 * The only owner of the provider ↔ tool loop. Each round streams one provider reply; requested tools run
 * (read-only directly, everything else after approval) and their results feed the next round.
 */
class AgentRunner(
    private val provider: ProviderAgentClient,
    private val approval: ToolApprovalGate,
    private val limits: AgentLimits = AgentLimits(),
) {
    fun run(request: AgentRequest, tools: List<AgentTool>): Flow<AgentEvent> = flow {
        val byName = tools.filter { AgentToolSpec.isValidName(it.spec.name) }.associateBy { it.spec.name }
        val specs = byName.values.map { it.spec }
        val messages = request.messages.toMutableList()
        val answer = StringBuilder()
        var toolRounds = 0
        while (true) {
            val text = StringBuilder()
            val calls = mutableListOf<ProviderToolCall>()
            val req = ProviderRequest(request.model, request.maxTokens, request.system, messages.toList(), specs, request.webSearch)
            provider.stream(req).collect { ev ->
                when (ev) {
                    is ProviderEvent.TextDelta -> {
                        if (text.isEmpty() && answer.isNotEmpty()) { answer.append("\n\n"); emit(AgentEvent.TextDelta("\n\n")) }
                        text.append(ev.text); answer.append(ev.text)
                        emit(AgentEvent.TextDelta(ev.text))
                    }
                    is ProviderEvent.SummaryDelta -> AgentText.summary(ev.text)?.let { emit(AgentEvent.ThinkingStep(it)) }
                    is ProviderEvent.ToolCallDelta -> emit(AgentEvent.ThinkingStep(startLabel(ev.name, byName[ev.name])))
                    is ProviderEvent.ToolCallComplete -> calls += ev.call
                    is ProviderEvent.Completed -> Unit
                }
            }
            if (calls.isEmpty()) {
                emit(AgentEvent.Completed(answer.toString()))
                return@flow
            }
            if (toolRounds >= limits.maxRounds) {
                throw AgentException("Модель запросила инструменты больше ${limits.maxRounds} раз подряд — остановлено")
            }
            toolRounds++
            messages += AgentMessage.Assistant(text.toString(), calls.toList())
            for (call in calls) {
                messages += AgentMessage.ToolResult(call.id, call.name, execute(call, byName[call.name]))
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.execute(call: ProviderToolCall, tool: AgentTool?): AgentToolResult {
        if (tool == null) {
            emit(AgentEvent.ThinkingStep(AgentText.summary("Инструмент недоступен · ${call.name}")!!))
            return AgentToolResult.error("Инструмент ${call.name} недоступен")
        }
        val title = tool.spec.title
        if (call.invalidArguments) {
            emit(AgentEvent.ThinkingStep(AgentText.summary("Некорректные аргументы · $title")!!))
            return AgentToolResult.error("Аргументы вызова не являются JSON-объектом")
        }
        if (!tool.spec.readOnly) {
            val source = title.substringBefore(" · ", title)
            val bare = title.substringAfter(" · ", call.name)
            emit(AgentEvent.ToolApprovalRequired(call, source, bare))
            if (approval.await(PendingToolCall(call, title, source, bare, tool.spec.permission)) == ToolDecision.DENY) {
                emit(AgentEvent.ThinkingStep(AgentText.summary("Вызов отклонён · $title")!!))
                return AgentToolResult.error("Пользователь отклонил вызов инструмента")
            }
        }
        emit(AgentEvent.ToolStarted(call, title))
        val raw = try {
            tool.invoke(call.arguments)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AgentToolResult.error(e.message ?: "Ошибка инструмента")
        }
        val result = AgentToolResult.of(raw.text, raw.isError, limits.maxToolResultBytes)
        emit(AgentEvent.ToolFinished(call, title, result.isError))
        emit(AgentEvent.ThinkingStep(AgentText.summary((if (result.isError) "Инструмент вернул ошибку · " else "Получен результат · ") + title)!!))
        return result
    }

    private fun startLabel(name: String, tool: AgentTool?): String = when {
        name == "web_search" && tool == null -> "Ищу в интернете"
        tool != null -> AgentText.summary("Вызываю ${tool.spec.title}")!!
        else -> AgentText.summary("Вызываю $name")!!
    }
}
