package app.clodex.harness.data.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import app.clodex.harness.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.ByteArrayOutputStream

class BackupRepository(
    private val db: HarnessDb,
    private val settings: SettingsRepository,
    private val resolver: ContentResolver,
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val preferences = settings.backupPreferences()
        val payload = db.withTransaction {
            val chats = db.chats().observeAll().first()
            val sessions = db.sessions().observeAll().first()
            BackupPayload(
                exportedAt = System.currentTimeMillis(), chats = chats,
                messages = chats.flatMap { db.messages().list(it.id) },
                projects = db.projects().observeAll().first(), artifacts = db.artifacts().observeAll().first(),
                sessions = sessions, sessionMessages = sessions.flatMap { db.sessions().messageList(it.id) },
                sessionEvents = sessions.flatMap { db.sessions().events(it.id).first() },
                sessionChanges = sessions.flatMap { db.sessions().changeList(it.id) },
                workspaces = db.workspaces().observeAll().first(), skills = db.skills().observeAll().first(),
                connectors = db.connectors().observeAll().first(), memory = db.memory().listAll(),
                scheduled = db.schedules().observe().first(), settings = preferences,
            )
        }
        val bytes = BackupCodec.encode(payload).encodeToByteArray()
        require(bytes.size <= MAX_BYTES) { "Копия превышает 32 МБ" }
        checkNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
    }

    /** Returns false if the content was imported but preferences could not be saved. */
    suspend fun importFrom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = checkNotNull(resolver.openInputStream(uri)).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(out.size() + n <= MAX_BYTES) { "Копия превышает 32 МБ" }
                out.write(buffer, 0, n)
            }
            out.toByteArray().decodeToString(throwOnInvalidSequence = true)
        }
        val p = BackupCodec.decode(text).getOrThrow()
        db.withTransaction {
            val projects = p.projects.associate { it.id to db.projects().insert(it.copy(id = 0, files = "")) }
            val artifacts = p.artifacts.associate { it.id to db.artifacts().insert(it.copy(id = 0)) }
            val chats = p.chats.associate { it.id to db.chats().insert(it.copy(id = 0, projectId = it.projectId?.let(projects::get))) }
            for (m in p.messages) db.messages().insert(m.copy(id = 0, chatId = chats.getValue(m.chatId), artifactId = m.artifactId?.let(artifacts::get), attachments = ""))
            val sessions = p.sessions.associate {
                it.id to db.sessions().insert(it.copy(id = 0, workspaceId = -1L,
                    projectId = it.projectId?.let(projects::get), status = SesStatus.DONE, perm = Perm.DENIED))
            }
            for (m in p.sessionMessages) db.sessions().insertMessage(m.copy(id = 0, sessionId = sessions.getValue(m.sessionId)))
            for (e in p.sessionEvents) db.sessions().insertEvent(e.copy(id = 0, sessionId = sessions.getValue(e.sessionId)))
            for (c in p.sessionChanges) db.sessions().insertChange(c.copy(id = 0, sessionId = sessions.getValue(c.sessionId),
                status = if (c.status == ChangeStatus.PENDING) ChangeStatus.DENIED else c.status))
            for (n in p.memory) db.memory().save(n.copy(id = 0, scope = if (n.scope == 0L) 0 else projects.getValue(n.scope)))
            for (s in p.skills) if (s.source == Src.MINE && db.skills().get(s.id) == null) {
                db.skills().upsert(s.copy(enabled = false))
            }
            for (row in p.scheduled) {
                val chat = chats[row.chatId] ?: continue
                db.schedules().insert(row.copy(id = java.util.UUID.randomUUID().toString(), chatId = chat,
                    projectId = row.projectId?.let(projects::get), state = "cancelled"))
            }
        }
        try {
            settings.restoreBackupPreferences(p.settings)
            true
        } catch (_: IOException) {
            false
        }
    }

    private companion object { const val MAX_BYTES = 32 * 1024 * 1024 }
}
