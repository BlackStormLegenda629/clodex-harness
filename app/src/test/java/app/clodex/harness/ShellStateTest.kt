package app.clodex.harness

import app.clodex.harness.clodex.ui.ShellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Menu drawer and model picker are plain state of AppRoot, so their transitions are testable. */
class ShellStateTest {
    private val models = listOf("Fable 5.2", "Opus 5.5", "Sonnet 5.5", "Haiku 5.5")

    @Test fun drawerOpensAndClosesIndependentlyOfThePicker() {
        val state = ShellState(models)

        state.openDrawer()
        assertTrue(state.drawer)
        assertFalse(state.picker)

        state.closeDrawer()
        assertFalse(state.drawer)

        state.openPicker()
        assertTrue(state.picker)
        state.closePicker()
        assertFalse(state.picker)
    }

    @Test fun defaultModelIsSonnet() {
        assertEquals("Sonnet 5.5", ShellState(models).model)
    }

    @Test fun pickingAModelStoresItAndClosesTheSheet() {
        val state = ShellState(models)
        state.openPicker()

        state.selectModel("Opus 5.5")

        assertEquals("Opus 5.5", state.model)
        assertFalse(state.picker)
        assertEquals(1, state.modelIndex)
    }

    @Test fun unknownModelKeepsTheCurrentOneButStillCloses() {
        val state = ShellState(models)
        state.openPicker()

        state.selectModel("Unknown 9")

        assertEquals("Sonnet 5.5", state.model)
        assertFalse(state.picker)
    }

    @Test fun subsequentDrawerSelectionDoesNotTouchTheModel() {
        val state = ShellState(models)
        state.selectModel(3)
        state.openDrawer()
        state.closeDrawer()

        assertEquals("Haiku 5.5", state.model)
        assertEquals(3, state.modelIndex)
    }
}
