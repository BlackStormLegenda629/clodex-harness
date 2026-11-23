package app.clodex.harness.clodex.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.clodex.harness.R
import app.clodex.harness.clodex.settings.AppearanceSettings
import app.clodex.harness.clodex.settings.DesignStyle
import app.clodex.harness.clodex.settings.RadiusOptions
import app.clodex.harness.clodex.theme.ClodexTheme
import app.clodex.harness.clodex.theme.LocalClodexStyle
import app.clodex.harness.clodex.ui.components.ClodexTab
import app.clodex.harness.clodex.ui.screens.AppearanceScreen
import app.clodex.harness.clodex.ui.screens.ChatScreen
import app.clodex.harness.clodex.ui.screens.CodeScreen
import app.clodex.harness.clodex.ui.screens.CoworkScreen
import app.clodex.harness.clodex.ui.screens.DrawerOverlay
import app.clodex.harness.clodex.ui.screens.HomeScreen
import app.clodex.harness.clodex.ui.screens.ModelPickerSheet
import app.clodex.harness.clodex.ui.screens.ProjectsScreen
import app.clodex.harness.clodex.ui.screens.SettingsScreen
import app.clodex.harness.clodex.ui.screens.demoCodeSessions

object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val PROJECTS = "projects"
    const val CODE = "code"
    const val COWORK = "cowork"
    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"
}

/** Navigation host plus the two overlays (menu and model picker), which are state, not routes. */
@Composable
fun AppRoot(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
) {
    ClodexTheme(settings) {
        val nav = rememberNavController()
        val names = listOf(
            stringResource(R.string.model_fable),
            stringResource(R.string.model_opus),
            stringResource(R.string.model_sonnet),
            stringResource(R.string.model_haiku),
        )
        val shell = remember(names) { ShellState(names) }
        val level = stringResource(R.string.level_medium)

        Box(modifier = Modifier.fillMaxSize().background(LocalClodexStyle.current.colors.background)) {
            NavHost(navController = nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onMenu = shell::openDrawer,
                        onNewChat = { nav.navigate(Routes.CHAT) },
                        onTab = { tab -> nav.navigate(tab.toRoute()) },
                    )
                }
                composable(Routes.CHAT) {
                    ChatScreen(
                        onMenu = shell::openDrawer,
                        onNewChat = {},
                        onModelPicker = shell::openPicker,
                        title = stringResource(R.string.chat_title),
                        modelName = shell.model,
                        modelLevel = level,
                    )
                }
                composable(Routes.PROJECTS) {
                    ProjectsScreen(
                        onMenu = shell::openDrawer,
                        onSearch = {},
                        onTab = { tab -> nav.navigate(tab.toRoute()) },
                        onNewProject = {},
                    )
                }
                composable(Routes.CODE) {
                    CodeScreen(
                        onMenu = shell::openDrawer,
                        onPlus = {},
                        sessions = demoCodeSessions(),
                    )
                }
                composable(Routes.COWORK) {
                    CoworkScreen(onMenu = shell::openDrawer, onPlus = {})
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onAppearance = { nav.navigate(Routes.APPEARANCE) },
                        onInfo = {},
                    )
                }
                composable(Routes.APPEARANCE) {
                    AppearanceScreen(
                        settings = settings,
                        onBack = { nav.popBackStack() },
                        onAccent = { onSettingsChange(settings.copy(accent = it)) },
                        onGlass = { onSettingsChange(settings.copy(liquidGlass = it)) },
                        onDesign = { design ->
                            val glass = if (design == DesignStyle.MATERIAL_YOU) false else settings.liquidGlass
                            onSettingsChange(settings.copy(design = design, liquidGlass = glass))
                        },
                        onFont = { onSettingsChange(settings.copy(font = it)) },
                        onRadius = { onSettingsChange(settings.copy(radius = RadiusOptions[it])) },
                        onThemeMode = { onSettingsChange(settings.copy(themeMode = it)) },
                    )
                }
            }
            AnimatedVisibility(visible = shell.drawer, enter = fadeIn(), exit = fadeOut()) {
                DrawerOverlay(
                    onDismiss = shell::closeDrawer,
                    onChats = { shell.closeDrawer(); nav.navigate(Routes.CHAT) },
                    onProjects = { shell.closeDrawer(); nav.navigate(Routes.PROJECTS) },
                    onCode = { shell.closeDrawer(); nav.navigate(Routes.CODE) },
                    onNewChat = { shell.closeDrawer(); nav.navigate(Routes.CHAT) },
                    onSettings = { shell.closeDrawer(); nav.navigate(Routes.SETTINGS) },
                )
            }
            AnimatedVisibility(visible = shell.picker, enter = fadeIn(), exit = fadeOut()) {
                ModelPickerSheet(
                    selected = shell.model,
                    onSelect = shell::selectModel,
                    onDismiss = shell::closePicker,
                )
            }
        }
    }
}

private fun ClodexTab.toRoute(): String = when (this) {
    ClodexTab.Chat -> Routes.CHAT
    ClodexTab.Projects -> Routes.PROJECTS
    ClodexTab.Code -> Routes.CODE
    ClodexTab.Artifacts -> Routes.CHAT
}
