package app.clodex.harness.ui

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.clodex.harness.ui.components.HapticPolicy
import app.clodex.harness.util.speechText
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticPolicyTest {
    @Test fun hapticActionMapping() {
        for (action in listOf("send", "confirm", "error")) {
            assertEquals(HapticFeedbackType.LongPress, HapticPolicy.typeFor(action))
        }
        assertEquals(HapticFeedbackType.TextHandleMove, HapticPolicy.typeFor("toggle"))
    }

    @Test fun ttsTextIsCapped() {
        assertEquals("x".repeat(4000), speechText("x".repeat(10000)))
        assertEquals("x".repeat(3999), speechText("x".repeat(3999) + "😃"))
    }
}
