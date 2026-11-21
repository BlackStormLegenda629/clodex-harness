package app.clodex.harness.ui.vm

import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import app.clodex.harness.AppContainer
import app.clodex.harness.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModelFetch(val loading: Boolean = false, val models: List<String> = emptyList(), val error: String? = null)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.settings
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy = _backupBusy.asStateFlow()

    fun exportBackup(uri: Uri) = backupOperation("Не удалось сохранить копию") {
        c.backups.exportTo(uri)
        "Резервная копия сохранена"
    }

    fun importBackup(uri: Uri) = backupOperation("Не удалось импортировать копию") {
        if (c.backups.importFrom(uri)) "Данные из копии добавлены"
        else "Данные добавлены, но настройки сохранить не удалось"
    }

    private fun backupOperation(error: String, action: suspend () -> String) = viewModelScope.launch {
        if (_backupBusy.value) return@launch
        _backupBusy.value = true
        try {
            c.toasts.show(action())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            c.toasts.show(error)
        } finally {
            _backupBusy.value = false
        }
    }
    val settings: StateFlow<AppSettings> = repo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, c.initialSettings)
    val mcpConfigs = c.mcp.configs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    fun revokeMcp(grant: app.clodex.harness.data.mcp.McpGrant) = viewModelScope.launch {
        c.mcp.revoke(grant.permission, grant.chatId)
    }

    private val _fetch = MutableStateFlow(ModelFetch())
    val fetch: StateFlow<ModelFetch> = _fetch

    fun <T> set(key: Preferences.Key<T>, v: T) = viewModelScope.launch { repo.set(key, v) }
    fun toggle(name: String) = viewModelScope.launch { repo.setToggle(name, !settings.value.on(name)) }
    fun setEffort(name: String) = set(K.effort, name)

    /** Selecting a model also selects the provider that serves it. */
    fun setModel(display: String) = viewModelScope.launch {
        settings.value.providerForModel(display)?.let { repo.set(K.providerId, it.id) }
        repo.set(K.model, display)
    }

    fun setProvider(id: String) = viewModelScope.launch {
        val s = settings.value
        val p = s.provider(id) ?: return@launch
        repo.set(K.providerId, id)
        val available = ModelCatalog.merged(p).map { it.display }
        if (s.model !in available) repo.set(K.model, ModelCatalog.preferredModel(p, s.model) ?: s.model)
    }

    /** Creates or updates a provider and makes it active. */
    fun saveProvider(cfg: ProviderCfg) = viewModelScope.launch {
        val cur = settings.value.providers
        val list = if (cur.any { it.id == cfg.id }) cur.map { if (it.id == cfg.id) cfg else it } else cur + cfg
        repo.setProviders(list)
        repo.set(K.providerId, cfg.id)
        // After a successful /models refresh an exact fetched id replaces an alias the provider does not know.
        ModelCatalog.preferredModel(cfg, settings.value.model)?.let { repo.set(K.model, it) }
        c.toasts.show(if (cfg.builtin) "${cfg.name} сохранён" else "Провайдер «${cfg.name}» сохранён")
    }

    fun deleteProvider(id: String) = viewModelScope.launch {
        val s = settings.value
        val p = s.provider(id) ?: return@launch
        if (p.builtin) return@launch
        val list = s.providers.filterNot { it.id == id }
        repo.setProviders(list)
        if (s.providerId == id) {
            val next = list.firstOrNull() ?: DefaultProviders.first()
            repo.set(K.providerId, next.id)
            repo.set(K.model, ModelCatalog.defaultModel(next.id))
        }
        c.toasts.show("Провайдер удалён")
    }

    fun clearFetch() { _fetch.value = ModelFetch() }

    /** GET {base}/models, both for "Проверить" and for filling a custom provider's model list. */
    fun fetchModels(url: String, key: String, format: String, headers: String) = viewModelScope.launch {
        _fetch.value = ModelFetch(loading = true)
        c.providerChecker.models(url, key, format, headers).fold(
            onSuccess = { list ->
                _fetch.value = ModelFetch(models = list)
                c.toasts.show("Загружено моделей: ${list.size}")
            },
            onFailure = {
                _fetch.value = ModelFetch(error = it.message ?: "Не удалось загрузить модели")
                c.toasts.show(it.message ?: "Не удалось загрузить модели")
            },
        )
    }

    fun removeAlwaysAllowed(cmd: String) = viewModelScope.launch { repo.setAlwaysAllowed(settings.value.alwaysAllowed - cmd) }

    fun wipeAll(onDone: () -> Unit) = viewModelScope.launch { c.wipeAll(); onDone() }
}

class VmFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when (modelClass) {
        AppViewModel::class.java -> AppViewModel(c.settings, extras.createSavedStateHandle(), c.initialSettings.tab)
        ChatViewModel::class.java -> ChatViewModel(c)
        ProjectsViewModel::class.java -> ProjectsViewModel(c, extras.createSavedStateHandle())
        ArtifactsViewModel::class.java -> ArtifactsViewModel(c)
        CodeViewModel::class.java -> c.codeVm
        SkillsViewModel::class.java -> SkillsViewModel(c.skillRepo, c.skillFetcher, c.toasts, c.caveman)
        ConnectorsViewModel::class.java -> ConnectorsViewModel(c.connectorRepo, c.mcp, c.toasts)
        SettingsViewModel::class.java -> SettingsViewModel(c)
        else -> error("Unknown ViewModel: $modelClass")
    } as T
}
