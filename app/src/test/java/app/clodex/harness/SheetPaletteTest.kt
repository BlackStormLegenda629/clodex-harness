package app.clodex.harness

import androidx.compose.ui.graphics.luminance
import app.clodex.harness.data.AppStyle
import app.clodex.harness.ui.theme.Accents
import app.clodex.harness.ui.theme.colorsFor
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetPaletteTest {
    @Test fun panelSurfacesStayOpaqueAndNeutralForEveryAccent() {
        listOf(false, true).forEach { dark ->
            Accents.forEach { accent ->
                val colors = colorsFor(dark, accent, AppStyle.Classic)
                listOf(colors.bg, colors.s1, colors.s2).forEach { surface ->
                    val channels = listOf(surface.red, surface.green, surface.blue)
                    assertTrue("$accent, dark=$dark: $surface must be opaque", surface.alpha == 1f)
                    assertTrue("$accent, dark=$dark: $surface must stay neutral", channels.max() - channels.min() <= 0.02f)
                }
            }
        }
    }

    @Test fun accentActionsHaveReadableForegroundsInBothThemes() {
        listOf(false, true).forEach { dark ->
            Accents.forEach { accent ->
                val colors = colorsFor(dark, accent, AppStyle.Classic)
                val a = colors.ac.luminance()
                val b = colors.onAc.luminance()
                val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                assertTrue("$accent, dark=$dark: accent button contrast $contrast < 4.5", contrast >= 4.5f)
            }
        }
    }
}
