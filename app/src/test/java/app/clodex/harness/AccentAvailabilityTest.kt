package app.clodex.harness

import app.clodex.harness.data.AppStyle
import app.clodex.harness.ui.theme.manualAccentAvailable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentAvailabilityTest {
    @Test fun `manual accent is hidden only for Material You`() {
        assertTrue(manualAccentAvailable(AppStyle.Classic))
        assertTrue(manualAccentAvailable(AppStyle.Studio))
        assertFalse(manualAccentAvailable(AppStyle.Material))
    }
}
