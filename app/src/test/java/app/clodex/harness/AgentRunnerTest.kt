package app.clodex.harness

import app.clodex.harness.data.agent.*
import app.clodex.harness.data.mcp.McpRepository
import app.clodex.harness.data.mcp.McpConnectionConfig
import app.clodex.harness.data.mcp.McpToolSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class ScriptedProvider(private val rounds: List<List<ProviderEvent>>, private val repeatLast: Boolean = false) : ProviderAgentClient {
    val requests = mutableListOf<ProviderRequest>()
    override fun stream(request: ProviderRequest): Flow<ProviderEvent> {
        requests += request
        val i = requests.size - 1
        return flowOf(*(if (i < rounds.size) rounds[i] else if (repeatLast) rounds.last() else error("no round $i")).toTypedArray())
    }
}

class FakeTool(name: String, readOnly: Boolean, private val reply: (JsonObject) -> AgentToolResult = { AgentToolResult.of("ok") }) : AgentTool {
    override val spec = AgentToolSpec(name, "", JsonObject(emptyMap()), readOnly, "Acme · $name")
    val calls = mutableListOf<JsonObject>()
    override suspend fun invoke(arguments: JsonObject): AgentToolResult { calls += arguments; return reply(arguments) }
}

class FixedGate(private val decision: ToolDecision) : ToolApprovalGate {
    val asked = mutableListOf<PendingToolCall>()
    override suspend fun await(call: PendingToolCall): ToolDecision { asked += call; return decision }
}

class AgentRunnerTest {
    private val req = AgentRequest("m", 100, null, listOf(AgentMessage.User("hi")), webSearch = false)
    private fun callRound(id: String, name: String, args: JsonObject = JsonObject(emptyMap())) = listOf(
        ProviderEvent.ToolCallDelta(id, name),
        ProviderEvent.ToolCallComplete(ProviderToolCall(id, name, args)),
        ProviderEvent.Completed("tool_use"),
    )
    private val finalRound = listOf(ProviderEvent.TextDelta("Готово"), ProviderEvent.Completed("end_turn"))

    @Test fun `plain text completes without tools`() = runBlocking {
        val p = ScriptedProvider(listOf(listOf(ProviderEvent.TextDelta("При"), ProviderEvent.TextDelta("вет"), ProviderEvent.Completed("end_turn"))))
        val events = AgentRunner(p, FixedGate(ToolDecision.DENY)).run(req, emptyList()).toList()
        assertEquals(listOf(AgentEvent.TextDelta("При"), AgentEvent.TextDelta("вет"), AgentEvent.Completed("Привет")), events)
        assertEquals(1, p.requests.size)
    }

    @Test fun `read only tool runs without approval and its result continues the conversation`() = runBlocking {
        val tool = FakeTool("acme__find", readOnly = true) { AgentToolResult.of("3 results") }
        val gate = FixedGate(ToolDecision.DENY)
        val p = ScriptedProvider(listOf(callRound("c1", "acme__find", buildJsonObject { put("q", "x") }), finalRound))
        val events = AgentRunner(p, gate).run(req, listOf(tool)).toList()

        assertTrue(gate.asked.isEmpty())
        assertEquals(1, tool.calls.size)
        assertEquals(AgentEvent.Completed("Готово"), events.last())
        assertTrue(AgentEvent.ThinkingStep("Вызываю Acme · acme__find") in events)
        assertTrue(AgentEvent.ThinkingStep("Получен результат · Acme · acme__find") in events)
        val second = p.requests[1].messages
        assertTrue(second[1] is AgentMessage.Assistant)
        val result = second[2] as AgentMessage.ToolResult
        assertEquals("c1", result.callId)
        assertEquals("3 results", result.result.text)
        assertEquals(listOf("acme__find"), p.requests[0].tools.map { it.name })
    }

    @Test fun `mutating tool needs approval and denial is reported to the model`() = runBlocking {
        val tool = FakeTool("acme__delete", readOnly = false)
        val gate = FixedGate(ToolDecision.DENY)
        val p = ScriptedProvider(listOf(callRound("c1", "acme__delete"), finalRound))
        val events = AgentRunner(p, gate).run(req, listOf(tool)).toList()

        assertEquals(1, gate.asked.size)
        assertEquals("Acme", gate.asked.single().source)
        assertTrue(tool.calls.isEmpty())
        assertTrue(events.any { it is AgentEvent.ToolApprovalRequired })
        val result = p.requests[1].messages.last() as AgentMessage.ToolResult
        assertTrue(result.result.isError)
    }

    @Test fun `approved mutating tool runs`() = runBlocking {
        val tool = FakeTool("acme__delete", readOnly = false)
        val p = ScriptedProvider(listOf(callRound("c1", "acme__delete"), finalRound))
        AgentRunner(p, FixedGate(ToolDecision.ALLOW)).run(req, listOf(tool)).toList()
        assertEquals(1, tool.calls.size)
    }

    @Test fun `tool permission reaches approval`() = runBlocking {
        val permission = app.clodex.harness.data.mcp.McpPermission("acme", "write", "fingerprint")
        var invoked = false
        val tool = object : AgentTool {
            override val spec = AgentToolSpec("acme__write", "Change", JsonObject(emptyMap()), false, "Acme · write", permission)
            override suspend fun invoke(arguments: JsonObject): AgentToolResult { invoked = true; return AgentToolResult.of("ok") }
        }
        val gate = FixedGate(ToolDecision.ALLOW)
        val p = ScriptedProvider(listOf(callRound("c1", "acme__write"), finalRound))
        AgentRunner(p, gate).run(req, listOf(tool)).toList()
        assertEquals(permission, gate.asked.single().permission)
        assertTrue(invoked)
    }

    @Test fun `endless tool requests stop after eight rounds with an error`() = runBlocking {
        val tool = FakeTool("acme__find", readOnly = true)
        val p = ScriptedProvider(listOf(callRound("c", "acme__find")), repeatLast = true)
        val e = runCatching { AgentRunner(p, FixedGate(ToolDecision.ALLOW)).run(req, listOf(tool)).toList() }.exceptionOrNull()
        assertTrue(e is AgentException)
        assertEquals(8, tool.calls.size)
        assertEquals(9, p.requests.size)
    }

    @Test fun `unknown tool and failing tool return errors instead of crashing`() = runBlocking {
        val broken = FakeTool("acme__boom", readOnly = true) { throw IllegalStateException("boom") }
        val p = ScriptedProvider(listOf(callRound("c1", "ghost__x") + callRound("c2", "acme__boom"), finalRound))
        val events = AgentRunner(p, FixedGate(ToolDecision.ALLOW)).run(req, listOf(broken)).toList()
        val results = p.requests[1].messages.filterIsInstance<AgentMessage.ToolResult>()
        assertEquals(listOf("c1", "c2"), results.map { it.callId })
        assertTrue(results.all { it.result.isError })
        assertEquals("boom", results[1].result.text)
        assertEquals(AgentEvent.Completed("Готово"), events.last())
    }

    @Test fun `large tool results are truncated to 64KB`() = runBlocking {
        val tool = FakeTool("acme__dump", readOnly = true) { AgentToolResult.of("x".repeat(200_000), maxBytes = Int.MAX_VALUE) }
        val p = ScriptedProvider(listOf(callRound("c1", "acme__dump"), finalRound))
        AgentRunner(p, FixedGate(ToolDecision.ALLOW)).run(req, listOf(tool)).toList()
        val r = p.requests[1].messages.last() as AgentMessage.ToolResult
        assertTrue(r.result.truncated)
        assertTrue(r.result.text.toByteArray().size <= 64 * 1024)
    }

    @Test fun `text from several rounds is separated`() = runBlocking {
        val tool = FakeTool("acme__find", readOnly = true)
        val p = ScriptedProvider(listOf(listOf(ProviderEvent.TextDelta("Смотрю")) + callRound("c1", "acme__find"), finalRound))
        val events = AgentRunner(p, FixedGate(ToolDecision.ALLOW)).run(req, listOf(tool)).toList()
        assertEquals(AgentEvent.Completed("Смотрю\n\nГотово"), events.last())
    }

    @Test fun `cancelling while approval is pending never runs the tool and clears the prompt`() = runBlocking {
        val tool = FakeTool("acme__delete", readOnly = false)
        val gate = InteractiveApprovalGate()
        val p = ScriptedProvider(listOf(callRound("c1", "acme__delete"), finalRound))
        val job = launch { AgentRunner(p, gate).run(req, listOf(tool)).toList() }
        while (gate.pending.value == null) yield()
        assertEquals("acme__delete", gate.pending.value!!.call.name)
        job.cancel(); job.join()
        gate.resolve(ToolDecision.ALLOW)
        assertNull(gate.pending.value)
        assertTrue(tool.calls.isEmpty())
        assertEquals(1, p.requests.size)
    }

    @Test fun `interactive gate returns the user's decision`() = runBlocking {
        val gate = InteractiveApprovalGate()
        val call = PendingToolCall(ProviderToolCall("c", "t", JsonObject(emptyMap())), "A · t", "A", "t")
        val answer = CompletableDeferred<ToolDecision>()
        val job = launch { answer.complete(gate.await(call)) }
        while (gate.pending.value == null) yield()
        gate.resolve(ToolDecision.ALLOW)
        job.join()
        assertEquals(ToolDecision.ALLOW, answer.await())
        assertNull(gate.pending.value)
    }

    @Test fun `mcp tools are exposed with safe names, titles and read only hints`() = runBlocking {
        val store = FakeMcpConfigRepository()
        store.save(
            McpConnectionConfig(
                "gh", "https://x", tools = listOf(
                    McpToolSpec("search.issues", "Search", readOnlyHint = true),
                    McpToolSpec("create_issue", "Create"),
                ),
            ),
        )
        val gateway = FakeMcpGateway(Result.failure(IllegalStateException("unused")))
        val tools = mcpAgentTools(McpRepository(store, gateway)) { if (it == "gh") "GitHub" else it }
        assertEquals(2, tools.size)
        val search = tools.first { it.spec.title == "GitHub · search.issues" }
        assertTrue(AgentToolSpec.isValidName(search.spec.name))
        assertTrue(search.spec.readOnly)
        assertFalse(tools.first { it.spec.title == "GitHub · create_issue" }.spec.readOnly)
        assertEquals("gh__create_issue", tools.first { !it.spec.readOnly }.spec.name)
        search.invoke(JsonObject(emptyMap()))
        assertEquals(1, gateway.calls)
        assertEquals("search.issues", gateway.lastTool)
    }
}
