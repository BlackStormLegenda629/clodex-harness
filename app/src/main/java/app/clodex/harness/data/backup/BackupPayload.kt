package app.clodex.harness.data.backup

import app.clodex.harness.data.*
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    @Required val schema: Int = 1,
    val exportedAt: Long,
    val chats: List<ChatEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val artifacts: List<ArtifactEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val sessionMessages: List<SessionMessageEntity> = emptyList(),
    val sessionEvents: List<SessionEventEntity> = emptyList(),
    val sessionChanges: List<SessionChangeEntity> = emptyList(),
    val workspaces: List<WorkspaceEntity> = emptyList(),
    val skills: List<SkillEntity> = emptyList(),
    val connectors: List<ConnectorEntity> = emptyList(),
    val memory: List<MemoryNote> = emptyList(),
    val scheduled: List<ScheduledMessage> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

object BackupCodec {
    private val json = Json { encodeDefaults = true }
    fun encode(payload: BackupPayload): String = json.encodeToString(payload)
    fun decode(text: String): Result<BackupPayload> = runCatching {
        json.decodeFromString<BackupPayload>(text).also { p ->
            require(p.schema == 1 && p.exportedAt > 0)
            val ids = listOf(p.chats.map { it.id }, p.messages.map { it.id }, p.projects.map { it.id },
                p.artifacts.map { it.id }, p.sessions.map { it.id }, p.sessionMessages.map { it.id },
                p.sessionEvents.map { it.id }, p.sessionChanges.map { it.id }, p.workspaces.map { it.id }, p.memory.map { it.id })
            val keys = listOf(p.skills.map { it.id }, p.connectors.map { it.id }, p.scheduled.map { it.id })
            require(ids.any { it.isNotEmpty() } || keys.any { it.isNotEmpty() } || p.settings.isNotEmpty())
            require(ids.all { it.all { id -> id > 0 } && it.size == it.toSet().size })
            require(keys.all { it.none(String::isBlank) && it.size == it.toSet().size })
        }
    }
}
