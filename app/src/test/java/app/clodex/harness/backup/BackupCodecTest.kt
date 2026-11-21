package app.clodex.harness.backup

import app.clodex.harness.data.*
import app.clodex.harness.data.backup.BackupCodec
import app.clodex.harness.data.backup.BackupPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val payload = BackupPayload(
        exportedAt = 42,
        chats = listOf(ChatEntity(1, "Чат", 1, 1)),
        messages = listOf(MessageEntity(1, 1, Role.ASSISTANT, "Привет", artifactId = 1)),
        projects = listOf(ProjectEntity(1, "Проект", updatedAt = 1)),
        artifacts = listOf(ArtifactEntity(1, "Документ", "HTML", "Чат", 1, "<p>Привет</p>")),
        sessions = listOf(SessionEntity(1, "Сессия", "Папка", "", SesStatus.DONE, "", "", 1, 1, workspaceId = 1)),
        sessionMessages = listOf(SessionMessageEntity(1, 1, Role.USER, "Задача")),
        sessionEvents = listOf(SessionEventEntity(1, 1, EventKind.STEP, "Шаг", createdAt = 1)),
        sessionChanges = listOf(SessionChangeEntity(1, 1, "a.txt", null, "текст", createdAt = 1)),
        workspaces = listOf(WorkspaceEntity(1, "Папка", "dev", "content://docs/tree/root")),
        skills = listOf(SkillEntity("custom", "Скилл", "", Src.MINE, true, "Pen")),
        connectors = listOf(ConnectorEntity("custom", "Подключение", "", "", "ink", ConStatus.OFF)),
        memory = listOf(MemoryNote(1, "Заметка", 1)),
        scheduled = listOf(ScheduledMessage("test", 1, 1, "provider", "", false, "model", "Сообщение", "Once", 1)),
        settings = mapOf("theme" to "Тёмная"),
    )

    @Test fun roundTripPreservesEverything() {
        assertEquals(payload, BackupCodec.decode(BackupCodec.encode(payload)).getOrThrow())
    }

    @Test fun wrongSchemaAndDuplicatesRejected() {
        for (invalid in listOf(payload.copy(schema = 2), payload.copy(chats = payload.chats + payload.chats))) {
            assertTrue(BackupCodec.decode(BackupCodec.encode(invalid)).isFailure)
        }
    }

    @Test fun missingSchemaEmptyAndCorruptArchivesAreRejected() {
        for (json in listOf("{", "{}", "{\"exportedAt\":42}", BackupCodec.encode(BackupPayload(exportedAt = 42)))) {
            assertTrue(BackupCodec.decode(json).isFailure)
        }
    }
}
