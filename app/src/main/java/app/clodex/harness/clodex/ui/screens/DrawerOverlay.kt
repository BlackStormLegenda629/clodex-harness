package app.clodex.harness.clodex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clodex.harness.R
import app.clodex.harness.clodex.settings.AppearanceSettings
import app.clodex.harness.clodex.theme.ClodexTheme
import app.clodex.harness.clodex.theme.LocalClodexStyle
import app.clodex.harness.clodex.ui.components.ClodexButton
import app.clodex.harness.clodex.ui.components.ClodexButtonVariant
import app.clodex.harness.clodex.ui.icons.ClodexIcons

/** ЭКРАН 3. МЕНЮ (DrawerOverlay) */
@Composable
fun DrawerOverlay(
    onDismiss: () -> Unit,
    onChats: () -> Unit,
    onProjects: () -> Unit,
    onCode: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit = {},
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(colors.backgroundSoft)
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.drawer_brand), style = style.type.title, color = colors.textPrimary)
                Spacer(Modifier.weight(1f))
                Icon(ClodexIcons.Search, contentDescription = stringResource(R.string.cd_search), tint = colors.textMuted2, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(16.dp))
            DrawerItem(stringResource(R.string.drawer_chats), ClodexIcons.Chat, onChats)
            DrawerItem(stringResource(R.string.drawer_projects), ClodexIcons.Folder, onProjects)
            DrawerItem(stringResource(R.string.drawer_code), ClodexIcons.Code, onCode)
            DrawerItem(stringResource(R.string.drawer_artifacts), ClodexIcons.Artifacts, onDismiss)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(colors.surfaceBubble))
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.drawer_recent),
                style = style.type.caption,
                color = colors.textMuted2,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            RecentItem(stringResource(R.string.recent_auth), active = true, onClick = onChats)
            RecentItem(stringResource(R.string.recent_design), active = false, onClick = onChats)
            RecentItem(stringResource(R.string.recent_prototype), active = false, onClick = onChats)
            RecentItem(stringResource(R.string.recent_html), active = false, onClick = onChats)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSettings)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.profile_letter),
                        style = style.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = colors.onAccent,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.profile_name),
                    style = style.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
            }
            ClodexButton(
                text = stringResource(R.string.new_chat),
                onClick = onNewChat,
                variant = ClodexButtonVariant.White,
                icon = ClodexIcons.Plus,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = style.type.body, color = colors.textSecondary)
    }
}

@Composable
private fun RecentItem(label: String, active: Boolean, onClick: () -> Unit) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (active) Modifier.background(colors.surfaceBubble, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = style.type.caption.copy(fontSize = style.type.description.fontSize),
            color = if (active) colors.textPrimary else colors.textSecondary,
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DrawerModernPreview() {
    ClodexTheme(AppearanceSettings()) {
        Box(modifier = Modifier.fillMaxSize().background(LocalClodexStyle.current.colors.background)) {
            DrawerOverlay({}, {}, {}, {}, {})
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DrawerGlassPreview() {
    ClodexTheme(AppearanceSettings(liquidGlass = true)) {
        Box(modifier = Modifier.fillMaxSize().background(LocalClodexStyle.current.colors.backgroundDeep)) {
            DrawerOverlay({}, {}, {}, {}, {})
        }
    }
}
