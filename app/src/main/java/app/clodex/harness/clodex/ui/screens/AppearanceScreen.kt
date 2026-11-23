package app.clodex.harness.clodex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clodex.harness.R
import app.clodex.harness.clodex.settings.AppFont
import app.clodex.harness.clodex.settings.AppearanceSettings
import app.clodex.harness.clodex.settings.DesignStyle
import app.clodex.harness.clodex.settings.RadiusOptions
import app.clodex.harness.clodex.settings.ThemeMode
import app.clodex.harness.clodex.theme.ClodexTheme
import app.clodex.harness.clodex.theme.LocalClodexStyle
import app.clodex.harness.clodex.ui.components.BackHeader
import app.clodex.harness.clodex.ui.components.ColorPickerDialog
import app.clodex.harness.clodex.ui.components.ColorSwatchRow
import app.clodex.harness.clodex.ui.components.GroupCard
import app.clodex.harness.clodex.ui.components.GroupDivider
import app.clodex.harness.clodex.ui.components.SegmentedControl
import app.clodex.harness.clodex.ui.components.Toggle
import app.clodex.harness.clodex.ui.components.clodexBackground

/** ЭКРАН 9. ОФОРМЛЕНИЕ */
@Composable
fun AppearanceScreen(
    settings: AppearanceSettings,
    onBack: () -> Unit,
    onAccent: (Color) -> Unit,
    onGlass: (Boolean) -> Unit,
    onDesign: (DesignStyle) -> Unit,
    onFont: (AppFont) -> Unit,
    onRadius: (Int) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    var picker by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clodexBackground()
            .padding(horizontal = 16.dp)
            .verticalScrollable(),
    ) {
        BackHeader(title = stringResource(R.string.appearance_title), onBack = onBack)
        SectionLabel(stringResource(R.string.appearance_section_color), Modifier.padding(top = 4.dp))
        GroupCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(stringResource(R.string.appearance_accent), style = style.type.rowTitle, color = colors.textSecondary)
                    Text(
                        stringResource(R.string.appearance_accent_desc),
                        style = style.type.captionSmall,
                        color = colors.textMuted,
                    )
                }
                ColorSwatchRow(
                    selected = settings.accent,
                    onSelect = onAccent,
                    onCustom = { picker = true },
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
            GroupDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.appearance_glass), style = style.type.rowTitle, color = colors.textSecondary)
                    Text(
                        stringResource(R.string.appearance_glass_desc),
                        style = style.type.captionSmall,
                        color = colors.textMuted,
                    )
                }
                Toggle(
                    checked = settings.liquidGlass && settings.design == DesignStyle.MODERN,
                    enabled = settings.design == DesignStyle.MODERN,
                    onCheckedChange = onGlass,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel(stringResource(R.string.appearance_section_style))
        GroupCard {
            ChoiceRow(
                title = stringResource(R.string.appearance_design),
                caption = stringResource(R.string.appearance_design_desc),
            ) {
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.appearance_design_modern),
                        stringResource(R.string.appearance_design_material),
                    ),
                    selectedIndex = if (settings.design == DesignStyle.MODERN) 0 else 1,
                    onSelect = { onDesign(if (it == 0) DesignStyle.MODERN else DesignStyle.MATERIAL_YOU) },
                )
            }
            GroupDivider()
            ChoiceRow(
                title = stringResource(R.string.appearance_font),
                caption = stringResource(R.string.appearance_font_desc),
            ) {
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.appearance_font_inter),
                        stringResource(R.string.appearance_font_manrope),
                        stringResource(R.string.appearance_font_roboto),
                    ),
                    selectedIndex = settings.font.ordinal,
                    onSelect = { onFont(AppFont.entries[it]) },
                )
            }
            GroupDivider()
            ChoiceRow(
                title = stringResource(R.string.appearance_corners),
                caption = stringResource(R.string.appearance_corners_desc),
            ) {
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.appearance_corner_sharp),
                        stringResource(R.string.appearance_corner_medium),
                        stringResource(R.string.appearance_corner_round),
                    ),
                    selectedIndex = RadiusOptions.indexOf(settings.radius).coerceAtLeast(0),
                    onSelect = onRadius,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel(stringResource(R.string.appearance_section_theme))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeTile(
                label = stringResource(R.string.appearance_theme_light),
                selected = settings.themeMode == ThemeMode.LIGHT,
                onClick = { onThemeMode(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f),
            ) { Box(Modifier.fillMaxSize().background(colors.white)) }
            ThemeTile(
                label = stringResource(R.string.appearance_theme_dark),
                selected = settings.themeMode == ThemeMode.DARK,
                onClick = { onThemeMode(ThemeMode.DARK) },
                modifier = Modifier.weight(1f),
            ) {
                Box(Modifier.fillMaxSize().background(colors.backgroundDeep).border(1.dp, colors.borderStrong))
            }
            ThemeTile(
                label = stringResource(R.string.appearance_theme_system),
                selected = settings.themeMode == ThemeMode.SYSTEM,
                onClick = { onThemeMode(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(0f to colors.white, 0.5f to colors.white, 0.5f to colors.background)),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (picker) {
        ColorPickerDialog(
            initial = settings.accent,
            onDismiss = { picker = false },
            onConfirm = { color -> onAccent(color); picker = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val style = LocalClodexStyle.current
    Text(
        text,
        style = style.type.caption,
        color = style.colors.textMuted2,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    caption: String,
    content: @Composable () -> Unit,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = style.type.rowTitle, color = colors.textSecondary)
        Text(caption, style = style.type.captionSmall, color = colors.textMuted)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ThemeTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.backgroundSoft, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.borderStrong, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) { preview() }
        Spacer(Modifier.height(8.dp))
        Text(label, style = style.type.captionSmall, color = colors.chipText, textAlign = TextAlign.Center)
    }
}

/** Small helper so the appearance column can scroll without repeating imports. */
@Composable
private fun Modifier.verticalScrollable(): Modifier =
    this.then(Modifier.verticalScroll(rememberScrollState()))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AppearanceModernPreview() {
    ClodexTheme(AppearanceSettings()) {
        AppearanceScreen(AppearanceSettings(), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AppearanceGlassPreview() {
    ClodexTheme(AppearanceSettings(liquidGlass = true)) {
        AppearanceScreen(AppearanceSettings(liquidGlass = true), {}, {}, {}, {}, {}, {}, {})
    }
}
