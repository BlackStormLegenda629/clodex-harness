package app.clodex.harness.clodex.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import app.clodex.harness.clodex.settings.AppearanceSettings
import app.clodex.harness.clodex.settings.DesignStyle
import app.clodex.harness.clodex.settings.ThemeMode

enum class TabStyle { MODERN, MATERIAL, GLASS }

enum class ButtonStyle { MODERN, MATERIAL, GLASS }

/** Everything the screens may use: palette, radius, fonts, glass flag and control styles. */
@Immutable
data class ClodexStyle(
    val colors: ClodexColors,
    val radius: Dp,
    val type: ClodexType,
    val glass: Boolean,
    val tabStyle: TabStyle,
    val buttonStyle: ButtonStyle,
    val dark: Boolean,
)

val LocalClodexStyle = staticCompositionLocalOf { styleFor(AppearanceSettings()) }

/** MODERN is the default; Liquid Glass replaces it when enabled; MATERIAL_YOU disables glass. */
fun styleFor(settings: AppearanceSettings, systemDark: Boolean = false): ClodexStyle {
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val glass = settings.liquidGlass && settings.design == DesignStyle.MODERN
    val colors = when {
        glass -> glassColors()
        settings.design == DesignStyle.MATERIAL_YOU -> materialColors(dark)
        dark -> modernColors(settings.accent)
        else -> modernLightColors(settings.accent)
    }
    val tabStyle = when {
        glass -> TabStyle.GLASS
        settings.design == DesignStyle.MATERIAL_YOU -> TabStyle.MATERIAL
        else -> TabStyle.MODERN
    }
    val buttonStyle = when {
        glass -> ButtonStyle.GLASS
        settings.design == DesignStyle.MATERIAL_YOU -> ButtonStyle.MATERIAL
        else -> ButtonStyle.MODERN
    }
    return ClodexStyle(
        colors = colors,
        radius = settings.radius,
        type = ClodexType(fontFamilyFor(settings.font)),
        glass = glass,
        tabStyle = tabStyle,
        buttonStyle = buttonStyle,
        dark = dark,
    )
}

@Composable
fun ClodexTheme(
    settings: AppearanceSettings,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val style = styleFor(settings, systemDark)
    val c = style.colors
    val scheme = if (style.dark) {
        darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            background = c.background,
            onBackground = c.textPrimary,
            surface = c.surface,
            onSurface = c.textPrimary,
            surfaceVariant = c.surfaceChip,
            onSurfaceVariant = c.chipText,
            outline = c.outline,
            error = c.diffDelText,
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            background = c.background,
            onBackground = c.textPrimary,
            surface = c.surface,
            onSurface = c.textPrimary,
            surfaceVariant = c.surfaceChip,
            onSurfaceVariant = c.chipText,
            outline = c.outline,
            error = c.diffDelText,
        )
    }
    CompositionLocalProvider(LocalClodexStyle provides style) {
        MaterialTheme(colorScheme = scheme, typography = style.type.material(), content = content)
    }
}
