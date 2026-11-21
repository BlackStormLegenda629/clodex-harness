package app.clodex.harness.code

import app.clodex.harness.data.agent.AgentTool
import app.clodex.harness.data.agent.ProviderToolCall
import app.clodex.harness.data.code.CodeMode
import app.clodex.harness.data.code.CodeToolNames
import app.clodex.harness.data.code.SearchArg
import app.clodex.harness.data.code.WorkspaceApplyResult
import app.clodex.harness.data.code.WorkspaceChange
import app.clodex.harness.data.code.WorkspaceEntry
import app.clodex.harness.data.code.WorkspaceGateway
import app.clodex.harness.data.code.WorkspaceOverlay
import app.clodex.harness.data.code.WorkspacePath
import app.clodex.harness.data.code.WorkspaceRead
import app.clodex.harness.data.code.codeEventTitle
import app.clodex.harness.data.code.codeTools
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSearchToolsTest {
    private fun p(value: String) = WorkspacePath.parse(value).getOrThrow()

    private class FakeGateway(
        private val entries: Map<String, List<WorkspaceEntry>>,
        private val texts: Map<String, String>,
    ) : WorkspaceGateway {
        override suspend fun list(path: WorkspacePath) = Result.success(entries[path.value].orEmpty())
        override suspend fun read(path: WorkspacePath): Result<WorkspaceRead> {
            val text = texts[path.value] ?: return Result.failure(Exception("not found"))
            return Result.success(WorkspaceRead(path, text, text.toByteArray().size.toLong()))
        }
        override suspend fun apply(changes: List<WorkspaceChange>, message: String) =
            Result.success(WorkspaceApplyResult(emptyList()))
    }

    @Test fun invalidRegexReturnsErrorNotThrow() = runBlocking {
        assertTrue(SearchArg.compileRegex("[").isFailure)
        assertTrue(SearchArg.compileRegex("x".repeat(300)).isFailure)

        val overlay = WorkspaceOverlay(FakeGateway(emptyMap(), emptyMap()))
        val grep = codeTools(overlay, CodeMode.PLAN).first { it.spec.name == CodeToolNames.GREP }
        val args = buildJsonObject { put("pattern", JsonPrimitive("[")) }
        val result = grep.invoke(args)
        assertTrue(result.isError)
    }

    @Test fun grepRespectsLimits() = runBlocking {
        val entries = (0 until 100).map { WorkspaceEntry(p("f$it.txt"), false, 20, true) }
        val texts = entries.associate { it.path.value to "нормальная строка\nнужное здесь\n" }
        val overlay = WorkspaceOverlay(FakeGateway(mapOf("" to entries), texts))
        val grep = codeTools(overlay, CodeMode.PLAN).first { it.spec.name == CodeToolNames.GREP }
        val args = buildJsonObject { put("pattern", JsonPrimitive("нужное")) }
        val result = grep.invoke(args)
        assertFalse(result.isError)
        val lines = result.text.lineSequence().filter { it.contains("нужное") }.toList()
        assertEquals(30, lines.size)
        assertTrue(lines.first().contains("f") && lines.first().contains(":"))
    }

    @Test fun eventTitlesCoverNewTools() {
        fun call(name: String) = ProviderToolCall("id", name, JsonObject(emptyMap()))
        assertTrue(codeEventTitle(call(CodeToolNames.GREP), false)!!.contains("Нашёл"))
        assertTrue(codeEventTitle(call(CodeToolNames.FIND), false)!!.contains("Искал"))
    }
}
