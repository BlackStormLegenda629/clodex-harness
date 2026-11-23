package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import app.clodex.harness.R
import app.clodex.harness.clodex.theme.LocalClodexStyle
import app.clodex.harness.clodex.theme.TabStyle
import app.clodex.harness.clodex.ui.icons.ClodexIcons

enum class ClodexTab { Chat, Projects, Code, Artifacts }

private data class TabSpec(val tab: ClodexTab, val label: String, val icon: ImageVector)

@Composable
private fun tabSpecs(): List<TabSpec> = listOf(
    TabSpec(ClodexTab.Chat, stringResource(R.string.tab_chat), ClodexIcons.Chat),
    TabSpec(ClodexTab.Projects, stringResource(R.string.tab_projects), ClodexIcons.Folder),
    TabSpec(ClodexTab.Code, stringResource(R.string.tab_code), ClodexIcons.Code),
    TabSpec(ClodexTab.Artifacts, stringResource(R.string.tab_artifacts), ClodexIcons.Artifacts),
)

/** Чат / Проекты / Code / Артефакты; shown only on Home and Projects. */
@Composable
fun BottomTabBar(
    active: ClodexTab,
    onSelect: (ClodexTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (LocalClodexStyle.current.tabStyle) {
        TabStyle.MODERN -> ModernTabBar(active, onSelect, modifier)
        TabStyle.MATERIAL -> MaterialTabBar(active, onSelect, modifier)
        TabStyle.GLASS -> GlassTabBar(active, onSelect, modifier)
    }
}

@Composable
private fun ModernTabBar(active: ClodexTab, onSelect: (ClodexTab) -> Unit, modifier: Modifier) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = CircleShape
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .height(68.dp)
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabSpecs().forEach { spec ->
            val selected = spec.tab == active
            Column(
                modifier = Modifier
                    .clip(shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(spec.tab) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(shape)
                        .then(if (selected) Modifier.background(colors.accent, shape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        spec.icon,
                        contentDescription = spec.label,
                        tint = if (selected) colors.onAccent else colors.textMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    spec.label,
                    style = style.type.captionSmall,
                    color = if (selected) colors.textPrimary else colors.textMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun MaterialTabBar(active: ClodexTab, onSelect: (ClodexTab) -> Unit, modifier: Modifier) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = CircleShape
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(80.dp)
            .background(colors.navBar)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabSpecs().forEach { spec ->
            val selected = spec.tab == active
            Column(
                modifier = Modifier
                    .clip(shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(spec.tab) }
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(32.dp)
                        .clip(shape)
                        .then(if (selected) Modifier.background(colors.secondaryContainer, shape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        spec.icon,
                        contentDescription = spec.label,
                        tint = if (selected) colors.onSecondaryContainer else colors.textMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    spec.label,
                    style = style.type.captionSmall,
                    color = if (selected) colors.textPrimary else colors.textMuted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun GlassTabBar(active: ClodexTab, onSelect: (ClodexTab) -> Unit, modifier: Modifier) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = CircleShape
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .height(68.dp)
            .glassSurface(shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabSpecs().forEach { spec ->
            val selected = spec.tab == active
            Column(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (selected) {
                            Modifier
                                .width(56.dp)
                                .height(52.dp)
                                .background(Color.White.copy(alpha = 0.22f), shape)
                                .border(1.dp, colors.glassBorder, shape)
                        } else {
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(spec.tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    spec.icon,
                    contentDescription = spec.label,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    spec.label,
                    style = style.type.captionSmall,
                    color = Color.White,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
