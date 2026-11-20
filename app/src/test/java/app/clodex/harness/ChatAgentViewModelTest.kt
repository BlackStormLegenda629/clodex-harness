package app.clodex.harness

import app.clodex.harness.data.agent.AgentEvent
import app.clodex.harness.data.agent.ProviderToolCall
import app.clodex.harness.ui.components.ThinkingPresentation
import app.clodex.harness.ui.vm.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class ChatAgentViewModelTest {
    @Test fun `live text and neutral steps while nothing richer arrives`() = runBlocking {
        val p = ReplyProgress(chatId = 7, startedAt = 1_000)
        val seen = mutableListOf<Pair<String, ThinkingPresentation>>()
        val out = p.consume(flowOf(AgentEvent.TextDelta("При"), AgentEvent.TextDelta("вет"), AgentEvent.Completed("Привет")), { 3_200 }) {
            seen += it.answer to it.live()
        }
        assertEquals(listOf(STEP_ANALYZE), seen.first().second.steps)
        assertTrue(seen.first().second.thinking)
        assertEquals(7L, seen.first().second.chatId)
        assertEquals("При", seen[1].first)
        assertEquals(listOf(STEP_ANALYZE, STEP_ANSWER), seen[1].second.steps)
        assertEquals(ReplyOutcome("Привет", 3, listOf(STEP_ANALYZE, STEP_ANSWER)), out)
    }

    @Test fun `factual steps replace neutral ones and duplicates collapse`() = runBlocking {
        val call = ProviderToolCall("c", "gh__create", JsonObject(emptyMap()))
        val p = ReplyProgress(1, 0)
        val out = p.consume(
            flowOf(
                AgentEvent.ThinkingStep("Вызываю GitHub · create"),
                AgentEvent.ThinkingStep("Вызываю GitHub · create"),
                AgentEvent.ToolApprovalRequired(call, "GitHub", "create"),
                AgentEvent.ThinkingStep("Получен результат · GitHub · create"),
                AgentEvent.TextDelta("Готово"),
                AgentEvent.Completed("Готово"),
            ),
            { 500 },
        ) {}
        assertEquals(
            listOf("Вызываю GitHub · create", "Жду подтверждения · GitHub · create", "Получен результат · GitHub · create"),
            out.steps,
        )
        assertEquals(1, out.seconds)
    }

    @Test fun `empty completion is an error, not an ellipsis`() = runBlocking {
        val e = runCatching { ReplyProgress(1, 0).consume(flowOf(AgentEvent.Completed("  ")), { 10 }) {} }.exceptionOrNull()
        assertTrue(e is EmptyReplyException)
    }

    @Test fun `partial answer survives a failure`() = runBlocking {
        val p = ReplyProgress(1, 0)
        val e = runCatching {
            p.consume(flow { emit(AgentEvent.TextDelta("Часть")); throw IllegalStateException("сеть") }, { 10 }) {}
        }.exceptionOrNull()
        assertEquals("сеть", e?.message)
        assertEquals("Часть", p.answer)
    }

    @Test fun `generating chats read as in progress`() {
        assertEquals("В работе · только что", chatStatus(true, "Сегодня"))
        assertEquals("Сегодня", chatStatus(false, "Сегодня"))
    }
}
