package app.clodex.harness.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.clodex.harness.data.ConStatus
import app.clodex.harness.data.ConnectorEntity
import app.clodex.harness.data.ConnectorRepository
import app.clodex.harness.data.mcp.McpConnectionConfig
import app.clodex.harness.data.mcp.McpRepository
import app.clodex.harness.data.mcp.McpProtocolException
import app.clodex.harness.data.mcp.McpAuthMode
import app.clodex.harness.util.ToastBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/** Connectors become ON only after a real MCP initialize + tools/list succeeds. */
class ConnectorsViewModel(
    private val repo: ConnectorRepository,
    private val mcp: McpRepository,
    private val toasts: ToastBus,
) : ViewModel() {
    val connectors: StateFlow<List<ConnectorEntity>> = combine(repo.connectors, mcp.configs) { entries, configs ->
        entries.map { entry ->
            if (entry.id != "gm") entry else {
                val config = configs[entry.id]
                when {
                    config?.authMode != McpAuthMode.GOOGLE && entry.status != ConStatus.WARN -> entry.copy(status = ConStatus.NEW)
                    config?.lastError != null -> entry.copy(status = ConStatus.WARN)
                    else -> entry
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val configs: StateFlow<Map<String, McpConnectionConfig>> = mcp.configs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _checking = MutableStateFlow<Set<String>>(emptySet())
    val checking: StateFlow<Set<String>> = _checking
    private val logins = mutableMapOf<String, Job>()

    fun cancelLogin(id: String) { logins[id]?.cancel() }

    fun finishGmailLogin(access: String) {
        if ("gm" in checking.value) return
        _checking.update { it + "gm" }
        logins["gm"] = viewModelScope.launch {
            try {
                mcp.connectGmail(access).getOrThrow()
                setStatus("gm", ConStatus.ON)
                toasts.show("Подключено: Gmail")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                setStatus("gm", ConStatus.WARN)
                toasts.show(if (e is McpProtocolException) "Gmail: ${e.message}" else "Не удалось проверить доступ к Gmail")
            } finally {
                _checking.update { it - "gm" }; logins.remove("gm")
            }
        }
    }

    fun browserConnect(id: String, endpoint: String, open: (String) -> Unit) {
        if (id in checking.value) return
        _checking.update { it + id }
        logins[id] = viewModelScope.launch {
            try {
                val connector = repo.get(id) ?: return@launch
                mcp.authorize(id, endpoint, open).getOrThrow()
                setStatus(id, ConStatus.ON)
                toasts.show("Подключено: ${connector.name}")
            } catch (_: TimeoutCancellationException) {
                toasts.show("Время входа истекло")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                toasts.show(if (e is IllegalStateException || e is McpProtocolException) e.message ?: "Не удалось подключить" else "Не удалось подключить через браузер")
            } finally {
                _checking.update { it - id }; logins.remove(id)
            }
        }
    }

    private suspend fun setStatus(id: String, status: String) { repo.get(id)?.let { repo.upsert(it.copy(status = status)) } }

    /** "Использовать в чатах": off always works; on only re-enables a verified connection. */
    fun toggle(id: String) = viewModelScope.launch {
        val c = repo.get(id) ?: return@launch
        if (c.status == ConStatus.ON) {
            mcp.setEnabled(id, false); setStatus(id, ConStatus.OFF); return@launch
        }
        val cfg = mcp.config(id)
        if (cfg == null || cfg.lastError != null || cfg.lastCheckedAt == 0L || (id == "gm" && cfg.authMode != McpAuthMode.GOOGLE)) { toasts.show("Сначала настройте подключение"); return@launch }
        mcp.setEnabled(id, true); setStatus(id, ConStatus.ON)
    }

    fun hasConfig(id: String): Boolean = configs.value[id] != null

    /** Probes [config] (or the saved one) and only then marks the connector ON; a failure becomes WARN. */
    fun connect(id: String, config: McpConnectionConfig? = null, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val c = repo.get(id) ?: return@launch onDone(false)
        val cfg = config ?: mcp.config(id)
        if (cfg == null) { toasts.show("Укажите адрес MCP-сервера"); onDone(false); return@launch }
        _checking.update { it + id }
        val r = try { mcp.probeAndSave(id, cfg) } finally { _checking.update { it - id } }
        r.onSuccess { p ->
            setStatus(id, ConStatus.ON)
            toasts.show("${c.name} подключён · ${p.tools.size} ${toolsWord(p.tools.size)}")
        }.onFailure { e ->
            setStatus(id, ConStatus.WARN)
            toasts.show("${c.name}: ${e.message ?: "ошибка подключения"}")
        }
        onDone(r.isSuccess)
    }

    fun disconnect(id: String) = viewModelScope.launch {
        mcp.remove(id)
        setStatus(id, ConStatus.NEW)
    }

    /** Custom MCP server by URL: probed first, added only when it answers. */
    fun addByUrl(url: String, config: McpConnectionConfig? = null, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val u = url.trim()
        val host = runCatching { URI(u).takeIf { it.scheme == "http" || it.scheme == "https" }?.host }.getOrNull()
        if (u.isEmpty() || host.isNullOrEmpty()) { toasts.show("Введите адрес сервера, например https://mcp.example.com/mcp"); onDone(false); return@launch }
        val id = "c" + System.nanoTime()
        val cfg = (config ?: McpConnectionConfig(id, u)).copy(connectorId = id, endpoint = u)
        val r = mcp.probeAndSave(id, cfg)
        r.onSuccess { p ->
            val minSort = repo.connectors.first().minOfOrNull { it.sortKey } ?: 0L
            repo.upsert(ConnectorEntity(id, host, "Свой сервер", u, "mu", ConStatus.ON, minSort - 1))
            toasts.show("$host подключён · ${p.tools.size} ${toolsWord(p.tools.size)}")
        }.onFailure { e ->
            mcp.remove(id)
            toasts.show("Не удалось подключиться: ${e.message ?: "ошибка"}")
        }
        onDone(r.isSuccess)
    }

    companion object {
        fun toolsWord(n: Int): String {
            val m10 = n % 10; val m100 = n % 100
            return when {
                m10 == 1 && m100 != 11 -> "инструмент"
                m10 in 2..4 && m100 !in 12..14 -> "инструмента"
                else -> "инструментов"
            }
        }
    }
}
