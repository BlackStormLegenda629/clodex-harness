package app.clodex.harness

import android.content.Context
import androidx.work.*
import androidx.room.withTransaction
import app.clodex.harness.data.*
import app.clodex.harness.data.agent.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal fun scheduleWork(context: Context, id: String, due: Long): Operation =
    WorkManager.getInstance(context).enqueueUniqueWork("schedule:$id:$due", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<ScheduleWorker>()
            .setInputData(workDataOf("id" to id, "due" to due)).addTag("schedule:$id").addTag("scheduled-messages")
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay((due - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build())

class ScheduleWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val due = inputData.getLong("due", 0)
        val c = (applicationContext as ClodexApp).container
        val db = c.db
        val dao = db.schedules()
        if (dao.get(id)?.let { it.state == "waiting" && it.due == due && System.currentTimeMillis() < due } == true) return Result.retry()

        val ran = c.chatVm.withScheduledReply {
            val row = dao.get(id) ?: return@withScheduledReply
            if (row.due != due) return@withScheduledReply
            if (row.state == "running") {
                dao.finish(id, due, "failed", due, "Отправка прервалась. Автоповтора не будет.")
                return@withScheduledReply
            }
            if (row.state != "waiting") return@withScheduledReply
            val settings = c.settings.flow.first()
            val provider = settings.provider(row.providerId)
            if (provider == null || !provider.ready || provider.url != row.endpoint || provider.openAi != row.openAi) {
                dao.failWaiting(id, due, "Подключение изменилось. Создайте новое расписание.")
                return@withScheduledReply
            }
            try {
                val request = db.withTransaction {
                    val chat = db.chats().get(row.chatId)
                    val project = row.projectId?.let { db.projects().get(it) }
                    if (chat == null || chat.projectId != row.projectId || (row.projectId != null && project == null)) {
                        dao.failWaiting(id, due, "Чат или проект изменился. Создайте новое расписание.")
                        return@withTransaction null
                    }
                    if (dao.claim(id, due) != 1) return@withTransaction null
                    val history = db.messages().list(chat.id).filter { it.role != Role.ERROR }.map {
                        if (it.role == Role.USER) AgentMessage.User(it.text) else AgentMessage.Assistant(it.text)
                    } + AgentMessage.User(row.text)
                    val instructions = listOfNotNull(settings.instructions, project?.instructions)
                        .filter { it.isNotBlank() }.joinToString("\n\n") + db.memoryFor(settings.on("p2"), row.projectId)
                    ProviderRequest(row.model, maxTokens(settings.effort), instructions.ifBlank { null }, history, emptyList(), false)
                } ?: return@withScheduledReply
                if (dao.get(id)?.state != "running") return@withScheduledReply
                var finished = false
                val answer = buildString {
                    c.scheduledProvider.streamPinned(provider, request).collect { event ->
                        when (event) {
                            is ProviderEvent.TextDelta -> {
                                check(event.text.length <= 500_000 - length)
                                append(event.text)
                            }
                            is ProviderEvent.Completed -> finished = !event.stopReason.isNullOrBlank()
                            else -> Unit
                        }
                    }
                }
                check(finished && answer.isNotBlank())
                val next = ScheduleRepeat.valueOf(row.repeat).nextAfter(due, System.currentTimeMillis())
                db.withTransaction {
                    if (dao.finish(id, due, if (next == null) "done" else "waiting", next ?: due, "Ответ получен") == 1) {
                        db.messages().insert(MessageEntity(chatId = row.chatId, role = Role.USER, text = row.text))
                        db.messages().insert(MessageEntity(chatId = row.chatId, role = Role.ASSISTANT, text = answer))
                        db.chats().touch(row.chatId, System.currentTimeMillis(), row.text.take(40))
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { dao.finish(id, due, "failed", due, "Отправка прервалась. Автоповтора не будет.") }
                throw e
            } catch (_: Exception) {
                if (dao.finish(id, due, "failed", due, "Ответ не получен. Автоповтора не будет.") == 0) {
                    dao.failWaiting(id, due, "Не удалось подготовить сообщение")
                }
            }
        }
        if (!ran) return Result.retry()
        val next = dao.get(id)
        if (next?.state == "waiting" && next.due != due) {
            try { scheduleWork(applicationContext, id, next.due).await() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                dao.failWaiting(id, next.due, "Ответ получен, но не удалось запланировать следующий запуск")
            }
        }
        return Result.success()
    }
}
