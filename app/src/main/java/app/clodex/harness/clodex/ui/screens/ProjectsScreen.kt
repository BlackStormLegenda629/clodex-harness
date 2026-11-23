package app.clodex.harness.clodex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clodex.harness.R
import app.clodex.harness.clodex.settings.AppearanceSettings
import app.clodex.harness.clodex.theme.ClodexTheme
import app.clodex.harness.clodex.theme.LocalClodexStyle
import app.clodex.harness.clodex.ui.components.BottomTabBar
import app.clodex.harness.clodex.ui.components.ClodexButton
import app.clodex.harness.clodex.ui.components.ClodexButtonVariant
import app.clodex.harness.clodex.ui.components.ClodexTab
import app.clodex.harness.clodex.ui.components.IconCircleButton
import app.clodex.harness.clodex.ui.components.SquareIcon
import app.clodex.harness.clodex.ui.components.clodexBackground
import app.clodex.harness.clodex.ui.icons.ClodexIcons

data class ProjectRow(val title: String, val meta: String)

/** ЭКРАН 5. ПРОЕКТЫ */
@Composable
fun ProjectsScreen(
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onTab: (ClodexTab) -> Unit,
    onNewProject: () -> Unit,
    titleRes: Int = R.string.projects_title,
    items: List<ProjectRow> = defaultProjects(),
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Column(modifier = Modifier.fillMaxSize().clodexBackground()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(ClodexIcons.Menu, stringResource(R.string.cd_menu), onMenu)
            Spacer(Modifier.weight(1f))
            IconCircleButton(ClodexIcons.Search, stringResource(R.string.cd_search), onSearch)
        }
        Text(
            stringResource(titleRes),
            style = style.type.display,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items) { row ->
                val shape = RoundedCornerShape(style.radius)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(colors.surface, shape)
                        .border(1.dp, colors.border, shape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val first = row == items.first()
                    SquareIcon(
                        icon = ClodexIcons.Folder,
                        background = if (first) colors.accent else colors.surfaceBubble,
                        tint = if (first) colors.onAccent else colors.textPrimary,
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.title, style = style.type.cardTitle, color = colors.textPrimary)
                        Text(row.meta, style = style.type.caption, color = colors.textMuted)
                    }
                    Icon(
                        ClodexIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.textMuted2,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            item {
                ClodexButton(
                    text = stringResource(R.string.project_new),
                    onClick = onNewProject,
                    variant = ClodexButtonVariant.Outlined,
                    icon = ClodexIcons.Plus,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
        BottomTabBar(active = ClodexTab.Projects, onSelect = onTab, modifier = Modifier.padding(bottom = 28.dp))
    }
}

@Composable
fun defaultProjects(): List<ProjectRow> = listOf(
    ProjectRow(stringResource(R.string.project_harness), stringResource(R.string.project_harness_meta)),
    ProjectRow(stringResource(R.string.project_design), stringResource(R.string.project_design_meta)),
    ProjectRow(stringResource(R.string.project_budget), stringResource(R.string.project_budget_meta)),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProjectsModernPreview() {
    ClodexTheme(AppearanceSettings()) {
        ProjectsScreen({}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProjectsGlassPreview() {
    ClodexTheme(AppearanceSettings(liquidGlass = true)) {
        ProjectsScreen({}, {}, {}, {})
    }
}
