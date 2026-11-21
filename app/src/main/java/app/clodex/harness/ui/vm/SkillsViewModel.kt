package app.clodex.harness.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.clodex.harness.data.CavemanIntensity
import app.clodex.harness.data.CavemanRepository
import app.clodex.harness.data.SkillEntity
import app.clodex.harness.data.SkillFetcher
import app.clodex.harness.data.SkillParser
import app.clodex.harness.data.SkillRepository
import app.clodex.harness.data.Src
import app.clodex.harness.util.ToastBus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SkillsViewModel(
    private val repo: SkillRepository,
    private val fetcher: SkillFetcher,
    private val toasts: ToastBus,
    private val caveman: CavemanRepository,
) : ViewModel() {
    val skills: StateFlow<List<SkillEntity>> = repo.skills.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun installCaveman() = viewModelScope.launch { caveman.install() }

    fun uninstallCaveman() = viewModelScope.launch { caveman.uninstall() }

    fun setCavemanIntensity(intensity: CavemanIntensity) = viewModelScope.launch { caveman.setIntensity(intensity) }

    fun toggleCavemanSkill(id: String) = viewModelScope.launch {
        repo.get(id)?.let { caveman.setSkillEnabled(id, !it.enabled) }
    }

    private suspend fun nextSort(): Long = (repo.skills.first().maxOfOrNull { it.sortKey } ?: -1L) + 1

    fun toggle(id: String) = viewModelScope.launch {
        repo.get(id)?.let { repo.upsert(it.copy(enabled = !it.enabled)) }
    }

    /** Create ("Создать скилл") when [id] is null, otherwise edit ("Сохранить"). */
    fun saveManual(id: String?, name: String, body: String, onDone: (created: Boolean?) -> Unit = {}) = viewModelScope.launch {
        val n = name.trim(); val b = body.trim()
        if (n.isEmpty()) { toasts.show("Введите название"); onDone(null); return@launch }
        val desc = b.lines().firstOrNull().orEmpty().take(60).ifEmpty { "Без инструкций" }
        val existing = id?.let { repo.get(it) }
        if (existing != null) {
            repo.upsert(existing.copy(name = n, body = b, description = desc))
            onDone(false)
        } else {
            repo.upsert(SkillEntity("k" + System.nanoTime(), n, desc, Src.MINE, true, "Pen", body = b, sortKey = nextSort()))
            onDone(true)
        }
    }

    /** Real fetch of SKILL.md via the GitHub contents API; nothing is added if it fails. */
    fun installFromGitHub(repoInput: String, path: String, branch: String, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val r = SkillParser.parseRepo(repoInput)
        if (r == null) { toasts.show("Укажите репозиторий в формате owner/repo"); onDone(false); return@launch }
        val ref = branch.trim().ifEmpty { "main" }
        val p = path.trim().trim('/').ifEmpty { "/" }
        fetcher.fetch(r, if (p == "/") "" else p, ref).fold(
            onSuccess = { remote ->
                val dup = repo.skills.first().firstOrNull { it.source == Src.GITHUB && it.repo == r && it.path == p && it.ref == ref }
                repo.upsert(
                    (dup ?: SkillEntity("g" + System.nanoTime(), remote.name, "", Src.GITHUB, true, "Branch", sortKey = nextSort()))
                        .copy(name = remote.name, description = remote.description, body = remote.body, repo = r, path = p, ref = ref),
                )
                toasts.show("Скилл установлен из $r"); onDone(true)
            },
            onFailure = { toasts.show(it.message ?: "Не удалось получить скилл"); onDone(false) },
        )
    }

    fun refreshFromGitHub(id: String) = viewModelScope.launch {
        val s = repo.get(id) ?: return@launch
        val r = s.repo ?: return@launch
        fetcher.fetch(r, if (s.path == "/") "" else s.path.orEmpty(), s.ref ?: "main").fold(
            onSuccess = { remote -> repo.upsert(s.copy(description = remote.description, body = remote.body)); toasts.show("Обновлено из GitHub: ${s.name}") },
            onFailure = { toasts.show(it.message ?: "Не удалось обновить") },
        )
    }

    /** Builtin skills cannot be deleted, only switched off. */
    fun delete(id: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        val s = repo.get(id) ?: return@launch
        if (s.source == Src.BUILTIN) { toasts.show("Встроенный скилл нельзя удалить"); return@launch }
        repo.delete(id); onDone()
    }
}
