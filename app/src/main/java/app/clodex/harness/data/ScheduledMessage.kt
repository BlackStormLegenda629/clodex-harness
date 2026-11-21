package app.clodex.harness.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

enum class ScheduleRepeat(val label: String, val interval: Long) {
    Once("Один раз", 0), Hourly("Каждый час", 3_600_000), Daily("Каждые 24 часа", 86_400_000);

    fun nextAfter(due: Long, now: Long): Long? = if (interval == 0L) null
        else due + ((now - due).coerceAtLeast(0) / interval + 1) * interval
}

@Entity(tableName = "scheduled_messages")
@Serializable
data class ScheduledMessage(
    @PrimaryKey val id: String, val chatId: Long, val projectId: Long?, val providerId: String,
    val endpoint: String, val openAi: Boolean, val model: String, val text: String, val repeat: String,
    val due: Long, val state: String = "waiting", val lastResult: String = "",
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY due, id") fun observe(): Flow<List<ScheduledMessage>>
    @Query("SELECT * FROM scheduled_messages WHERE id = :id") suspend fun get(id: String): ScheduledMessage?
    @Insert suspend fun insert(row: ScheduledMessage)
    @Query("UPDATE scheduled_messages SET state = 'running' WHERE id = :id AND due = :due AND state = 'waiting'")
    suspend fun claim(id: String, due: Long): Int
    @Query("UPDATE scheduled_messages SET state = :state, due = :nextDue, lastResult = :result WHERE id = :id AND due = :due AND state = 'running'")
    suspend fun finish(id: String, due: Long, state: String, nextDue: Long, result: String): Int
    @Query("UPDATE scheduled_messages SET state = 'failed', lastResult = :result WHERE id = :id AND due = :due AND state = 'waiting'")
    suspend fun failWaiting(id: String, due: Long, result: String)
    @Query("SELECT EXISTS(SELECT 1 FROM scheduled_messages WHERE projectId = :projectId AND state = 'running')")
    suspend fun isProjectRunning(projectId: Long): Boolean
    @Query("UPDATE scheduled_messages SET state = 'cancelled' WHERE id = :id AND state IN ('waiting', 'running')") suspend fun cancel(id: String): Int
}
