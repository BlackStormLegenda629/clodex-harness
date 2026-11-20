package app.clodex.harness

import app.clodex.harness.data.code.WorkspaceException
import app.clodex.harness.data.code.WorkspacePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

class SafWorkspacePolicyTest {
    @Test fun `text files by mime, extension and well known names`() {
        assertTrue(WorkspacePolicy.canReadText("App.kt", "application/octet-stream", 100))
        assertTrue(WorkspacePolicy.canReadText("notes", "text/plain", 100))
        assertTrue(WorkspacePolicy.canReadText("data", "application/json", 100))
        assertTrue(WorkspacePolicy.canReadText("Dockerfile", "", 100))
        assertTrue(WorkspacePolicy.canReadText(".gitignore", "", 100))
        assertTrue(WorkspacePolicy.canReadText("gradlew", "", 100))
    }

    @Test fun `binary files are listed but not readable as text`() {
        assertFalse(WorkspacePolicy.canReadText("logo.png", "image/png", 100))
        assertFalse(WorkspacePolicy.canReadText("app.apk", "application/vnd.android.package-archive", 100))
        assertFalse(WorkspacePolicy.canReadText("blob", "application/octet-stream", 100))
        assertEquals("двоичный файл", WorkspacePolicy.unreadableReason("logo.png", 100))
    }

    @Test fun `read cap is 256KB`() {
        assertTrue(WorkspacePolicy.canReadText("a.txt", "text/plain", 256L * 1024))
        assertFalse(WorkspacePolicy.canReadText("a.txt", "text/plain", 256L * 1024 + 1))
        assertEquals("больше 256 КБ", WorkspacePolicy.unreadableReason("a.txt", 300_000))
    }

    @Test fun `write cap is 512KB and binary targets are refused`() {
        assertNull(WorkspacePolicy.writeError("src/App.kt", 512 * 1024))
        assertEquals("больше 512 КБ", WorkspacePolicy.writeError("src/App.kt", 512 * 1024 + 1))
        assertEquals("двоичный файл", WorkspacePolicy.writeError("logo.png", 10))
        assertTrue(WorkspacePolicy.canWriteText("README", 10))
    }

    @Test fun `nul bytes mean binary content`() {
        assertTrue(WorkspacePolicy.looksBinary(byteArrayOf(65, 0, 66)))
        assertFalse(WorkspacePolicy.looksBinary("привет".toByteArray()))
    }

    @Test fun `revoked grant becomes a recoverable error`() {
        val e = WorkspacePolicy.mapSafError(SecurityException("Permission Denial: content://x"))
        assertTrue(e is WorkspaceException)
        assertTrue((e as WorkspaceException).recoverable)
        assertTrue(e.message!!.contains("выберите папку заново"))
        val missing = WorkspacePolicy.mapSafError(FileNotFoundException("gone")) as WorkspaceException
        assertFalse(missing.recoverable)
    }
}
