package app.clodex.harness.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

internal const val MEMORY_NOTE_LIMIT = 1000

@Entity(tableName = "memory_notes", indices = [Index("scope")])
data class MemoryNote(val scope: Long, val text: String, @PrimaryKey(autoGenerate = true) val id: Long = 0)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_notes WHERE scope = :scope ORDER BY id DESC")
    fun observe(scope: Long): Flow<List<MemoryNote>>

    @Query("SELECT * FROM memory_notes WHERE scope = :global OR scope = :project ORDER BY scope DESC, id DESC LIMIT 100")
    suspend fun contextNotes(global: Long, project: Long): List<MemoryNote>

    @Upsert suspend fun save(note: MemoryNote)
    @Delete suspend fun delete(note: MemoryNote)
    @Query("DELETE FROM memory_notes WHERE scope = :scope") suspend fun deleteScope(scope: Long)
}

internal fun memoryContext(notes: List<MemoryNote>, global: Boolean, project: Long?): String = buildString {
    for (note in notes) {
        if (!(note.scope == 0L && global || note.scope > 0L && note.scope == project)) continue
        val text = note.text.trim().take(MEMORY_NOTE_LIMIT)
        if (text.isEmpty()) continue
        val label = if (note.scope == 0L) "Общая заметка" else "Заметка проекта"
        val remaining = 4000 - length - label.length - 4
        if (remaining <= 0) break
        append("\n\n").append(label).append(": ").append(text.take(remaining))
    }
}

internal suspend fun HarnessDb.memoryFor(global: Boolean, projectId: Long?): String {
    val project = projectId?.takeIf { projects().get(it)?.useMemory == true }
    if (!global && project == null) return ""
    return memoryContext(memory().contextNotes(if (global) 0 else -1, project ?: -1), global, project)
}
