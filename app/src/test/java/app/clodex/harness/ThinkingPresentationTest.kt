package app.clodex.harness

import app.clodex.harness.ui.components.ThinkingPresentation
import app.clodex.harness.ui.components.thinkingLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingPresentationTest {
    @Test fun activeThinkingShowsElapsedTime() {
        assertEquals("Ход рассуждений · 4 с", thinkingLabel(ThinkingPresentation(thinking = true, startedAt = 1000), 5200))
    }
    @Test fun activeThinkingShowsDotsAndPlainLabel() {
        val state = ThinkingPresentation(thinking = true, steps = listOf("Анализирую запрос"))

        assertTrue(state.showDots)
        assertEquals("Ход рассуждений", thinkingLabel(state))
    }

    @Test fun completedThinkingShowsDurationWithoutDots() {
        val state = ThinkingPresentation(thinking = false, seconds = 12, steps = listOf("Готово"))

        assertFalse(state.showDots)
        assertEquals("Ход рассуждений · 12 с", thinkingLabel(state))
    }

    @Test fun emptySummaryIsSafe() {
        val state = ThinkingPresentation(thinking = false)

        assertFalse(state.showDots)
        assertTrue(state.steps.isEmpty())
        assertEquals("Ход рассуждений", thinkingLabel(state))
    }
}
