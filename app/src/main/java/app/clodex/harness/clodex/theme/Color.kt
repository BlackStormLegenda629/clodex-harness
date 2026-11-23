package app.clodex.harness.clodex.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Every colour used by the UI. Screens must read them from [ClodexStyle] only. */
@Immutable
data class ClodexColors(
    val background: Color,
    val backgroundDeep: Color,
    val backgroundSoft: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceChip: Color,
    val surfaceBubble: Color,
    val surfaceAlt: Color,
    val border: Color,
    val borderStrong: Color,
    val borderGhost: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val chipText: Color,
    val textMuted: Color,
    val textMuted2: Color,
    val white: Color,
    val onAccent: Color,
    val accent: Color,
    val warning: Color,
    val success: Color,
    val successStrong: Color,
    val diffAddText: Color,
    val diffDelText: Color,
    val diffAddBg: Color,
    val diffDelBg: Color,
    val dangerBorder: Color,
    val modelBadgeBg: Color,
    val modelBadgeText: Color,
    val navBar: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val primary: Color,
    val onPrimary: Color,
    val outline: Color,
    val scrim: Color,
    val handle: Color,
    val pendingBorder: Color,
    val glassBase: Color,
    val glassSpot1: Color,
    val glassSpot2: Color,
    val glassSpot3: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassActiveFrom: Color,
    val glassActiveTo: Color,
)

// Palette constants from the specification (MODERN).
private val Background = Color(0xFF0C0C0D)
private val BackgroundDeep = Color(0xFF070708)
private val BackgroundSoft = Color(0xFF161615)
private val Surface = Color(0xFF17171A)
private val SurfaceElevated = Color(0xFF1F1E1D)
private val SurfaceChip = Color(0xFF1E1E22)
private val SurfaceBubble = Color(0xFF2A2927)
private val SurfaceAlt = Color(0xFF1B1B20)
private val Border = Color(0xFF26262B)
private val BorderStrong = Color(0xFF33322F)
private val BorderGhost = Color(0xFF2A2A2E)
private val TextPrimary = Color(0xFFF4F4F5)
private val TextSecondary = Color(0xFFE6E4DC)
private val ChipText = Color(0xFFC9C7BE)
private val TextMuted = Color(0xFF9C9A92)
private val TextMuted2 = Color(0xFF8A8A92)
private val White = Color(0xFFF5F5F7)
private val OnAccent = Color(0xFF0C0C0D)
private val Warning = Color(0xFFFFD23F)
private val Success = Color(0xFF8DDBB0)
private val SuccessStrong = Color(0xFF3DDC97)
private val DiffAddText = Color(0xFF9BEFC8)
private val DiffDelText = Color(0xFFFFA9A9)
private val DiffAddBg = Color(0x1F3DDC97)
private val DiffDelBg = Color(0x1FFF6E6E)
private val DangerBorder = Color(0xFF4A2E2E)
private val ModelBadgeBg = Color(0xFF2A3442)
private val ModelBadgeText = Color(0xFF9CC4FF)
private val Scrim = Color(0x8C000000)
private val Handle = Color(0xFF4A4945)
private val PendingBorder = Color(0xFF4A4530)

// MATERIAL_YOU constants.
private val M3Background = Color(0xFF1C1B1F)
private val M3SurfaceContainer = Color(0xFF2B2930)
private val M3NavBar = Color(0xFF211F26)
private val M3SecondaryContainer = Color(0xFF4A4458)
private val M3OnSecondaryContainer = Color(0xFFE8DEF8)
private val M3Primary = Color(0xFFD0BCFF)
private val M3OnPrimary = Color(0xFF381E72)
private val M3Outline = Color(0xFF938F99)
private val M3OnSurface = Color(0xFFE6E1E5)
private val M3OnSurfaceVariant = Color(0xFFCAC4D0)

// LIQUID GLASS constants.
private val GlassBase = Color(0xFF05040A)
private val GlassSpot1 = Color(0x527C5CFF)
private val GlassSpot2 = Color(0x2EA78BFA)
private val GlassSpot3 = Color(0x3D633EDC)
private val GlassActiveFrom = Color(0xF2C4B5FD)
private val GlassActiveTo = Color(0xE67C5CFF)

/** MODERN dark palette; accent comes from the settings. */
fun modernColors(accent: Color): ClodexColors = ClodexColors(
    background = Background,
    backgroundDeep = BackgroundDeep,
    backgroundSoft = BackgroundSoft,
    surface = Surface,
    surfaceElevated = SurfaceElevated,
    surfaceChip = SurfaceChip,
    surfaceBubble = SurfaceBubble,
    surfaceAlt = SurfaceAlt,
    border = Border,
    borderStrong = BorderStrong,
    borderGhost = BorderGhost,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    chipText = ChipText,
    textMuted = TextMuted,
    textMuted2 = TextMuted2,
    white = White,
    onAccent = OnAccent,
    accent = accent,
    warning = Warning,
    success = Success,
    successStrong = SuccessStrong,
    diffAddText = DiffAddText,
    diffDelText = DiffDelText,
    diffAddBg = DiffAddBg,
    diffDelBg = DiffDelBg,
    dangerBorder = DangerBorder,
    modelBadgeBg = ModelBadgeBg,
    modelBadgeText = ModelBadgeText,
    navBar = Background,
    secondaryContainer = SurfaceBubble,
    onSecondaryContainer = TextPrimary,
    primary = accent,
    onPrimary = OnAccent,
    outline = TextMuted2,
    scrim = Scrim,
    handle = Handle,
    pendingBorder = PendingBorder,
    glassBase = GlassBase,
    glassSpot1 = GlassSpot1,
    glassSpot2 = GlassSpot2,
    glassSpot3 = GlassSpot3,
    glassFill = Color.White.copy(alpha = 0.07f),
    glassBorder = Color.White.copy(alpha = 0.16f),
    glassHighlight = Color.White.copy(alpha = 0.16f),
    glassActiveFrom = GlassActiveFrom,
    glassActiveTo = GlassActiveTo,
)

/** Light MODERN palette, built only from the palette constants above. */
fun modernLightColors(accent: Color): ClodexColors = modernColors(accent).copy(
    background = White,
    backgroundDeep = White,
    backgroundSoft = White,
    surface = White,
    surfaceElevated = White,
    surfaceChip = TextSecondary,
    surfaceBubble = TextSecondary,
    surfaceAlt = TextSecondary,
    border = BorderGhost.copy(alpha = 0.16f),
    borderStrong = BorderGhost.copy(alpha = 0.28f),
    borderGhost = BorderGhost.copy(alpha = 0.16f),
    textPrimary = OnAccent,
    textSecondary = OnAccent.copy(alpha = 0.78f),
    chipText = OnAccent.copy(alpha = 0.72f),
    textMuted = TextMuted2,
    textMuted2 = TextMuted2,
    white = White,
    onAccent = OnAccent,
    navBar = White,
    onSecondaryContainer = OnAccent,
    outline = TextMuted2,
)

/** Fixed Material You palette; the accent setting is ignored. */
fun materialColors(dark: Boolean): ClodexColors {
    val base = ClodexColors(
        background = M3Background,
        backgroundDeep = M3Background,
        backgroundSoft = M3NavBar,
        surface = M3Background,
        surfaceElevated = M3SurfaceContainer,
        surfaceChip = M3SurfaceContainer,
        surfaceBubble = M3SecondaryContainer,
        surfaceAlt = M3SurfaceContainer,
        border = M3Outline.copy(alpha = 0.35f),
        borderStrong = M3Outline.copy(alpha = 0.6f),
        borderGhost = M3Outline.copy(alpha = 0.35f),
        textPrimary = M3OnSurface,
        textSecondary = M3OnSurfaceVariant,
        chipText = M3OnSurfaceVariant,
        textMuted = M3Outline,
        textMuted2 = M3Outline,
        white = M3OnSecondaryContainer,
        onAccent = M3OnPrimary,
        accent = M3Primary,
        warning = Warning,
        success = Success,
        successStrong = SuccessStrong,
        diffAddText = DiffAddText,
        diffDelText = DiffDelText,
        diffAddBg = DiffAddBg,
        diffDelBg = DiffDelBg,
        dangerBorder = DangerBorder,
        modelBadgeBg = ModelBadgeBg,
        modelBadgeText = ModelBadgeText,
        navBar = M3NavBar,
        secondaryContainer = M3SecondaryContainer,
        onSecondaryContainer = M3OnSecondaryContainer,
        primary = M3Primary,
        onPrimary = M3OnPrimary,
        outline = M3Outline,
        scrim = Scrim,
        handle = M3Outline.copy(alpha = 0.6f),
        pendingBorder = PendingBorder,
        glassBase = GlassBase,
        glassSpot1 = GlassSpot1,
        glassSpot2 = GlassSpot2,
        glassSpot3 = GlassSpot3,
        glassFill = Color.White.copy(alpha = 0.07f),
        glassBorder = Color.White.copy(alpha = 0.16f),
        glassHighlight = Color.White.copy(alpha = 0.16f),
        glassActiveFrom = GlassActiveFrom,
        glassActiveTo = GlassActiveTo,
    )
    if (dark) return base
    return base.copy(
        background = White,
        backgroundDeep = White,
        backgroundSoft = White,
        surface = White,
        surfaceElevated = White,
        surfaceChip = M3OnSecondaryContainer,
        surfaceBubble = M3OnSecondaryContainer,
        surfaceAlt = M3OnSecondaryContainer,
        border = M3Outline.copy(alpha = 0.4f),
        borderStrong = M3Outline.copy(alpha = 0.65f),
        textPrimary = M3Background,
        textSecondary = M3Background.copy(alpha = 0.78f),
        chipText = M3Background.copy(alpha = 0.72f),
        textMuted = M3Outline,
        textMuted2 = M3Outline,
        white = White,
        onAccent = M3OnPrimary,
        navBar = White,
        onSecondaryContainer = M3OnPrimary,
        onPrimary = M3OnPrimary,
        outline = M3Outline,
    )
}

/** LIQUID GLASS palette: translucent white surfaces over the spot background. */
fun glassColors(): ClodexColors = modernColors(GlassActiveFrom).copy(
    background = Color.Transparent,
    backgroundDeep = GlassBase,
    backgroundSoft = Color.White.copy(alpha = 0.05f),
    surface = Color.White.copy(alpha = 0.07f),
    surfaceElevated = Color.White.copy(alpha = 0.09f),
    surfaceChip = Color.White.copy(alpha = 0.07f),
    surfaceBubble = Color.White.copy(alpha = 0.1f),
    surfaceAlt = Color.White.copy(alpha = 0.07f),
    border = Color.White.copy(alpha = 0.16f),
    borderStrong = Color.White.copy(alpha = 0.16f),
    borderGhost = Color.White.copy(alpha = 0.16f),
    navBar = Color.White.copy(alpha = 0.07f),
    accent = GlassActiveFrom,
    onAccent = Color.White,
    primary = GlassActiveFrom,
    onPrimary = Color.White,
    outline = Color.White.copy(alpha = 0.4f),
    handle = Color.White.copy(alpha = 0.3f),
)

