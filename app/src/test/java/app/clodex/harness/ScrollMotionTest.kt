package app.clodex.harness

import app.clodex.harness.ui.theme.ScrollMotion
import org.junit.Assert.*
import org.junit.Test

class ScrollMotionTest {
    @Test fun slowScrollStaysSharpAndFastScrollFades() {
        val motion = ScrollMotion()
        repeat(10) { assertEquals(0f, motion.advance(2f, .016f), 0f) }
        var peak = 0f
        repeat(4) { peak = motion.advance(18f, .016f) }
        assertTrue(peak > 2f)
        val trailing = motion.advance(0f, .016f)
        assertTrue(trailing in 0f..<peak)
        assertTrue(trailing > 0f)
        repeat(45) { motion.advance(0f, .016f) }
        assertEquals(0f, motion.advance(0f, .016f), 0f)
    }
}
