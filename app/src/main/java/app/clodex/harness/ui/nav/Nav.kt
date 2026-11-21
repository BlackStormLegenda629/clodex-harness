package app.clodex.harness.ui.nav

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import app.clodex.harness.AppContainer
import app.clodex.harness.ui.vm.*
import app.clodex.harness.util.ToastBus

object Routes {
    const val MAIN = "main"
    const val CHATS = "chats"
    const val SEARCH = "search"
    const val SETTINGS = "settings/{id}"
    const val PROJECT = "project/{id}"
    const val CONTEXT = "context/{id}"
    const val MEMORY = "memory/{id}"
    const val SESSION = "session/{id}"
    const val DIFF = "diff/{id}"
    const val ARTIFACT = "artifact/{id}"
    const val BROWSE = "browse?uri={uri}"
    const val TERM = "term"
}

class Vms(
    val container: AppContainer,
    val app: AppViewModel, val chat: ChatViewModel, val projects: ProjectsViewModel, val artifacts: ArtifactsViewModel,
    val code: CodeViewModel, val skills: SkillsViewModel, val connectors: ConnectorsViewModel, val settings: SettingsViewModel,
)

class AttachActions(val camera: () -> Unit, val photo: () -> Unit, val files: () -> Unit, val video: () -> Unit)

@Stable
class Nav(val controller: NavHostController, private val app: AppViewModel) {
    fun push(route: String) { controller.navigate(route) }
    fun pop() { controller.popBackStack() }
    fun settings(id: String = "root") = push("settings/$id")
    fun search() = push(Routes.SEARCH)
    fun project(id: Long) = push("project/$id")
    fun session(id: Long) = push("session/$id")
    fun artifact(id: Long) = push("artifact/$id")
    fun browse(uri: String) = push("browse?uri=" + Uri.encode(uri))
    fun terminal() = push(Routes.TERM)

    /** Tab switch from anywhere: back to the tab root, tab selected, stack cleared (the prototype's `tab|x`). */
    fun toTab(tab: AppTab) {
        app.setTab(tab)
        app.close()
        controller.popBackStack(Routes.MAIN, inclusive = false)
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav not provided") }
val LocalVms = staticCompositionLocalOf<Vms> { error("Vms not provided") }
val LocalToasts = staticCompositionLocalOf<ToastBus> { error("ToastBus not provided") }
val LocalAttach = staticCompositionLocalOf<AttachActions> { error("AttachActions not provided") }
