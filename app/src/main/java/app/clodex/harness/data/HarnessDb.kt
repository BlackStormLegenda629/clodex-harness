package app.clodex.harness.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

val MIGRATION_2_3_SQL = listOf(
    "ALTER TABLE messages ADD COLUMN thinkingSeconds INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE messages ADD COLUMN thinkingSteps TEXT NOT NULL DEFAULT ''",
)

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_2_3_SQL.forEach(db::execSQL) }
}

/** Adds real Code session state; existing sessions stay readable, no synthetic events are backfilled. */
val MIGRATION_3_4_SQL = listOf(
    "ALTER TABLE sessions ADD COLUMN workspaceId INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE sessions ADD COLUMN mode TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE sessions ADD COLUMN baseBranch TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE sessions ADD COLUMN commitSha TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE sessions ADD COLUMN prUrl TEXT NOT NULL DEFAULT ''",
    "CREATE TABLE IF NOT EXISTS `session_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `detail` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_session_events_sessionId` ON `session_events` (`sessionId`)",
    "CREATE TABLE IF NOT EXISTS `session_changes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `path` TEXT NOT NULL, `beforeText` TEXT, `afterText` TEXT NOT NULL, `status` TEXT NOT NULL, `added` INTEGER NOT NULL, `removed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_session_changes_sessionId` ON `session_changes` (`sessionId`)",
)

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_3_4_SQL.forEach(db::execSQL) }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `memory_notes` (`scope` INTEGER NOT NULL, `text` TEXT NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_notes_scope` ON `memory_notes` (`scope`)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `scheduled_messages` (`id` TEXT NOT NULL, `chatId` INTEGER NOT NULL, `projectId` INTEGER, `providerId` TEXT NOT NULL, `endpoint` TEXT NOT NULL, `openAi` INTEGER NOT NULL, `model` TEXT NOT NULL, `text` TEXT NOT NULL, `repeat` TEXT NOT NULL, `due` INTEGER NOT NULL, `state` TEXT NOT NULL, `lastResult` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY id DESC") fun observeAll(): Flow<List<ChatEntity>>
    @Query("SELECT * FROM chats WHERE id = :id") suspend fun get(id: Long): ChatEntity?
    @Query("SELECT * FROM chats ORDER BY id DESC LIMIT 1") suspend fun getNewest(): ChatEntity?
    @Query("SELECT * FROM chats WHERE projectId = :pid ORDER BY id DESC") fun observeByProject(pid: Long): Flow<List<ChatEntity>>
    @Insert suspend fun insert(c: ChatEntity): Long
    @Query("UPDATE chats SET projectId = :projectId WHERE id = :id AND (:projectId IS NULL OR EXISTS(SELECT 1 FROM projects WHERE id = :projectId)) AND NOT EXISTS(SELECT 1 FROM scheduled_messages WHERE chatId = :id AND state = 'running')")
    suspend fun assignProject(id: Long, projectId: Long?): Int
    @Query("UPDATE chats SET updatedAt = :now, title = CASE WHEN title = 'Новый чат' AND :firstTitle IS NOT NULL THEN :firstTitle ELSE title END WHERE id = :id")
    suspend fun touch(id: Long, now: Long, firstTitle: String? = null)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id") fun observe(chatId: Long): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id") suspend fun list(chatId: Long): List<MessageEntity>
    @Insert suspend fun insert(m: MessageEntity): Long
    @Query("DELETE FROM messages WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY pinned DESC, id DESC") fun observeAll(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE id = :id") fun observe(id: Long): Flow<ProjectEntity?>
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun get(id: Long): ProjectEntity?
    @Insert suspend fun insert(p: ProjectEntity): Long
    @Update suspend fun update(p: ProjectEntity)
    @Query("DELETE FROM projects WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE chats SET projectId = NULL WHERE projectId = :id") suspend fun detachChats(id: Long)
    @Query("UPDATE sessions SET projectId = NULL WHERE projectId = :id") suspend fun detachSessions(id: Long)
}

@Dao
interface ArtifactDao {
    @Query("SELECT * FROM artifacts ORDER BY id DESC") fun observeAll(): Flow<List<ArtifactEntity>>
    @Query("SELECT * FROM artifacts WHERE id = :id") fun observe(id: Long): Flow<ArtifactEntity?>
    @Insert suspend fun insert(a: ArtifactEntity): Long
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY id DESC") fun observeAll(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE id = :id") fun observe(id: Long): Flow<SessionEntity?>
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun get(id: Long): SessionEntity?
    @Query("SELECT * FROM sessions WHERE projectId = :pid ORDER BY id DESC") fun observeByProject(pid: Long): Flow<List<SessionEntity>>
    @Insert suspend fun insert(s: SessionEntity): Long
    @Update suspend fun update(s: SessionEntity)
    @Query("SELECT * FROM session_messages WHERE sessionId = :id ORDER BY id") fun messages(id: Long): Flow<List<SessionMessageEntity>>
    @Insert suspend fun insertMessage(m: SessionMessageEntity): Long
    @Query("SELECT * FROM session_messages WHERE sessionId = :id ORDER BY id") suspend fun messageList(id: Long): List<SessionMessageEntity>
    @Query("SELECT * FROM session_events WHERE sessionId = :id ORDER BY id") fun events(id: Long): Flow<List<SessionEventEntity>>
    @Insert suspend fun insertEvent(e: SessionEventEntity): Long
    @Query("SELECT * FROM session_changes WHERE sessionId = :id ORDER BY id") fun changes(id: Long): Flow<List<SessionChangeEntity>>
    @Query("SELECT * FROM session_changes WHERE sessionId = :id ORDER BY id") suspend fun changeList(id: Long): List<SessionChangeEntity>
    @Insert suspend fun insertChange(c: SessionChangeEntity): Long
    @Query("UPDATE session_changes SET status = :to WHERE sessionId = :id AND status = :from") suspend fun moveChanges(id: Long, from: String, to: String)
    @Query("UPDATE session_changes SET status = :to WHERE id IN (:ids)") suspend fun setChangeStatus(ids: List<Long>, to: String)
}

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY id") fun observeAll(): Flow<List<WorkspaceEntity>>
    @Query("SELECT * FROM workspaces WHERE id = :id") suspend fun get(id: Long): WorkspaceEntity?
    @Insert suspend fun insert(w: WorkspaceEntity): Long
    @Query("DELETE FROM workspaces WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY sortKey, id") fun observeAll(): Flow<List<SkillEntity>>
    @Query("SELECT * FROM skills WHERE id = :id") suspend fun get(id: String): SkillEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: SkillEntity)
    @Query("DELETE FROM skills WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface ConnectorDao {
    @Query("SELECT * FROM connectors ORDER BY sortKey, id") fun observeAll(): Flow<List<ConnectorEntity>>
    @Query("SELECT * FROM connectors WHERE id = :id") suspend fun get(id: String): ConnectorEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(c: ConnectorEntity)
}

@Database(
    entities = [
        ChatEntity::class, MessageEntity::class, ProjectEntity::class, ArtifactEntity::class,
        SessionEntity::class, SessionMessageEntity::class, WorkspaceEntity::class,
        SkillEntity::class, ConnectorEntity::class, SessionEventEntity::class, SessionChangeEntity::class, MemoryNote::class, ScheduledMessage::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class HarnessDb : RoomDatabase() {
    abstract fun chats(): ChatDao
    abstract fun messages(): MessageDao
    abstract fun projects(): ProjectDao
    abstract fun artifacts(): ArtifactDao
    abstract fun sessions(): SessionDao
    abstract fun workspaces(): WorkspaceDao
    abstract fun skills(): SkillDao
    abstract fun connectors(): ConnectorDao
    abstract fun memory(): MemoryDao
    abstract fun schedules(): ScheduleDao

    companion object {
        /** v1 held demo data and is still dropped; from v2 on chats are migrated, never deleted. */
        fun create(ctx: Context): HarnessDb =
            Room.databaseBuilder(ctx, HarnessDb::class.java, "harness.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigrationFrom(1)
                .build()
    }
}
