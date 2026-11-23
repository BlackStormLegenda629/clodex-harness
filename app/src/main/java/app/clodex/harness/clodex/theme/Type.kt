package app.clodex.harness.clodex.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.clodex.harness.R
import app.clodex.harness.clodex.settings.AppFont

private fun family(
    w400: Int, w500: Int, w600: Int, w700: Int, w800: Int,
): FontFamily = FontFamily(
    Font(w400, FontWeight.Normal),
    Font(w500, FontWeight.Medium),
    Font(w600, FontWeight.SemiBold),
    Font(w700, FontWeight.Bold),
    Font(w800, FontWeight.ExtraBold),
)

private val Inter = family(
    R.font.clodex_inter_400, R.font.clodex_inter_500, R.font.clodex_inter_600,
    R.font.clodex_inter_700, R.font.clodex_inter_800,
)

private val Manrope = family(
    R.font.clodex_manrope_400, R.font.clodex_manrope_500, R.font.clodex_manrope_600,
    R.font.clodex_manrope_700, R.font.clodex_manrope_800,
)

private val Roboto = family(
    R.font.clodex_roboto_400, R.font.clodex_roboto_500, R.font.clodex_roboto_600,
    R.font.clodex_roboto_700, R.font.clodex_roboto_800,
)

fun fontFamilyFor(font: AppFont): FontFamily = when (font) {
    AppFont.INTER -> Inter
    AppFont.MANROPE -> Manrope
    AppFont.ROBOTO -> Roboto
}

/** All headings are ExtraBold with -0.02em tracking; body text is 15 sp Normal. */
class ClodexType(font: FontFamily) {
    val display = TextStyle(fontFamily = font, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.02).em)
    val title = TextStyle(fontFamily = font, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.02).em)
    val heading = TextStyle(fontFamily = font, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.02).em)
    val subtitle = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = (-0.02).em)
    val cardTitle = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.02).em)
    val rowTitle = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.02).em)
    val body = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyRelaxed = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp)
    val description = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
    val caption = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val captionSmall = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val button = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    val buttonSmall = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    val monoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

    fun material(): Typography = Typography(
        displaySmall = display,
        headlineMedium = heading,
        headlineSmall = heading,
        titleLarge = subtitle,
        titleMedium = cardTitle,
        titleSmall = rowTitle,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = buttonSmall,
        labelMedium = caption,
        labelSmall = captionSmall,
    )
}
