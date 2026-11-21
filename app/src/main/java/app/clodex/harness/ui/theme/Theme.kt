package app.clodex.harness.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import app.clodex.harness.data.AppSettings
import app.clodex.harness.data.AppStyle

val LocalColors = staticCompositionLocalOf { colorsFor(false, Accents[0], AppStyle.Material) }
val LocalText = staticCompositionLocalOf { HText(FontFamily.Default, FontFamily.Monospace) }
val LocalMotion = staticCompositionLocalOf { Motion(true) }
internal val LocalProgressStyle = staticCompositionLocalOf { app.clodex.harness.data.ProgressStyle.Straight }
internal val LocalScrollBlur = staticCompositionLocalOf { false }
private val LocalStyle = staticCompositionLocalOf { AppStyle.Classic }

object H {
    val c: HColors @Composable @ReadOnlyComposable get() = LocalColors.current
    val t: HText @Composable @ReadOnlyComposable get() = LocalText.current
    val style: AppStyle @Composable @ReadOnlyComposable get() = LocalStyle.current
    val studio: Boolean @Composable @ReadOnlyComposable get() = LocalStyle.current == AppStyle.Studio
    val material: Boolean @Composable @ReadOnlyComposable get() = LocalStyle.current == AppStyle.Material
}

private fun animationsEnabled(ctx: Context): Boolean =
    Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

@Composable
fun rememberMotion(): Motion {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(animationsEnabled(ctx)) }
    DisposableEffect(ctx) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = animationsEnabled(ctx) }
        }
        ctx.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, obs)
        onDispose { ctx.contentResolver.unregisterContentObserver(obs) }
    }
    return remember(enabled) { Motion(enabled) }
}

@Composable
fun isDarkTheme(settings: AppSettings): Boolean = when (settings.theme) {
    "Светлая" -> false
    "Тёмная" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun HarnessTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isDarkTheme(settings)
    val accent = Accents.firstOrNull { it.name == settings.accent } ?: Accents[0]
    val colors = remember(dark, accent, settings.style, ctx) {
        if (settings.style == AppStyle.Material && Build.VERSION.SDK_INT >= 31) {
            materialColors(if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx), dark)
        } else colorsFor(dark, accent, settings.style)
    }
    val text = remember(settings.uiFont, settings.codeFont) {
        HText(UiFontCatalog.family(settings.uiFont), CodeFontCatalog.family(settings.codeFont))
    }
    val scale = when (settings.textSize) { "Мелкий" -> 0.9f; "Крупный" -> 1.12f; else -> 1f }
    val d = LocalDensity.current
    val motion = rememberMotion()
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.ac, onPrimary = colors.onAc, background = colors.bg, onBackground = colors.ink,
        surface = colors.s1, onSurface = colors.ink, onSurfaceVariant = colors.mu, outline = colors.mu,
        outlineVariant = colors.line, surfaceTint = colors.s1, surfaceVariant = colors.s2,
        surfaceContainerLowest = colors.bg, surfaceContainerLow = colors.s1, surfaceContainer = colors.s1,
        surfaceContainerHigh = colors.s2, surfaceContainerHighest = colors.s2,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalText provides text,
            LocalMotion provides motion,
            LocalProgressStyle provides settings.progressStyle,
            LocalScrollBlur provides settings.scrollBlur,
            LocalStyle provides settings.style,
            LocalDensity provides Density(d.density, d.fontScale * scale),
            LocalContentColor provides colors.ink,
        ) { content() }
    }
}
