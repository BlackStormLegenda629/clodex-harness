package app.clodex.harness.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Src { const val BUILTIN = "Встроенный"; const val MINE = "Мой"; const val GITHUB = "GitHub"; const val CAVEMAN = "Caveman" }
object ConStatus { const val ON = "on"; const val OFF = "off"; const val WARN = "warn"; const val NEW = "new" }
object SesStatus { const val WAIT = "wait"; const val RUN = "run"; const val DONE = "done" }
object Perm { const val PENDING = 0; const val ALLOWED = 1; const val DENIED = 2 }
object Role { const val USER = "user"; const val ASSISTANT = "assistant"; const val ERROR = "error" }

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val updatedAt: Long,
    val projectId: Long? = null,
)

@Entity(tableName = "messages", indices = [Index("chatId")])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val role: String,
    val text: String,
    val artifactId: Long? = null,
    /** attachment file names, '\n'-separated */
    val attachments: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Assistant replies: how long the agent worked and its factual steps ([ThinkingCodec]). */
    val thinkingSeconds: Int = 0,
    val thinkingSteps: String = "",
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val updatedAt: Long,
    /** file names, '\n'-separated */
    val files: String = "",
    val instructions: String = "",
    val useMemory: Boolean = true,
    val pinned: Boolean = false,
    val archived: Boolean = false,
)

fun ProjectEntity.fileList(): List<String> = files.split('\n').filter { it.isNotBlank() }

@Entity(tableName = "artifacts")
data class ArtifactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "HTML" | "Код" | "Документ" */
    val kind: String,
    val source: String,
    val createdAt: Long,
    val content: String,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val repo: String,
    val branch: String,
    val status: String,
    val summary: String,
    val diff: String,
    val startedAt: Long,
    val projectId: Long? = null,
    val perm: Int = Perm.PENDING,
    val prNumber: Int = 0,
    val reverted: Boolean = false,
    /** WorkspaceEntity.id the session works in (0: legacy session without a workspace). */
    val workspaceId: Long = 0,
    /** CodeMode label: «План» / «Правки» / «Авто». */
    val mode: String = "",
    /** GitHub: branch the work is based on; [branch] is the head branch commits go to. */
    val baseBranch: String = "",
    /** Set only from a successful GitHub commit / pull request response. */
    val commitSha: String = "",
    val prUrl: String = "",
)

object EventKind { const val STEP = "step"; const val TOOL = "tool"; const val APPLY = "apply"; const val PR = "pr"; const val ERROR = "error" }
object EventStatus { const val OK = "ok"; const val ERROR = "error"; const val DENIED = "denied" }
object ChangeStatus { const val PENDING = "pending"; const val APPLIED = "applied"; const val DENIED = "denied"; const val FAILED = "failed"; const val SUPERSEDED = "superseded" }

/** A completed, factual step of a Code session (tool call, apply, PR, error). */
@Entity(tableName = "session_events", indices = [Index("sessionId")])
data class SessionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val kind: String,
    val title: String,
    val detail: String = "",
    val status: String = EventStatus.OK,
    val createdAt: Long,
)

/** A file change produced by the agent; [beforeText] is null for a new file. */
@Entity(tableName = "session_changes", indices = [Index("sessionId")])
data class SessionChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val path: String,
    val beforeText: String?,
    val afterText: String,
    val status: String = ChangeStatus.PENDING,
    val added: Int = 0,
    val removed: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "session_messages", indices = [Index("sessionId")])
data class SessionMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "gh" | "dev" */
    val kind: String,
    val path: String,
)

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val source: String,
    val enabled: Boolean,
    /** HIcon enum name */
    val icon: String,
    val body: String = "",
    val repo: String? = null,
    val path: String? = null,
    val ref: String? = null,
    val sortKey: Long = 0,
)

@Entity(tableName = "connectors")
data class ConnectorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val developer: String,
    val description: String,
    /** "#RRGGBB", or "ink" / "mu" for theme-dependent colors */
    val color: String,
    val status: String,
    val sortKey: Long = 0,
)
