package app.clodex.harness.data

import kotlinx.coroutines.flow.Flow

interface SkillRepository {
    val skills: Flow<List<SkillEntity>>
    suspend fun get(id: String): SkillEntity?
    suspend fun upsert(s: SkillEntity)
    suspend fun delete(id: String)
}

interface ConnectorRepository {
    val connectors: Flow<List<ConnectorEntity>>
    suspend fun get(id: String): ConnectorEntity?
    suspend fun upsert(c: ConnectorEntity)
}

interface SessionRepository {
    val sessions: Flow<List<SessionEntity>>
    val workspaces: Flow<List<WorkspaceEntity>>
    fun session(id: Long): Flow<SessionEntity?>
    fun messages(id: Long): Flow<List<SessionMessageEntity>>
    suspend fun get(id: Long): SessionEntity?
    suspend fun insert(s: SessionEntity): Long
    suspend fun update(s: SessionEntity)
    suspend fun addMessage(m: SessionMessageEntity)
    suspend fun messageList(id: Long): List<SessionMessageEntity>
    fun events(id: Long): Flow<List<SessionEventEntity>>
    fun changes(id: Long): Flow<List<SessionChangeEntity>>
    suspend fun changeList(id: Long): List<SessionChangeEntity>
    suspend fun addEvent(e: SessionEventEntity)
    suspend fun addChange(c: SessionChangeEntity)
    /** Moves every change of a session in status [from] to [to]. */
    suspend fun moveChanges(id: Long, from: String, to: String)
    suspend fun setChangeStatus(ids: List<Long>, to: String)
    suspend fun addWorkspace(w: WorkspaceEntity): Long
    suspend fun deleteWorkspace(id: Long) {}
}

class RoomSkillRepository(private val dao: SkillDao) : SkillRepository {
    override val skills = dao.observeAll()
    override suspend fun get(id: String) = dao.get(id)
    override suspend fun upsert(s: SkillEntity) = dao.upsert(s)
    override suspend fun delete(id: String) = dao.delete(id)
}

class RoomConnectorRepository(private val dao: ConnectorDao) : ConnectorRepository {
    override val connectors = dao.observeAll()
    override suspend fun get(id: String) = dao.get(id)
    override suspend fun upsert(c: ConnectorEntity) = dao.upsert(c)
}

class RoomSessionRepository(private val sessionsDao: SessionDao, private val wsDao: WorkspaceDao) : SessionRepository {
    override val sessions = sessionsDao.observeAll()
    override val workspaces = wsDao.observeAll()
    override fun session(id: Long) = sessionsDao.observe(id)
    override fun messages(id: Long) = sessionsDao.messages(id)
    override suspend fun get(id: Long) = sessionsDao.get(id)
    override suspend fun insert(s: SessionEntity) = sessionsDao.insert(s)
    override suspend fun update(s: SessionEntity) = sessionsDao.update(s)
    override suspend fun addMessage(m: SessionMessageEntity) { sessionsDao.insertMessage(m) }
    override suspend fun messageList(id: Long) = sessionsDao.messageList(id)
    override fun events(id: Long) = sessionsDao.events(id)
    override fun changes(id: Long) = sessionsDao.changes(id)
    override suspend fun changeList(id: Long) = sessionsDao.changeList(id)
    override suspend fun addEvent(e: SessionEventEntity) { sessionsDao.insertEvent(SessionEventCodec.bound(e)) }
    override suspend fun addChange(c: SessionChangeEntity) { sessionsDao.insertChange(c) }
    override suspend fun moveChanges(id: Long, from: String, to: String) = sessionsDao.moveChanges(id, from, to)
    override suspend fun setChangeStatus(ids: List<Long>, to: String) { if (ids.isNotEmpty()) sessionsDao.setChangeStatus(ids, to) }
    override suspend fun addWorkspace(w: WorkspaceEntity) = wsDao.insert(w)
    override suspend fun deleteWorkspace(id: Long) = wsDao.delete(id)
}

/** Keeps persisted event rows bounded and their enums valid. */
object SessionEventCodec {
    const val MAX_TITLE = 200
    const val MAX_DETAIL = 2000
    private val kinds = setOf(EventKind.STEP, EventKind.TOOL, EventKind.APPLY, EventKind.PR, EventKind.ERROR)
    private val statuses = setOf(EventStatus.OK, EventStatus.ERROR, EventStatus.DENIED)

    fun bound(e: SessionEventEntity): SessionEventEntity = e.copy(
        kind = e.kind.takeIf { it in kinds } ?: EventKind.STEP,
        title = e.title.replace(Regex("\\s+"), " ").trim().take(MAX_TITLE).ifEmpty { "Шаг" },
        detail = e.detail.trim().take(MAX_DETAIL),
        status = e.status.takeIf { it in statuses } ?: EventStatus.OK,
    )
}
