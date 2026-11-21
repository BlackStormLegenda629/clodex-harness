package app.clodex.harness.code

import app.clodex.harness.data.code.WorkspaceApplyResult
import app.clodex.harness.data.code.WorkspaceChange
import app.clodex.harness.data.code.WorkspaceEntry
import app.clodex.harness.data.code.WorkspaceGateway
import app.clodex.harness.data.code.WorkspaceOverlay
import app.clodex.harness.data.code.WorkspacePath
import app.clodex.harness.data.code.WorkspaceRead
import app.clodex.harness.data.code.WorkspaceSearch
import app.clodex.harness.data.search.SearchSlice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSearchTest {
    private fun path(value: String) = WorkspacePath.parse(value).getOrThrow()

    private class FakeGateway(
        private val entries: Map<String, List<WorkspaceEntry>>,
        private val texts: Map<String, String>,
    ) : WorkspaceGateway {
        val readPaths = mutableListOf<String>()
        override suspend fun list(p: WorkspacePath): Result<List<WorkspaceEntry>> =
            Result.success(entries[p.value].orEmpty())
        override suspend fun read(p: WorkspacePath): Result<WorkspaceRead> {
            readPaths += p.value
            val text = texts[p.value] ?: return Result.failure(Exception("not found"))
            return Result.success(WorkspaceRead(p, text, text.toByteArray().size.toLong()))
        }
        override suspend fun apply(changes: List<WorkspaceChange>, message: String): Result<WorkspaceApplyResult> =
            Result.success(WorkspaceApplyResult(emptyList()))
    }

    @Test fun binaryAndOversizedFilesAreSkipped() = runBlocking {
        val normal = WorkspaceEntry(path("ok.txt"), false, 10, true)
        val binary = WorkspaceEntry(path("pic.png"), false, 10, false)
        val huge = WorkspaceEntry(path("huge.txt"), false, 1_000, true)
        val gateway = FakeGateway(
            entries = mapOf("" to listOf(normal, binary, huge)),
            texts = mapOf("ok.txt" to "нужное слово here", "huge.txt" to "нужное слово"),
        )
        val hits = WorkspaceSearch(gateway).search("нужное", listOf(WorkspacePath.ROOT), maxBytes = 100)
        assertEquals(1, hits.size)
        assertEquals("ok.txt", hits.first().title)
        assertFalse(gateway.readPaths.contains("pic.png"))
        assertFalse(gateway.readPaths.contains("huge.txt"))
    }

    @Test fun stagedEditsAreVisible() = runBlocking {
        val entry = WorkspaceEntry(path("a.txt"), false, 4, true)
        val gateway = FakeGateway(
            entries = mapOf("" to listOf(entry)),
            texts = mapOf("a.txt" to "старый текст"),
        )
        val overlay = WorkspaceOverlay(gateway)
        overlay.stage(path("a.txt"), "новый текст с искомым словом")
        val hits = WorkspaceSearch(gateway, overlay).search("искомым", listOf(WorkspacePath.ROOT))
        assertTrue(hits.any { it.slice == SearchSlice.FILES && it.title == "a.txt" })
    }
}
