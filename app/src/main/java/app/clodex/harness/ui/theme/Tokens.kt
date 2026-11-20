package app.clodex.harness.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.clodex.harness.data.AppStyle

/** Neutral surfaces are independent of the selected accent. */
@Immutable
data class HColors(
    val bg: Color, val s1: Color, val s2: Color, val line: Color, val ink: Color, val mu: Color,
    val warn: Color, val ok: Color, val bb: Color, val bt: Color, val ab: Color, val at: Color,
    val db: Color, val dt: Color, val wb: Color, val onAc: Color,
    val ac: Color, val dark: Boolean,
)

private val LightBase = HColors(
    bg = Color(0xFFF7F7F8), s1 = Color(0xFFFFFFFF), s2 = Color(0xFFEEEFF1), line = Color(0xFFDFE0E3),
    ink = Color(0xFF1A1B1E), mu = Color(0xFF5F6269), warn = Color(0xFF9A3412), ok = Color(0xFF1F6B5C),
    bb = Color(0xFFE6EDFB), bt = Color(0xFF1D4ED8), ab = Color(0xFFE7F4EE), at = Color(0xFF14573F),
    db = Color(0xFFFDECEC), dt = Color(0xFF9B1C1C), wb = Color(0xFFFBEBD9), onAc = Color(0xFFFFFFFF),
    ac = Color(0xFF8C5142), dark = false,
)

private val DarkBase = HColors(
    bg = Color(0xFF151516), s1 = Color(0xFF1D1E20), s2 = Color(0xFF28292C), line = Color(0xFF343539),
    ink = Color(0xFFF1F1F2), mu = Color(0xFFABADB4), warn = Color(0xFFF0925A), ok = Color(0xFF5BBF9F),
    bb = Color(0xFF1B2740), bt = Color(0xFF9DBBFF), ab = Color(0xFF12291F), at = Color(0xFF86DDB5),
    db = Color(0xFF2E1717), dt = Color(0xFFF39A9A), wb = Color(0xFF3A2410), onAc = Color(0xFF151516),
    ac = Color(0xFFDCA18F), dark = true,
)

private val StudioLight = LightBase.copy(
    bg = Color(0xFFF8F5F0), s1 = Color(0xFFFFFCF8), s2 = Color(0xFFF0EAE2), line = Color(0xFFE3D9CE),
    ink = Color(0xFF25201C), mu = Color(0xFF625950),
)

private val StudioDark = DarkBase.copy(
    bg = Color(0xFF171513), s1 = Color(0xFF211D19), s2 = Color(0xFF2E2823), line = Color(0xFF423830),
    ink = Color(0xFFF2EDE7), mu = Color(0xFFB9AEA4),
)

/** Accent names are persisted in settings. */
data class Accent(val name: String, val light: Color, val dark: Color)

val Accents = listOf(
    Accent("Терракота", LightBase.ac, DarkBase.ac),
    Accent("Синий", Color(0xFF405D87), Color(0xFF9FB7DD)),
    Accent("Зелёный", Color(0xFF37665A), Color(0xFF8ABFB0)),
    Accent("Фиолетовый", Color(0xFF5F5082), Color(0xFFB8A9D8)),
)

fun colorsFor(dark: Boolean, accent: Accent, style: AppStyle = AppStyle.Classic): HColors =
    when (style) {
        AppStyle.Classic -> if (dark) DarkBase else LightBase
        AppStyle.Studio -> if (dark) StudioDark else StudioLight
    }.copy(ac = if (dark) accent.dark else accent.light)

object HDim {
    val card = 12.dp
    val input = 12.dp
    val composer = 16.dp
    val sheetTop = 18.dp
    val pill = 12.dp
    val iconBtn = 44.dp
    val chip = 10.dp
    val tabsBar = 60.dp
    val content = 20.dp
    val header = 68.dp
    val userMsgBig = 20.dp
    val userMsgSmall = 6.dp
}

// CSS easing curves from the prototype
val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)          // `ease`
val Rise = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)            // screens, rows, messages
val SheetEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)       // sheets

/** All animation specs go through here, so ANIMATOR_DURATION_SCALE == 0 turns everything into snaps. */
@Immutable
class Motion(val enabled: Boolean) {
    fun <T> spec(ms: Int, easing: androidx.compose.animation.core.Easing = Ease, delay: Int = 0): FiniteAnimationSpec<T> =
        if (enabled) tween(ms, delay, easing) else snap()
}

@Immutable
class HText(val sans: FontFamily, val mono: FontFamily) {
    private fun s(size: Float, w: FontWeight = FontWeight.Normal, lh: Float? = null, ls: Float = 0f, f: FontFamily = sans) =
        TextStyle(
            fontFamily = f, fontWeight = w, fontSize = size.sp,
            lineHeight = if (lh != null) (size * lh).sp else TextUnit.Unspecified, letterSpacing = ls.sp,
        )

    val hd = s(17f, FontWeight.SemiBold)
    val big = s(26f, FontWeight.SemiBold, ls = -0.52f)
    val pt = s(30f, FontWeight.SemiBold, ls = -0.6f)
    val sheetTitle = s(17f, FontWeight.SemiBold)
    val body = s(15f)
    val bodyMed = s(15f, FontWeight.Medium)
    val msgUser = s(15f, lh = 1.45f)
    val msgAi = s(15f, lh = 1.55f)
    val row16 = s(16f, FontWeight.Medium)
    val row14 = s(14f)
    val row14Med = s(14f, FontWeight.Medium)
    val row14Semi = s(14f, FontWeight.SemiBold, lh = 1.71f)
    val note = s(13f, lh = 1.5f)
    val small = s(12f)
    val sg = s(12f, ls = 0.72f)
    val btn = s(14f, FontWeight.Medium)
    val tab = s(11f, FontWeight.Medium)
    val chip13 = s(13f)
    val fab = s(15f, FontWeight.Medium)
    val empTitle = s(17f, FontWeight.Medium)
    val detTitle = s(20f, FontWeight.SemiBold)
    val ctl = s(16f, FontWeight.SemiBold)
    val mono12 = s(12f, f = mono)
    val mono13 = s(13f, f = mono)
    val monoCode = s(12f, lh = 1.65f, f = mono)
    val monoDiff = s(12f, lh = 22f / 12f, f = mono)
    val badge = s(12f, FontWeight.Medium)
}
