package app.clodex.harness.updates

import app.clodex.harness.ui.screens.updateVersionLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePresentationTest {
    @Test fun versionLabelShowsInstalledAndAvailableCodes() {
        assertEquals("Версия 1.1 (5) → 6", updateVersionLabel("1.1", 5, 6))
    }
}
