package app.clodex.harness.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.clodex.harness.AppContainer
import app.clodex.harness.data.*
import app.clodex.harness.data.agent.AgentMessage
import app.clodex.harness.data.agent.AgentRequest
import app.clodex.harness.data.agent.AgentRunner
import app.clodex.harness.data.agent.InteractiveApprovalGate
import app.clodex.harness.data.agent.PendingToolCall
import app.clodex.harness.data.agent.ToolDecision
import app.clodex.harness.data.agent.mcpAgentTools
import app.clodex.harness.ui.components.ThinkingPresentation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import app.clodex.harness.data.notifications.NotifyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

data class Attachment(val uri: String, val name: String)
data class Draft(val chatId: Long, val text: String)

/** Shown after a quota/balance error: the user chooses whether to continue on another provider. */
data class ProviderOffer(val title: String, val message: String, val providerId: String?, val providerName: String?)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val c: AppContainer,
    private val onNotify: (NotifyEvent, String, String) -> Unit = { _, _, _ -> },
    private val isForeground: () -> Boolean = { true },
) : ViewModel() {
    private val db = c.db

    val chats: StateFlow<List<ChatEntity>> = db.chats().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val currentId: StateFlow<Long> = c.settings.flow.map { it.currentChatId }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, c.initialSettings.currentChatId)

    /** Current chat; falls back to the newest one if the stored id is stale. */
    val current: StateFlow<ChatEntity?> = combine(chats, currentId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val messages: StateFlow<List<MessageEntity>> = current.map { it?.id ?: -1L }.distinctUntilChanged()
        .flatMapLatest { db.messages().observe(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft

    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = _attachments

    private val _offer = MutableStateFlow<ProviderOffer?>(null)
    val offer: StateFlow<ProviderOffer?> = _offer

    val artifacts: StateFlow<List<ArtifactEntity>> = db.artifacts().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val projects: StateFlow<List<ProjectEntity>> = db.projects().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _thinking = MutableStateFlow<ThinkingPresentation?>(null)
    /** The live «Ход рассуждений» of the reply being generated (null when idle). */
    val thinking: StateFlow<ThinkingPresentation?> = _thinking

    private val _generating = MutableStateFlow<Set<Long>>(emptySet())
    val generatingChatIds: StateFlow<Set<Long>> = _generating
    private val _events = MutableSharedFlow<NotifyEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NotifyEvent> = _events

    private val gate = InteractiveApprovalGate()
    /** A non-read-only MCP tool call waiting for «Разрешить» / «Отклонить». */
    val pendingToolApproval: StateFlow<PendingToolCall?> = gate.pending

    private var streamJob: Job? = null
    internal val activeReply = MutableStateFlow(false)

    val schedules: Flow<List<ScheduledMessage>> = db.schedules().observe()

    fun schedule(chat: ChatEntity, target: AppSettings, text: String, due: Long, repeat: ScheduleRepeat) = viewModelScope.launch {
        val provider = target.activeProvider()
        if (!provider.ready || due <= System.currentTimeMillis() || text.isBlank()) {
            c.toasts.show("Укажите время в будущем, текст и настроенного провайдера")
            return@launch
        }
        c.enqueueSchedule(ScheduledMessage(java.util.UUID.randomUUID().toString(), chat.id, chat.projectId,
            provider.id, provider.url, provider.openAi, resolveModel(target, provider, target.model), text.trim(), repeat.name, due))
    }

    fun cancelSchedule(id: String) = viewModelScope.launch { c.cancelSchedule(id) }

    internal suspend fun withScheduledReply(block: suspend () -> Unit): Boolean = withContext(Dispatchers.Main.immediate) {
        if (activeReply.value) return@withContext false
        activeReply.value = true
        try { block(); true } finally { activeReply.value = false }
    }

    fun resolveTool(allow: Boolean, remember: Boolean = false) = gate.resolve(if (!allow) ToolDecision.DENY else if (remember) ToolDecision.ALLOW_CHAT else ToolDecision.ALLOW)

    /** Stops the current reply; partial text is kept. */
    fun stop() { streamJob?.cancel() }
    suspend fun cancelAndJoinReply() { val job = streamJob; job?.cancel(); job?.join() }

    private fun startReply(block: suspend () -> Unit) {
        if (activeReply.value) return
        activeReply.value = true
        c.keepGenerationAlive()
        streamJob = viewModelScope.launch {
            try { block() } finally { activeReply.value = false }
        }
    }

    fun open(chatId: Long) = viewModelScope.launch { c.settings.set(K.currentChat, chatId) }

    /** "Новый чат": always a fresh chat. */
    fun newChat(projectId: Long? = null, onDone: () -> Unit = {}) = viewModelScope.launch {
        val id = db.chats().insert(ChatEntity(title = "Новый чат", updatedAt = System.currentTimeMillis(), projectId = projectId))
        c.settings.set(K.currentChat, id)
        _attachments.value = emptyList()
        _offer.value = null
        onDone()
    }

    fun addAttachment(uri: String, name: String) = _attachments.update { it + Attachment(uri, name) }
    fun removeAttachment(i: Int) = _attachments.update { l -> l.filterIndexed { idx, _ -> idx != i } }

    fun assignProject(projectId: Long?) = viewModelScope.launch {
        val chat = current.value ?: return@launch
        if (db.chats().assignProject(chat.id, projectId) == 0) c.toasts.show("Не удалось перенести чат: проверьте проект или дождитесь отправки по расписанию")
    }

    fun dismissOffer() { _offer.value = null }

    /** Switch to the suggested provider and retry the last user message without duplicating it. */
    fun acceptOffer() {
        val o = _offer.value ?: return
        viewModelScope.launch {
            _offer.value = null
            val target = o.providerId ?: return@launch
            c.settings.set(K.providerId, target)
            c.settings.flow.first().provider(target)?.let { p -> ModelCatalog.preferredModel(p, "")?.let { c.settings.set(K.model, it) } }
            val chat = current.value ?: return@launch
            val msgs = db.messages().list(chat.id)
            msgs.filter { it.role == Role.ERROR }.forEach { db.messages().delete(it.id) }
            val lastUser = msgs.lastOrNull { it.role == Role.USER } ?: return@launch
            startReply { runReply(chat, lastUser.text, emptyList()) }
        }
    }

    /** @return true when the message was accepted (the input field may be cleared). */
    fun send(text: String): Boolean {
        val t = text.trim()
        val chat = current.value ?: return false
        if (t.isEmpty() && _attachments.value.isEmpty()) return false
        if (activeReply.value) { c.toasts.show("Дождитесь ответа"); return false }
        val files = _attachments.value
        _attachments.value = emptyList()
        startReply {
            val body = t.ifEmpty { "Вложения" }
            db.messages().insert(MessageEntity(chatId = chat.id, role = Role.USER, text = body, attachments = files.joinToString("\n") { it.name }))
            db.chats().touch(chat.id, System.currentTimeMillis(), body.take(40))
            db.chats().get(chat.id)?.let { runReply(it, body, files) }
        }
        return true
    }

    private suspend fun runReply(chat: ChatEntity, body: String, files: List<Attachment>) {
        val s = c.settings.flow.first()
        val project = chat.projectId?.let { db.projects().get(it) }
        val memory = db.memoryFor(s.on("p2"), project?.id)
        val system = buildString {
            if (s.instructions.isNotBlank()) append(s.instructions.trim())
            if (project != null && project.instructions.isNotBlank()) append("\n\nИнструкции проекта «${project.name}»:\n").append(project.instructions.trim())
            if (project != null) {
                val ctx = c.files.readProjectTexts(project.id, project.fileList())
                if (ctx.isNotEmpty()) append("\n\nКонтекст проекта:\n").append(ctx)
            }
            append(memory)
            append(skillInstructions(c.skillRepo.skills.first(), Caveman.block(s)))
        }.trim().ifEmpty { null }

        val rows = db.messages().list(chat.id).filter { it.role != Role.ERROR }
        val history = rows.mapIndexed { i, m ->
            when {
                m.role != Role.USER -> AgentMessage.Assistant(m.text)
                i == rows.lastIndex && files.isNotEmpty() -> AgentMessage.User(body + "\n\n[Вложения: " + files.joinToString { it.name } + "]")
                else -> AgentMessage.User(m.text)
            }
        }
        val tools = runCatching { mcpAgentTools(c.mcp) { id -> c.connectorRepo.get(id)?.name ?: id } }.getOrDefault(emptyList())
        val request = AgentRequest(s.model, maxTokens(s.effort), system, history, s.on("web"))

        val progress = ReplyProgress(chat.id, System.currentTimeMillis())
        _generating.update { it + chat.id }
        try {
            val runner = AgentRunner(c.provider, app.clodex.harness.data.agent.RememberingApprovalGate(chat.id, c.mcp, gate))
            val out = progress.consume(runner.run(request, tools), System::currentTimeMillis) { p ->
                _draft.value = Draft(chat.id, p.answer)
                _thinking.value = p.live()
            }
            db.messages().insert(
                MessageEntity(
                    chatId = chat.id, role = Role.ASSISTANT, text = out.text,
                    thinkingSeconds = out.seconds, thinkingSteps = ThinkingCodec.encode(out.steps),
                ),
            )
            db.chats().touch(chat.id, System.currentTimeMillis())
            _offer.value = null
            if (progress.seconds(System.currentTimeMillis()) >= 10 && !isForeground()) {
                _events.tryEmit(NotifyEvent.REPLY_READY)
                onNotify(NotifyEvent.REPLY_READY, "Ответ готов", chat.title)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { savePartial(chat.id, progress) }
            throw e
        } catch (e: Exception) {
            savePartial(chat.id, progress)
            db.messages().insert(MessageEntity(chatId = chat.id, role = Role.ERROR, text = "Не удалось получить ответ: " + (e.message ?: "ошибка сети")))
            if (e is ProviderFailure && e.quota) offerAlternative(e)
        } finally {
            _draft.value = null
            _thinking.value = null
            _generating.update { it - chat.id }
        }
    }

    private suspend fun savePartial(chatId: Long, p: ReplyProgress) {
        if (p.answer.isNotBlank()) db.messages().insert(
            MessageEntity(
                chatId = chatId, role = Role.ASSISTANT, text = p.answer,
                thinkingSeconds = p.seconds(System.currentTimeMillis()), thinkingSteps = ThinkingCodec.encode(p.steps),
            ),
        )
    }

    private suspend fun offerAlternative(f: ProviderFailure) {
        val s = c.settings.flow.first()
        val candidates = s.providers.filter { it.id != f.providerId && it.ready }
        val next = candidates.firstOrNull { it.id == "deepseek" } ?: candidates.firstOrNull()
        _offer.value = if (next != null) ProviderOffer(
            title = "Лимит ${f.providerName}",
            message = "Похоже, закончился лимит или баланс. Продолжить на ${next.name}?",
            providerId = next.id, providerName = next.name,
        ) else ProviderOffer(
            title = "Нужен другой провайдер",
            message = "У ${f.providerName} закончился лимит. Добавьте ключ другого провайдера в настройках.",
            providerId = null, providerName = null,
        )
    }
}
