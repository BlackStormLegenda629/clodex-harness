package app.clodex.harness.ui.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.clodex.harness.data.K
import app.clodex.harness.data.SettingsRepository
import app.clodex.harness.ui.icons.HIcon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab(val key: String, val label: String, val icon: HIcon) {
    CHAT("chat", "Чат", HIcon.Chat),
    PROJ("proj", "Проекты", HIcon.Stack),
    CODE("code", "Code", HIcon.Code),
    ART("art", "Артефакты", HIcon.Cards);

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } ?: CHAT }
}

enum class SheetKind {
    Add, Model, Effort, MoreModels, AddToProject,
    NewProject, ProjectMenu, DeleteProject, Instructions, ProjectFilter,
    NewSession, Workspace, GhWorkspace,
    SkillAdd, SkillNew, SkillGh, SkillDetail,
    ConnectorDetail, ConnectorAdd, ConnectorConfig,
    ProviderEdit, FilePreview,
    WipeConfirm, Licenses,
}

data class Sheet(val kind: SheetKind, val arg: String? = null) {
    fun encode() = kind.name + "|" + (arg ?: "")
    companion object {
        fun decode(s: String?): Sheet? {
            if (s.isNullOrEmpty()) return null
            val k = SheetKind.entries.firstOrNull { it.name == s.substringBefore('|') } ?: return null
            return Sheet(k, s.substringAfter('|', "").ifEmpty { null })
        }
    }
}

class AppViewModel(
    private val settings: SettingsRepository,
    private val handle: SavedStateHandle,
    initialTab: String,
) : ViewModel() {
    val tab: StateFlow<AppTab> = handle.getStateFlow("tab", initialTab).map { AppTab.of(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTab.of(initialTab))

    val sheet: StateFlow<Sheet?> = handle.getStateFlow<String?>("sheet", null).map { Sheet.decode(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Sheet.decode(handle.get<String?>("sheet")))

    fun setTab(t: AppTab) {
        handle["tab"] = t.key
        viewModelScope.launch { settings.set(K.tab, t.key) }
    }

    fun open(kind: SheetKind, arg: String? = null) { handle["sheet"] = Sheet(kind, arg).encode() }
    fun close() { handle["sheet"] = null }
    fun closeIfOpen(kind: SheetKind) {
        if (Sheet.decode(handle.get<String?>("sheet"))?.kind == kind) close()
    }
}
