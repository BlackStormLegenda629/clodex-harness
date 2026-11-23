package app.clodex.harness.clodex.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clodex.harness.R
import app.clodex.harness.clodex.settings.AppearanceSettings
import app.clodex.harness.clodex.settings.DesignStyle
import app.clodex.harness.clodex.theme.ClodexTheme
import app.clodex.harness.clodex.theme.LocalClodexStyle
import app.clodex.harness.clodex.ui.components.BottomTabBar
import app.clodex.harness.clodex.ui.components.ClodexTab
import app.clodex.harness.clodex.ui.components.ComposerBar
import app.clodex.harness.clodex.ui.components.IconCircleButton
import app.clodex.harness.clodex.ui.components.clodexBackground
import app.clodex.harness.clodex.ui.icons.ClodexIcons

/** ЭКРАН 1. ГЛАВНАЯ */
@Composable
fun HomeScreen(
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onTab: (ClodexTab) -> Unit,
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
            IconCircleButton(ClodexIcons.EditNew, stringResource(R.string.cd_new_chat), onNewChat)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.home_title),
                style = style.type.display,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
        Box(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            ComposerBar(
                hint = stringResource(R.string.home_hint),
                value = text,
                onValueChange = { text = it },
                onSend = { text = "" },
                whiteVoice = true,
            )
        }
        BottomTabBar(active = ClodexTab.Chat, onSelect = onTab, modifier = Modifier.padding(bottom = 28.dp))
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeModernPreview() {
    ClodexTheme(AppearanceSettings()) {
        HomeScreen(onMenu = {}, onNewChat = {}, onTab = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeGlassPreview() {
    ClodexTheme(AppearanceSettings(liquidGlass = true, design = DesignStyle.MODERN)) {
        HomeScreen(onMenu = {}, onNewChat = {}, onTab = {})
    }
}
