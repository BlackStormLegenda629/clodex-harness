package app.clodex.harness

import app.clodex.harness.data.agent.*
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentModelTest {
    @Test fun `tool names must be provider safe`() {
        assertTrue(AgentToolSpec.isValidName("github__search_issues"))
        assertTrue(AgentToolSpec.isValidName("read-file"))
        assertFalse(AgentToolSpec.isValidName(""))
        assertFalse(AgentToolSpec.isValidName("has space"))
        assertFalse(AgentToolSpec.isValidName("dots.not.allowed"))
        assertFalse(AgentToolSpec.isValidName("x".repeat(65)))
    }

    @Test fun `tools are mutating unless explicitly read only`() {
        assertFalse(AgentToolSpec("a", "", JsonObject(emptyMap())).readOnly)
        assertTrue(AgentToolSpec("a", "", JsonObject(emptyMap()), readOnly = true).readOnly)
    }

    @Test fun `summary text is trimmed, collapsed and bounded`() {
        assertEquals("Ищу файлы проекта", AgentText.summary("  Ищу   файлы\nпроекта  "))
        assertEquals(null, AgentText.summary("   "))
        assertEquals(240, AgentText.summary("а".repeat(500))!!.length)
    }

    @Test fun `tool results are truncated to the byte limit with a marker`() {
        val big = "я".repeat(40_000) // 80 000 bytes
        val r = AgentToolResult.of(big, maxBytes = 64 * 1024)
        assertTrue(r.truncated)
        assertTrue(r.text.toByteArray().size <= 64 * 1024)
        assertTrue(r.text.endsWith(AgentToolResult.TRUNCATED_MARK))
        val small = AgentToolResult.of("ok")
        assertFalse(small.truncated)
        assertEquals("ok", small.text)
    }

    @Test fun `events and requests never print secrets or arguments`() {
        val call = ProviderToolCall("call_1", "github__create_issue", JsonObject(mapOf("token" to kotlinx.serialization.json.JsonPrimitive("sk-secret"))))
        val ev = AgentEvent.ToolApprovalRequired(call, "GitHub", "create_issue")
        assertFalse(ev.toString().contains("sk-secret"))
        assertFalse(call.toString().contains("sk-secret"))
        val req = ProviderRequest(
            model = "m", maxTokens = 10, system = "секретная инструкция", messages = listOf(AgentMessage.User("пароль 123")),
            tools = emptyList(), webSearch = false,
        )
        assertFalse(req.toString().contains("пароль"))
        assertFalse(req.toString().contains("секретная"))
    }
}
