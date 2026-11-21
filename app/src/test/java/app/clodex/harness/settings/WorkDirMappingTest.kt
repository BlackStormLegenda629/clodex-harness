package app.clodex.harness.settings

import app.clodex.harness.data.TOGGLE_DEFAULTS
import app.clodex.harness.data.WorkDir
import app.clodex.harness.data.WorkDirMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkDirMappingTest {
    @Test fun safTreeUriIsPreserved() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
        val ws = WorkDirMapping.toWorkspace(WorkDir(uri, "saf"))
        assertEquals(uri, ws.path)
        assertEquals("Documents", ws.name)
        assertEquals("dev", ws.kind)
    }

    @Test fun plainPathKeepsItsLastSegment() {
        val ws = WorkDirMapping.toWorkspace(WorkDir("/storage/emulated/0/Download/notes/", "git"))
        assertEquals("/storage/emulated/0/Download/notes/", ws.path)
        assertEquals("notes", ws.name)
    }

    @Test fun emptyTreeUriFails() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkDirMapping.toWorkspace(WorkDir("  ", "saf"))
        }
    }

    @Test fun deadTogglesAreGone() {
        assertFalse(TOGGLE_DEFAULTS.containsKey("ask"))
        assertFalse(TOGGLE_DEFAULTS.containsKey("edit"))
        assertFalse(TOGGLE_DEFAULTS.containsKey("agentWeb"))
        assertFalse(TOGGLE_DEFAULTS.containsKey("p1"))
    }
}
