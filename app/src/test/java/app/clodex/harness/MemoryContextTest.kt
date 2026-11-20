package app.clodex.harness

import app.clodex.harness.data.*
import org.junit.Assert.*
import org.junit.Test

class MemoryContextTest {
    private val notes = listOf(MemoryNote(0, "Global"), MemoryNote(4, "Project 4"), MemoryNote(8, "Project 8"))

    @Test fun memoryUsesOnlyEnabledScopes() {
        val context = memoryContext(notes, true, 4L)
        assertTrue("Global" in context)
        assertTrue("Project 4" in context)
        assertFalse("Project 8" in context)
        assertEquals("", memoryContext(notes, false, null))
        assertFalse("Global" in memoryContext(notes, false, 4L))
        assertFalse("Project 4" in memoryContext(notes, false, 8L))
        assertEquals("", memoryContext(emptyList(), true, 4L))
    }

    @Test fun memoryHasABoundedPromptBudget() {
        val context = memoryContext(List(100) { MemoryNote(0, "x".repeat(1000)) }, true, null)
        assertTrue(context.isNotBlank())
        assertTrue(context.length <= 4000)
    }
}
