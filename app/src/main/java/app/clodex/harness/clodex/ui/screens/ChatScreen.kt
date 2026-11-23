package app.clodex.harness.clodex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import app.clodex.harness.clodex.ui.components.ModelChip
import app.clodex.harness.clodex.ui.components.clodexBackground
import app.clodex.harness.clodex.ui.icons.ClodexIcons

/** ЭКРАН 2. ЧАТ */
@Composable
fun ChatScreen(
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onModelPicker: () -> Unit,
    title: String,
    modelName: String,
    modelLevel: String,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    var text by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().clodexBackground()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(ClodexIcons.Menu, stringResource(R.string.cd_menu), onMenu, size = 40.dp, transparent = true)
            Text(
                title,
                style = style.type.rowTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            IconCircleButton(ClodexIcons.EditNew, stringResource(R.string.cd_new_chat), onNewChat, size = 40.dp, transparent = true)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        stringResource(R.string.chat_user_message),
                        style = style.type.body,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                            .background(colors.surfaceBubble, RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.chat_answer),
                    style = style.type.bodyRelaxed,
                    color = colors.textSecondary,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.borderStrong, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ClodexIcons.File, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.chat_file_name),
                        style = style.type.mono,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.chat_diff_stat), style = style.type.caption, color = colors.textMuted)
                }
            }
            item { ConfirmationCard(onDecline = {}, onAllow = {}) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipRow(
                chips = listOf(
                    ChipSpec(stringResource(R.string.chip_plan), ClodexIcons.Bulb),
                    ChipSpec(stringResource(R.string.chip_auto_edit), ClodexIcons.Bolt),
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        ComposerBar(
            hint = stringResource(R.string.chat_hint),
            value = text,
            onValueChange = { text = it },
            onSend = { text = "" },
            modelChip = {
                ModelChip(model = modelName, level = modelLevel, onClick = onModelPicker)
            },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun ConfirmationCard(onDecline: () -> Unit, onAllow: () -> Unit) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = RoundedCornerShape(style.radius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.chat_confirm_title), style = style.type.rowTitle, color = colors.textPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.chat_confirm_command),
            style = style.type.mono,
            color = colors.chipText,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.background, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClodexButton(
                text = stringResource(R.string.action_decline),
                onClick = onDecline,
                variant = ClodexButtonVariant.Danger,
                modifier = Modifier.weight(1f).height(44.dp),
            )
            ClodexButton(
                text = stringResource(R.string.action_allow),
                onClick = onAllow,
                variant = ClodexButtonVariant.Primary,
                modifier = Modifier.weight(1f).height(44.dp),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ChatModernPreview() {
    ClodexTheme(AppearanceSettings()) {
        ChatScreen(
            {},
            {},
            {},
            stringResource(R.string.chat_title),
            stringResource(R.string.model_sonnet),
            stringResource(R.string.level_medium),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ChatGlassPreview() {
    ClodexTheme(AppearanceSettings(liquidGlass = true)) {
        ChatScreen(
            {},
            {},
            {},
            stringResource(R.string.chat_title),
            stringResource(R.string.model_sonnet),
            stringResource(R.string.level_medium),
        )
    }
}
