package app.clodex.harness

import app.clodex.harness.data.agent.ProviderToolCall
import app.clodex.harness.data.code.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

/** In-memory workspace: [files] maps paths to text (null = binary). */
class FakeWorkspaceGateway(initial: Map<String, String?> = emptyMap()) : WorkspaceGateway {
    val files = initial.toMutableMap()
    val applied = mutableListOf<List<WorkspaceChange>>()
    var failApply: Exception? = null
    var commitSha: String? = null

    override suspend fun list(path: WorkspacePath): Result<List<WorkspaceEntry>> {
        val prefix = if (path.isRoot) "" else path.value + "/"
        val direct = files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct()
        if (direct.isEmpty() && !path.isRoot) return Result.failure(WorkspaceException("нет папки", notFound = true))
        return Result.success(direct.map { n ->
            val full = prefix + n
            val isFile = full in files
            WorkspaceEntry(WorkspacePath.parse(full).getOrThrow(), !isFile, files[full]?.length?.toLong() ?: 0, isFile && files[full] != null)
        })
    }

    override suspend fun read(path: WorkspacePath): Result<WorkspaceRead> {
        if (path.value !in files) return Result.failure(WorkspaceException("Файл не найден: $path", notFound = true))
        val t = files[path.value]
        return Result.success(WorkspaceRead(path, t, t?.length?.toLong() ?: 10, if (t == null) "двоичный файл" else null))
    }

    override suspend fun apply(changes: List<WorkspaceChange>, message: String): Result<WorkspaceApplyResult> {
        failApply?.let { return Result.failure(it) }
        applied += changes
        changes.forEach { files[it.path.value] = it.after }
        return Result.success(WorkspaceApplyResult(changes.map { it.path }, commitSha = commitSha, branch = commitSha?.let { "claude/x" }))
    }
}

class CodeAgentToolsTest {
    private fun args(path: String, content: String? = null) = buildJsonObject { put("path", path); content?.let { put("content", it) } }
    private fun tool(tools: List<app.clodex.harness.data.agent.AgentTool>, name: String) = tools.first { it.spec.name == name }

    @Test fun `plan mode has no write tool and all tools are provider safe`() {
        val overlay = WorkspaceOverlay(FakeWorkspaceGateway())
        assertEquals(listOf("workspace_list", "workspace_read"), codeTools(overlay, CodeMode.PLAN).map { it.spec.name })
        val edits = codeTools(overlay, CodeMode.EDITS)
        assertEquals(listOf("workspace_list", "workspace_read", "workspace_write"), edits.map { it.spec.name })
        assertTrue(edits.all { it.spec.readOnly && app.clodex.harness.data.agent.AgentToolSpec.isValidName(it.spec.name) })
        val schema = tool(edits, "workspace_write").spec.inputSchema.toString()
        assertTrue(schema.contains("\"content\"") && schema.contains("\"required\""))
    }

    @Test fun `staged writes are visible to reads but never touch the workspace`() = runBlocking {
        val gw = FakeWorkspaceGateway(mapOf("src/App.kt" to "a\nb\nc\n"))
        val overlay = WorkspaceOverlay(gw)
        val tools = codeTools(overlay, CodeMode.EDITS)
        val w = tool(tools, "workspace_write").invoke(args("src/App.kt", "a\nB\nc\n"))
        assertFalse(w.isError)
        assertTrue(w.text.contains("+1 −1"))
        assertEquals("a\nB\nc\n", tool(tools, "workspace_read").invoke(args("src/App.kt")).text)
        assertEquals("a\nb\nc\n", gw.files["src/App.kt"])
        assertTrue(gw.applied.isEmpty())
        val ch = overlay.changes().single()
        assertEquals("a\nb\nc\n", ch.before)
        assertEquals("a\nB\nc\n", ch.after)
    }

    @Test fun `new files have no before and appear in listings`() = runBlocking {
        val overlay = WorkspaceOverlay(FakeWorkspaceGateway(mapOf("README.md" to "x")))
        val tools = codeTools(overlay, CodeMode.AUTO)
        assertTrue(tool(tools, "workspace_write").invoke(args("docs/new/Guide.md", "# Guide")).text.contains("в конце задачи"))
        assertNull(overlay.changes().single().before)
        val root = tool(tools, "workspace_list").invoke(args("")).text
        assertTrue(root, root.contains("docs/") && root.contains("README.md"))
        assertTrue(tool(tools, "workspace_list").invoke(args("docs/new")).text.contains("docs/new/Guide.md"))
    }

    @Test fun `rewriting the same file keeps the original before and unchanged files are dropped`() = runBlocking {
        val overlay = WorkspaceOverlay(FakeWorkspaceGateway(mapOf("a.txt" to "1")))
        overlay.stage(WorkspacePath.parse("a.txt").getOrThrow(), "2")
        overlay.stage(WorkspacePath.parse("a.txt").getOrThrow(), "3")
        assertEquals("1", overlay.changes().single().before)
        overlay.stage(WorkspacePath.parse("a.txt").getOrThrow(), "1")
        assertTrue(overlay.changes().isEmpty())
    }

    @Test fun `binary, oversize and escaping paths are tool errors`() = runBlocking {
        val overlay = WorkspaceOverlay(FakeWorkspaceGateway(mapOf("img.dat" to null)))
        val tools = codeTools(overlay, CodeMode.EDITS)
        assertTrue(tool(tools, "workspace_read").invoke(args("img.dat")).isError)
        assertTrue(tool(tools, "workspace_write").invoke(args("img.dat", "x")).isError)
        assertTrue(tool(tools, "workspace_write").invoke(args("logo.png", "x")).isError)
        assertTrue(tool(tools, "workspace_write").invoke(args("big.txt", "x".repeat(600_000))).isError)
        assertTrue(tool(tools, "workspace_read").invoke(args("../etc/passwd")).isError)
        assertTrue(tool(tools, "workspace_write").invoke(args("a.txt")).isError)
        assertTrue(overlay.changes().isEmpty())
    }

    @Test fun `line diff counts additions and removals`() {
        assertEquals(DiffSummary(0, 0), TextDiff.summarize("a\nb", "a\nb"))
        assertEquals(DiffSummary(2, 0), TextDiff.summarize(null, "x\ny\n"))
        assertEquals(DiffSummary(1, 1), TextDiff.summarize("a\nb\nc", "a\nX\nc"))
        assertEquals(DiffSummary(1, 2), TextDiff.summarize("1\n2\n3\n4", "1\n4\n5"))
        val l = TextDiff.lines("a\nb\nc", "a\nc\nd")
        assertEquals(listOf(DiffKind.SAME, DiffKind.DEL, DiffKind.SAME, DiffKind.ADD), l.map { it.kind })
        assertEquals("+3 −3", TextDiff.total(listOf("a\nb\nc" to "a\nX\nc", "1\n2\n3\n4" to "1\n4\n5", "" to "z")).toString())
    }

    @Test fun `event titles come from real arguments`() {
        fun call(name: String, path: String) = ProviderToolCall("c", name, args(path))
        assertEquals("Прочитал src/App.kt", codeEventTitle(call("workspace_read", "src/App.kt"), false))
        assertEquals("Просмотрел корень", codeEventTitle(call("workspace_list", ""), false))
        assertEquals("Подготовил изменение src/App.kt", codeEventTitle(call("workspace_write", "./src//App.kt"), false))
        assertEquals("Не удалось прочитать x.bin", codeEventTitle(call("workspace_read", "x.bin"), true))
        assertNull(codeEventTitle(ProviderToolCall("c", "gh__search", JsonObject(emptyMap())), false))
    }
}
