package app.clodex.harness.clodex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.clodex.harness.clodex.ui.components.BackHeader
import app.clodex.harness.clodex.ui.components.ClodexButton
import app.clodex.harness.clodex.ui.components.ClodexButtonVariant
import app.clodex.harness.clodex.ui.components.GroupCard
import app.clodex.harness.clodex.ui.components.GroupDivider
import app.clodex.harness.clodex.ui.components.SettingsRow
import app.clodex.harness.clodex.ui.components.clodexBackground
import app.clodex.harness.clodex.ui.icons.ClodexIcons

/** ЭКРАН 8. НАСТРОЙКИ */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onInfo: () -> Unit,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Column(modifier = Modifier.fillMaxSize().clodexBackground().padding(horizontal = 16.dp)) {
        BackHeader(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
            trailingIcon = ClodexIcons.Info,
            trailingDescription = stringResource(R.string.cd_info),
            onTrailing = onInfo,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_email),
                style = style.type.description,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.settings_plan_free),
                style = style.type.captionSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onAccent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.white, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(style.radius))
                .border(1.dp, colors.borderStrong, RoundedCornerShape(style.radius))
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.settings_upgrade_title), style = style.type.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.settings_upgrade_desc), style = style.type.caption, color = colors.textMuted)
            Spacer(Modifier.height(12.dp))
            ClodexButton(
                text = stringResource(R.string.settings_upgrade_action),
                onClick = {},
                variant = ClodexButtonVariant.White,
                modifier = Modifier.fillMaxWidth().height(44.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        GroupCard {
            SettingsRow(stringResource(R.string.settings_profile), icon = ClodexIcons.Person, chevron = true)
            GroupDivider()
            SettingsRow(stringResource(R.string.settings_payment), icon = ClodexIcons.Card, chevron = true)
        }
        Spacer(Modifier.height(10.dp))
        GroupCard {
            SettingsRow(stringResource(R.string.settings_capabilities), icon = ClodexIcons.Sliders, value = stringResource(R.string.settings_capabilities_value))
            GroupDivider()
            SettingsRow(stringResource(R.string.settings_connectors), icon = ClodexIcons.Connectors, value = stringResource(R.string.settings_connectors_value))
            GroupDivider()
            SettingsRow(stringResource(R.string.settings_permissions), icon = ClodexIcons.Shield)
        }
        Spacer(Modifier.height(10.dp))
        GroupCard {
            SettingsRow(
                title = stringResource(R.string.settings_appearance),
                caption = stringResource(R.string.settings_appearance_desc),
                icon = ClodexIcons.Palette,
                chevron = true,
                onClick = onAppearance,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsModernPreview() {
    ClodexTheme(AppearanceSettings()) {
        SettingsScreen({}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsGlassPreview() {
    ClodexTheme(AppearanceSettings(liquidGlass = true)) {
        SettingsScreen({}, {}, {})
    }
}
