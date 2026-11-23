package app.clodex.harness.clodex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import app.clodex.harness.clodex.ui.components.glassSurface
import app.clodex.harness.clodex.ui.icons.ClodexIcons

/** ЭКРАН 4. ВЫБОР МОДЕЛИ (ModelPickerSheet) */
@Composable
fun ModelPickerSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.backgroundDeep.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .glassSurface(shape)
                .clip(shape)
                .background(if (LocalClodexStyle.current.glass) androidx.compose.ui.graphics.Color.Transparent else colors.surfaceElevated, shape)
                .padding(top = 10.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.handle)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    ClodexIcons.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = colors.textPrimary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                )
                Text(
                    stringResource(R.string.model_picker_title),
                    style = style.type.subtitle,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Box(Modifier.size(20.dp))
            }
            Spacer(Modifier.height(14.dp))
            val fable = stringResource(R.string.model_fable)
            val opus = stringResource(R.string.model_opus)
            val sonnet = stringResource(R.string.model_sonnet)
            val haiku = stringResource(R.string.model_haiku)
            ModelCard(
                name = fable,
                badge = stringResource(R.string.model_fable_badge),
                description = stringResource(R.string.model_fable_desc),
                selected = selected == fable,
                onClick = { onSelect(fable) },
            )
            ModelCard(
                name = opus,
                badge = stringResource(R.string.model_opus_badge),
                description = stringResource(R.string.model_opus_desc),
                selected = selected == opus,
                onClick = { onSelect(opus) },
            )
            ModelCard(
                name = sonnet,
                badge = null,
                description = stringResource(R.string.model_sonnet_desc),
                selected = selected == sonnet,
                onClick = { onSelect(sonnet) },
            )
            ModelCard(
                name = haiku,
                badge = null,
                description = stringResource(R.string.model_haiku_desc),
                selected = selected == haiku,
                onClick = { onSelect(haiku) },
            )
            Spacer(Modifier.height(8.dp))
            SimpleRow(
                title = stringResource(R.string.model_effort),
                caption = stringResource(R.string.model_effort_value),
                onClick = {},
            )
            Spacer(Modifier.height(8.dp))
            SimpleRow(title = stringResource(R.string.model_other), caption = null, onClick = {})
        }
    }
}

@Composable
private fun ModelCard(
    name: String,
    badge: String?,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(colors.surfaceBubble, shape)
            .then(if (selected) Modifier.border(1.dp, colors.accent, shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = style.type.subtitle, color = colors.textPrimary)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(22.dp)
                            .clip(CircleShape)
                            .background(colors.modelBadgeBg, CircleShape)
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            badge,
                            style = style.type.captionSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.modelBadgeText,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(description, style = style.type.caption, color = colors.textMuted)
        }
        if (selected) {
            Icon(ClodexIcons.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SimpleRow(title: String, caption: String?, onClick: () -> Unit) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(colors.surfaceBubble, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = style.type.rowTitle, color = colors.textPrimary)
            if (caption != null) {
                Spacer(Modifier.height(2.dp))
                Text(caption, style = style.type.caption, color = colors.textMuted)
            }
        }
        Icon(
            ClodexIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted2,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ModelPickerModernPreview() {
    ClodexTheme(AppearanceSettings()) {
        ModelPickerSheet("Sonnet 5.5", {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ModelPickerGlassPreview() {
    ClodexTheme(AppearanceSettings(liquidGlass = true)) {
        ModelPickerSheet("Sonnet 5.5", {}, {})
    }
}
