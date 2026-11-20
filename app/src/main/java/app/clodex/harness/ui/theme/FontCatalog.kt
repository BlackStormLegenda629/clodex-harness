package app.clodex.harness.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.clodex.harness.R

object UiFontCatalog {
    val names = listOf("Inter", "Manrope", "Noto Sans", "System")

    fun resolveName(name: String): String = name.takeIf { it in names } ?: names.first()

    fun family(name: String): FontFamily = when (resolveName(name)) {
        "Manrope" -> FontFamily(
            Font(R.font.manrope_variable, FontWeight.Normal),
            Font(R.font.manrope_variable, FontWeight.Medium),
            Font(R.font.manrope_variable, FontWeight.SemiBold),
        )
        "Noto Sans" -> FontFamily(
            Font(R.font.noto_sans_variable, FontWeight.Normal),
            Font(R.font.noto_sans_variable, FontWeight.Medium),
            Font(R.font.noto_sans_variable, FontWeight.SemiBold),
        )
        "System" -> FontFamily.Default
        else -> FontFamily(
            Font(R.font.inter_regular, FontWeight.Normal),
            Font(R.font.inter_medium, FontWeight.Medium),
            Font(R.font.inter_semibold, FontWeight.SemiBold),
        )
    }
}

object CodeFontCatalog {
    val names = listOf("JetBrains Mono", "Fira Code", "IBM Plex Mono", "Source Code Pro", "System Mono")

    fun resolveName(name: String): String = name.takeIf { it in names } ?: names.first()

    fun family(name: String): FontFamily = when (resolveName(name)) {
        "Fira Code" -> FontFamily(
            Font(R.font.fira_code_variable, FontWeight.Normal),
            Font(R.font.fira_code_variable, FontWeight.Medium),
        )
        "IBM Plex Mono" -> FontFamily(
            Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
            Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
        )
        "Source Code Pro" -> FontFamily(
            Font(R.font.source_code_pro_variable, FontWeight.Normal),
            Font(R.font.source_code_pro_variable, FontWeight.Medium),
        )
        "System Mono" -> FontFamily.Monospace
        else -> FontFamily(
            Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
            Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        )
    }
}
