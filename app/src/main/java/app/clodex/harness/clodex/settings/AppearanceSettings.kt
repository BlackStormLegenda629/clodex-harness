package app.clodex.harness.clodex.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class DesignStyle { MODERN, MATERIAL_YOU }

enum class AppFont { INTER, MANROPE, ROBOTO }

enum class ThemeMode { LIGHT, DARK, SYSTEM }

val RadiusOptions = listOf(12.dp, 20.dp, 28.dp)

@Immutable
data class AppearanceSettings(
    val accent: Color = Color(0xFFFF7A1A),
    val liquidGlass: Boolean = false,
    val design: DesignStyle = DesignStyle.MODERN,
    val font: AppFont = AppFont.INTER,
    val radius: Dp = 20.dp,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
