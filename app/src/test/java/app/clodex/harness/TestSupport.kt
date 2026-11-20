package app.clodex.harness

import app.clodex.harness.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** viewModelScope uses Dispatchers.Main; Unconfined makes launches run eagerly, so tests stay synchronous. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}

class FakeSkillRepo(initial: List<SkillEntity> = emptyList()) : SkillRepository {
    val state = MutableStateFlow(initial)
    override val skills = state
    override suspend fun get(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun upsert(s: SkillEntity) { state.value = state.value.filterNot { it.id == s.id } + s }
    override suspend fun delete(id: String) { state.value = state.value.filterNot { it.id == id } }
}

class FakeConnectorRepo(initial: List<ConnectorEntity> = emptyList()) : ConnectorRepository {
    val state = MutableStateFlow(initial)
    override val connectors = state
    override suspend fun get(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun upsert(c: ConnectorEntity) { state.value = state.value.filterNot { it.id == c.id } + c }
}

class FakeSessionRepo(initial: List<SessionEntity> = emptyList()) : SessionRepository {
    private val rows = MutableStateFlow(initial)
    private val msgs = MutableStateFlow<List<SessionMessageEntity>>(emptyList())
    private val spaces = MutableStateFlow<List<WorkspaceEntity>>(emptyList())
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1
    override val sessions = rows
    override val workspaces = spaces
    override fun session(id: Long) = rows.map { l -> l.firstOrNull { it.id == id } }
    override fun messages(id: Long) = msgs.map { l -> l.filter { it.sessionId == id } }
    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun insert(s: SessionEntity): Long { val id = nextId++; rows.value = listOf(s.copy(id = id)) + rows.value; return id }
    override suspend fun update(s: SessionEntity) { rows.value = rows.value.map { if (it.id == s.id) s else it } }
    override suspend fun addMessage(m: SessionMessageEntity) { msgs.value = msgs.value + m.copy(id = msgs.value.size + 1L) }
    override suspend fun addWorkspace(w: WorkspaceEntity): Long { val id = spaces.value.size + 1L; spaces.value = spaces.value + w.copy(id = id); return id }
    override suspend fun deleteWorkspace(id: Long) { spaces.value = spaces.value.filterNot { it.id == id } }

    val eventRows = MutableStateFlow<List<SessionEventEntity>>(emptyList())
    val changeRows = MutableStateFlow<List<SessionChangeEntity>>(emptyList())
    override suspend fun messageList(id: Long) = msgs.value.filter { it.sessionId == id }
    override fun events(id: Long) = eventRows.map { l -> l.filter { it.sessionId == id } }
    override fun changes(id: Long) = changeRows.map { l -> l.filter { it.sessionId == id } }
    override suspend fun changeList(id: Long) = changeRows.value.filter { it.sessionId == id }
    override suspend fun addEvent(e: SessionEventEntity) { eventRows.value = eventRows.value + SessionEventCodec.bound(e).copy(id = eventRows.value.size + 1L) }
    override suspend fun addChange(c: SessionChangeEntity) { changeRows.value = changeRows.value + c.copy(id = changeRows.value.size + 1L) }
    override suspend fun moveChanges(id: Long, from: String, to: String) {
        changeRows.value = changeRows.value.map { if (it.sessionId == id && it.status == from) it.copy(status = to) else it }
    }
    override suspend fun setChangeStatus(ids: List<Long>, to: String) { changeRows.value = changeRows.value.map { if (it.id in ids) it.copy(status = to) else it } }
}

class FakePrefs : CodePrefs {
    private val m = MutableStateFlow("Правки")
    private val w = MutableStateFlow("Все")
    override val mode = m
    override val workspace = w
    override suspend fun setMode(v: String) { m.value = v }
    override suspend fun setWorkspace(v: String) { w.value = v }
}

class FakeFetcher(var result: Result<RemoteSkill>) : SkillFetcher {
    var calls = 0
    var lastArgs: Triple<String, String, String>? = null
    override suspend fun fetch(repo: String, path: String, ref: String): Result<RemoteSkill> {
        calls++; lastArgs = Triple(repo, path, ref); return result
    }
}
