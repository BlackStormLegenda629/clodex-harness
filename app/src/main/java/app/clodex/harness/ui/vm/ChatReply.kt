package app.clodex.harness.ui.vm

import app.clodex.harness.data.ClaudeException
import app.clodex.harness.data.agent.AgentEvent
import app.clodex.harness.ui.components.ThinkingPresentation
import kotlinx.coroutines.flow.Flow

data class ReplyOutcome(val text: String, val seconds: Int, val steps: List<String>)

class EmptyReplyException : ClaudeException("модель завершила ответ без текста")

const val STEP_ANALYZE = "Анализирую запрос"
const val STEP_ANSWER = "Формирую ответ"

/** Chat list subtitle: an active reply reads «В работе · только что» until it finishes. */
fun chatStatus(generating: Boolean, relative: String): String = if (generating) "В работе · только что" else relative

/**
 * Folds agent events into what the chat shows while a reply streams. Steps are factual only:
 * provider summaries and tool events; the neutral pair is used only when nothing richer arrived.
 */
class ReplyProgress(val chatId: Long, val startedAt: Long) {
    private val text = StringBuilder()
    private val rich = mutableListOf<String>()
    private var completedText: String? = null

    val answer: String get() = completedText ?: text.toString()

    val steps: List<String>
        get() = rich.toList().ifEmpty { if (text.isEmpty()) listOf(STEP_ANALYZE) else listOf(STEP_ANALYZE, STEP_ANSWER) }

    fun apply(ev: AgentEvent) {
        when (ev) {
            is AgentEvent.TextDelta -> text.append(ev.text)
            is AgentEvent.ThinkingStep -> addStep(ev.text)
            is AgentEvent.ToolApprovalRequired -> addStep("Жду подтверждения · ${ev.source} · ${ev.tool}")
            is AgentEvent.Completed -> completedText = ev.text
            is AgentEvent.ToolStarted, is AgentEvent.ToolFinished -> Unit
        }
    }

    private fun addStep(s: String) {
        val t = s.trim()
        if (t.isNotEmpty() && rich.lastOrNull() != t) rich += t
    }

    fun seconds(now: Long): Int = ((now - startedAt + 999) / 1000).toInt().coerceAtLeast(1)

    fun live(): ThinkingPresentation = ThinkingPresentation(chatId, thinking = true, startedAt = startedAt, steps = steps)

    /** Collects [events], calling [onUpdate] after each one; a blank final answer is an error. */
    suspend fun consume(events: Flow<AgentEvent>, clock: () -> Long, onUpdate: (ReplyProgress) -> Unit): ReplyOutcome {
        onUpdate(this)
        events.collect { apply(it); onUpdate(this) }
        val final = answer
        if (final.isBlank()) throw EmptyReplyException()
        return ReplyOutcome(final, seconds(clock()), steps)
    }
}
