package app.clodex.harness.clodex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clodex.harness.R
import app.clodex.harness.clodex.settings.AppearanceSettings
import app.clodex.harness.clodex.theme.ClodexTheme
import app.clodex.harness.clodex.theme.LocalClodexStyle
import app.clodex.harness.clodex.ui.components.ChipRow
import app.clodex.harness.clodex.ui.components.ChipSpec
import app.clodex.harness.clodex.ui.components.ClodexButton
import app.clodex.harness.clodex.ui.components.ClodexButtonVariant
import app.clodex.harness.clodex.ui.components.ComposerBar
import app.clodex.harness.clodex.ui.components.IconCircleButton
import app.clodex.harness.clodex.ui.components.clodexBackground
import app.clodex.harness.clodex.ui.icons.ClodexIcons

/** ЭКРАН 7. COWORK */
@Composable
fun CoworkScreen(
    onMenu: () -> Unit,
    onPlus: () -> Unit,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    var text by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().clodexBackground()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(ClodexIcons.Menu, stringResource(R.string.cd_menu), onMenu)
            Spacer(Modifier.weight(1f))
            IconCircleButton(ClodexIcons.Plus, stringResource(R.string.cd_add), onPlus)
        }
        Text(
            stringResource(R.string.cowork_title),
            style = style.type.display,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            stringResource(R.string.cowork_description),
            style = style.type.description,
            color = colors.textMuted,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { CoworkProgressCard() }
            item { CoworkDoneCard() }
            item { CoworkPendingCard() }
        }
        ComposerBar(
            hint = stringResource(R.string.cowork_hint),
            value = text,
            onValueChange = { text = it },
            onSend = { text = "" },
            leadingChip = {
                ChipRow(chips = listOf(ChipSpec(stringResource(R.string.cowork_chip_docs), ClodexIcons.Folder)))
            },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun CoworkProgressCard() {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = RoundedCornerShape(style.radius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.cowork_1_title),
                style = style.type.rowTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.cowork_1_percent),
                style = style.type.captionSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accent,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surfaceChip, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.accent, RoundedCornerShape(3.dp)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ClodexIcons.Clock, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.cowork_1_meta), style = style.type.captionSmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun CoworkDoneCard() {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = RoundedCornerShape(style.radius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.cowork_2_title), style = style.type.rowTitle, color = colors.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ClodexIcons.Check, contentDescription = null, tint = colors.successStrong, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.cowork_2_meta), style = style.type.captionSmall, color = colors.textMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FileChip(stringResource(R.string.cowork_2_file_1))
            FileChip(stringResource(R.string.cowork_2_file_2))
        }
    }
}

@Composable
private fun FileChip(label: String) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(colors.surfaceChip, CircleShape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ClodexIcons.File, contentDescription = null, tint = colors.chipText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, style = style.type.captionSmall, color = colors.chipText)
    }
}

@Composable
private fun CoworkPendingCard() {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = RoundedCornerShape(style.radius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.pendingBorder, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.cowork_3_title), style = style.type.rowTitle, color = colors.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ClodexIcons.Shield, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.cowork_3_meta), style = style.type.captionSmall, color = colors.warning)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClodexButton(
                text = stringResource(R.string.action_decline),
                onClick = {},
                variant = ClodexButtonVariant.Ghost,
                modifier = Modifier.weight(1f).height(40.dp),
            )
            ClodexButton(
                text = stringResource(R.string.action_allow),
                onClick = {},
                variant = ClodexButtonVariant.Primary,
                modifier = Modifier.weight(1f).height(40.dp),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CoworkModernPreview() {
    ClodexTheme(AppearanceSettings()) {
        CoworkScreen({}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CoworkGlassPreview() {
    ClodexTheme(AppearanceSettings(liquidGlass = true)) {
        CoworkScreen({}, {})
    }
}
