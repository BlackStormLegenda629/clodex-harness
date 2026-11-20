package app.clodex.harness.ui.vm

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.clodex.harness.AppContainer
import app.clodex.harness.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    private val dao = c.db.projects()

    val projects: StateFlow<List<ProjectEntity>> = dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** "Ваши" | "Архивированные" */
    val filter: StateFlow<String> = handle.getStateFlow("pf", "Ваши")
    fun setFilter(v: String) { handle["pf"] = v }

    fun project(id: Long): Flow<ProjectEntity?> = dao.observe(id)
    fun chatsOf(id: Long): Flow<List<ChatEntity>> = c.db.chats().observeByProject(id)
    fun sessionsOf(id: Long): Flow<List<SessionEntity>> = c.db.sessions().observeByProject(id)

    /** Returns the new id through [onDone] so the screen can open it. */
    fun create(name: String, description: String, onDone: (Long) -> Unit) = viewModelScope.launch {
        val id = dao.insert(ProjectEntity(name = name.trim(), description = description, updatedAt = System.currentTimeMillis()))
        onDone(id)
    }

    private suspend fun edit(id: Long, f: (ProjectEntity) -> ProjectEntity) {
        dao.get(id)?.let { dao.update(f(it).copy(updatedAt = System.currentTimeMillis())) }
    }

    fun update(id: Long, name: String, description: String) = viewModelScope.launch { edit(id) { it.copy(name = name.trim(), description = description) } }
    fun togglePin(id: Long) = viewModelScope.launch { edit(id) { it.copy(pinned = !it.pinned) } }
    fun archive(id: Long) = viewModelScope.launch { edit(id) { it.copy(archived = true) } }
    fun toggleMemory(id: Long) = viewModelScope.launch { edit(id) { it.copy(useMemory = !it.useMemory) } }
    fun memory(scope: Long) = c.db.memory().observe(scope)
    fun saveMemory(scope: Long, id: Long, text: String) = viewModelScope.launch {
        val value = text.trim().take(MEMORY_NOTE_LIMIT)
        if (value.isEmpty()) return@launch
        c.db.withTransaction {
            if (scope == 0L || dao.get(scope) != null) c.db.memory().save(MemoryNote(scope, value, id))
        }
    }
    fun deleteMemory(note: MemoryNote) = viewModelScope.launch { c.db.memory().delete(note) }
    fun saveInstructions(id: Long, text: String) = viewModelScope.launch { edit(id) { it.copy(instructions = text) } }

    fun delete(id: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        val deleted = c.db.withTransaction {
            if (c.db.schedules().isProjectRunning(id)) return@withTransaction false
            dao.detachChats(id); dao.detachSessions(id); c.db.memory().deleteScope(id); dao.delete(id)
            true
        }
        if (deleted) onDone() else c.toasts.show("Дождитесь отправки по расписанию перед удалением проекта")
    }

    /** Context files: copied into app storage, names recorded on the project. */
    fun addFiles(id: Long, uris: List<Uri>) = viewModelScope.launch {
        val names = uris.mapNotNull { c.files.importProjectFile(id, it) }
        if (names.isEmpty()) { if (uris.isNotEmpty()) c.toasts.show("Не удалось добавить файл"); return@launch }
        edit(id) { p -> p.copy(files = (p.fileList() + names).joinToString("\n")) }
    }
}

class ArtifactsViewModel(private val c: AppContainer) : ViewModel() {
    val artifacts: StateFlow<List<ArtifactEntity>> = c.db.artifacts().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun artifact(id: Long): Flow<ArtifactEntity?> = c.db.artifacts().observe(id)
}
