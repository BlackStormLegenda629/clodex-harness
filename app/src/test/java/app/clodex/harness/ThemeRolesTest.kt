package app.clodex.harness

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import app.clodex.harness.ui.theme.materialColors
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeRolesTest {
    @Test fun materialColorsTakeForegroundsFromTheScheme() {
        val scheme = darkColorScheme(
            primary = Color(0xFF112233), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF334455), onPrimaryContainer = Color(0xFFDDEEFF),
            surface = Color(0xFF101010), onSurface = Color(0xFFEEEEEE),
            surfaceContainerLow = Color(0xFF161616), surfaceContainerHigh = Color(0xFF222222),
            onSurfaceVariant = Color(0xFFBBBBBB), outlineVariant = Color(0xFF444444),
            error = Color(0xFFFF8888), errorContainer = Color(0xFF330000),
        )
        val c = materialColors(scheme, true)
        assertEquals(scheme.primary, c.ac); assertEquals(scheme.onPrimary, c.onAc)
        assertEquals(scheme.surface, c.bg); assertEquals(scheme.onSurface, c.ink)
        assertEquals(scheme.surfaceContainerLow, c.s1); assertEquals(scheme.surfaceContainerHigh, c.s2)
        assertEquals(scheme.outlineVariant, c.line); assertEquals(scheme.error, c.dt)
    }
}
