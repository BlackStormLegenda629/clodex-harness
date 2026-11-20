package app.clodex.harness

import app.clodex.harness.data.code.CodeMode
import app.clodex.harness.data.code.WorkspacePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePathTest {
    private fun ok(s: String) = WorkspacePath.parse(s).getOrThrow().value
    private fun bad(s: String) = assertTrue("must reject: $s", WorkspacePath.parse(s).isFailure)

    @Test fun `nested paths are kept`() {
        assertEquals("src/main/App.kt", ok("src/main/App.kt"))
        assertEquals(listOf("src", "main", "App.kt"), WorkspacePath.parse("src/main/App.kt").getOrThrow().segments)
        assertEquals("App.kt", WorkspacePath.parse("src/main/App.kt").getOrThrow().name)
        assertEquals("src/main", WorkspacePath.parse("src/main/App.kt").getOrThrow().parent.value)
    }

    @Test fun `slashes and dots are normalized`() {
        assertEquals("src/App.kt", ok("src//App.kt"))
        assertEquals("src/App.kt", ok("./src/./App.kt"))
        assertEquals("src", ok("src/"))
    }

    @Test fun `blank and dot mean the root`() {
        assertTrue(WorkspacePath.parse("").getOrThrow().isRoot)
        assertTrue(WorkspacePath.parse(".").getOrThrow().isRoot)
        assertEquals("", WorkspacePath.ROOT.value)
    }

    @Test fun `escapes are rejected`() {
        bad("..")
        bad("../secret")
        bad("src/../../etc")
        bad("src/..")
        bad("/etc/passwd")
        bad("src\\App.kt")
        bad("src%2Fx")
        bad("src%5cx")
        bad("a\u0000b")
        bad("a\nb")
        bad("a\u007Fb")
    }

    @Test fun `length limits`() {
        bad("a".repeat(256))
        assertEquals(255, ok("a".repeat(255)).length)
        bad(List(200) { "abc" }.joinToString("/"))
    }

    @Test fun `child keeps the path contained`() {
        val src = WorkspacePath.parse("src").getOrThrow()
        assertEquals("src/App.kt", src.child("App.kt").getOrThrow().value)
        assertFalse(src.child("../x").isSuccess)
    }

    @Test fun `modes map to russian labels`() {
        assertEquals(listOf("План", "Правки", "Авто"), CodeMode.entries.map { it.label })
        assertEquals(CodeMode.AUTO, CodeMode.fromLabel("Авто"))
        assertEquals(CodeMode.EDITS, CodeMode.fromLabel("???"))
    }
}
