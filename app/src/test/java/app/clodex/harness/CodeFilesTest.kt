package app.clodex.harness

import app.clodex.harness.data.code.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CodeFilesTest {
    private fun path(s: String) = WorkspacePath.parse(s).getOrThrow()
    private var readPath: WorkspacePath? = null
    private val gateway = object : WorkspaceGateway {
        override suspend fun list(path: WorkspacePath) = Result.success(listOf(
            WorkspaceEntry(path.child("a.HTML").getOrThrow(), false, 10, true),
            WorkspaceEntry(path.child("z").getOrThrow(), true, 0, false),
        ))
        override suspend fun read(path: WorkspacePath): Result<WorkspaceRead> {
            readPath = path
            return Result.success(WorkspaceRead(path, null, 900000, "Файл слишком большой"))
        }
        override suspend fun apply(changes: List<WorkspaceChange>, message: String): Result<WorkspaceApplyResult> = error("Read only")
    }

    @Test fun nestedListingShowsDirectoriesFirst() = runBlocking {
        assertEquals(listOf("site/z", "site/a.HTML"), CodeFiles(gateway).list(path("site")).getOrThrow().map { it.path.value })
    }

    @Test fun unsupportedFileIsNotReadAndOversizedHtmlIsNotRendered() = runBlocking {
        val files = CodeFiles(gateway)
        assertTrue(files.preview(path("a.txt")).isFailure)
        assertNull(readPath)
        assertEquals("Файл слишком большой", files.preview(path("site/a.HTML")).exceptionOrNull()?.message)
        assertEquals(path("site/a.HTML"), readPath)
    }
}
