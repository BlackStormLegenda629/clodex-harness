package app.clodex.harness.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.clodex.harness.data.ChangeStatus
import app.clodex.harness.data.CodePrefs
import app.clodex.harness.data.EventKind
import app.clodex.harness.data.EventStatus
import app.clodex.harness.data.Perm
import app.clodex.harness.data.Role
import app.clodex.harness.data.SesStatus
import app.clodex.harness.data.SessionChangeEntity
import app.clodex.harness.data.SessionEntity
import app.clodex.harness.data.SessionEventEntity
import app.clodex.harness.data.SessionMessageEntity
import app.clodex.harness.data.SessionRepository
import app.clodex.harness.data.SkillParser
import app.clodex.harness.data.WorkDir
import app.clodex.harness.data.WorkDirMapping
import app.clodex.harness.data.WorkspaceEntity
import app.clodex.harness.data.agent.AgentEvent
import app.clodex.harness.data.agent.AgentMessage
import app.clodex.harness.data.code.CodeAgent
import app.clodex.harness.data.code.CodeMode
import app.clodex.harness.data.code.TextDiff
import app.clodex.harness.data.code.WorkspaceChange
import app.clodex.harness.data.code.WorkspacePath
import app.clodex.harness.data.code.codeEventTitle
import app.clodex.harness.data.code.headBranch
import app.clodex.harness.data.code.CodeFiles
import app.clodex.harness.data.code.WorkspaceException
import app.clodex.harness.clodex.thinking.ThinkingPresentation
import app.clodex.harness.util.ToastBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import app.clodex.harness.data.notifications.NotifyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a running session shows while the provider streams: factual steps and the partial answer. */
data class CodeLive(val thinking: ThinkingPresentation, val text: String)

class CodeViewModel(
    private val repo: SessionRepository,
    private val prefs: CodePrefs,
    private val toasts: ToastBus,
    private val agent: CodeAgent,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onNotify: (NotifyEvent, String, String) -> Unit = { _, _, _ -> },
    private val onRunStart: () -> Unit = {},
    val openFiles: suspend (WorkspaceEntity, String) -> Result<CodeFiles> = { _, _ -> Result.failure(WorkspaceException("Папка недоступна")) },
) : ViewModel() {
    val sessions: StateFlow<List<SessionEntity>> = repo.sessions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val workspaces: StateFlow<List<WorkspaceEntity>> = repo.workspaces.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val workspace: StateFlow<String> = prefs.workspace.stateIn(viewModelScope, SharingStarted.Eagerly, "Все")
    val mode: StateFlow<String> = prefs.mode.stateIn(viewModelScope, SharingStarted.Eagerly, "Правки")
    private val _checkingWorkspace = MutableStateFlow(false)
    val checkingWorkspace = _checkingWorkspace.asStateFlow()

    private val _live = MutableStateFlow<Map<Long, CodeLive>>(emptyMap())
    val live: StateFlow<Map<Long, CodeLive>> = _live.asStateFlow()
    private val _running = MutableStateFlow<Set<Long>>(emptySet())
    val running: StateFlow<Set<Long>> = _running.asStateFlow()
    private val _events = MutableSharedFlow<NotifyEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NotifyEvent> = _events
    private val jobs = mutableMapOf<Long, Job>()

    init {
        // A run cannot survive process death: nothing was applied, the user continues by sending a message.
        viewModelScope.launch {
            repo.sessions.first().filter { it.status == SesStatus.RUN }.forEach {
                repo.update(it.copy(status = SesStatus.WAIT, summary = "Прервано — отправьте сообщение, чтобы продолжить"))
            }
        }
    }

    fun session(id: Long): Flow<SessionEntity?> = repo.session(id)
    fun messages(id: Long): Flow<List<SessionMessageEntity>> = repo.messages(id)
    fun events(id: Long): Flow<List<SessionEventEntity>> = repo.events(id)
    fun changes(id: Long): Flow<List<SessionChangeEntity>> = repo.changes(id)

    private fun workspaceAction(onDone: (Boolean) -> Unit, action: suspend () -> Boolean) = viewModelScope.launch {
        if (_checkingWorkspace.value) return@launch
        _checkingWorkspace.value = true
        val ok = try { action() } finally { _checkingWorkspace.value = false }
        onDone(ok)
    }

    private suspend fun accessible(ws: WorkspaceEntity): Boolean {
        val error = agent.prepare(ws).exceptionOrNull() ?: return true
        if (error is CancellationException) throw error
        toasts.show(error.message ?: "Не удалось открыть рабочую область")
        return false
    }

    fun selectWorkspace(name: String, onDone: (Boolean) -> Unit = {}) = workspaceAction(onDone) {
        if (name != ALL_WORKSPACES) {
            val ws = repo.workspaces.first().firstOrNull { it.name == name }
            if (ws == null) { toasts.show("Рабочая область удалена"); return@workspaceAction false }
            if (!accessible(ws)) return@workspaceAction false
        }
        prefs.setWorkspace(name)
        true
    }
    fun setMode(m: String) = viewModelScope.launch { prefs.setMode(m) }
    fun setSessionMode(id: Long, m: String) = sessionJob(id, visible = false) {
        edit(id) { it.copy(mode = CodeMode.fromLabel(m).label) }
    }

    private suspend fun edit(id: Long, f: (SessionEntity) -> SessionEntity) { repo.get(id)?.let { repo.update(f(it)) } }

    private suspend fun event(id: Long, kind: String, title: String, detail: String = "", status: String = EventStatus.OK, at: Long = clock()) =
        repo.addEvent(SessionEventEntity(sessionId = id, kind = kind, title = title, detail = detail, status = status, createdAt = at))

    private suspend fun workspaceOf(s: SessionEntity): WorkspaceEntity? {
        val all = repo.workspaces.first()
        return all.firstOrNull { s.workspaceId != 0L && it.id == s.workspaceId } ?: all.firstOrNull { s.workspaceId == 0L && it.name == s.repo }
    }

    /** New session: creates it with the task as the first message and starts the agent. Returns the id via [onDone]. */
    fun launch(task: String, workspaceName: String, modeLabel: String, projectId: Long? = null, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        val text = task.trim()
        if (text.isEmpty()) { toasts.show("Опишите задачу"); return@launch }
        val ws = repo.workspaces.first().firstOrNull { it.name == workspaceName }
        if (ws == null) { toasts.show("Сначала добавьте рабочую область"); return@launch }
        val m = CodeMode.fromLabel(modeLabel)
        prefs.setMode(m.label)
        val id = repo.insert(
            SessionEntity(
                title = text.lineSequence().first().take(60).ifEmpty { "Новая задача" }, repo = ws.name, branch = "",
                status = SesStatus.RUN, summary = "Начинает работу…", diff = "", startedAt = clock(), perm = Perm.ALLOWED,
                workspaceId = ws.id, mode = m.label, projectId = projectId,
            ),
        )
        repo.addMessage(SessionMessageEntity(sessionId = id, role = Role.USER, text = text, createdAt = clock()))
        onDone(id)
        runSession(id)
    }

    fun sendMessage(id: Long, text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        return sessionJob(id, generation = true) {
            repo.addMessage(SessionMessageEntity(sessionId = id, role = Role.USER, text = t, createdAt = clock()))
            runOnce(id)
        }
    }

    /** Re-runs the session from its saved history (after an error or interruption). */
    fun retry(id: Long) { if (id !in jobs) runSession(id) }

    fun stop(id: Long) { jobs[id]?.cancel() }
    suspend fun cancelAndJoinRuns() {
        val active = jobs.values.toList()
        active.forEach { it.cancel() }
        active.forEach { it.join() }
    }

    private fun runSession(id: Long) = sessionJob(id, generation = true) { runOnce(id) }

    private fun sessionJob(id: Long, visible: Boolean = true, generation: Boolean = false, block: suspend () -> Unit): Boolean {
        if (id in jobs) return false
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                jobs.remove(id)
                _running.update { it - id }
                _live.update { it - id }
            }
        }
        jobs[id] = job
        if (visible) _running.update { it + id }
        if (generation) onRunStart()
        job.start()
        return true
    }

    private suspend fun runOnce(id: Long) {
        val start = repo.get(id) ?: return
        val ws = workspaceOf(start)
        if (ws == null) { fail(id, "Рабочая область удалена"); return }
        val progress = ReplyProgress(id, clock())
        try {
            repo.update(start.copy(status = SesStatus.RUN, summary = "Работает…"))
            val base = agent.prepare(ws).getOrElse { fail(id, it.message ?: "нет доступа к рабочей области"); return }
            val gh = ws.kind == "gh"
            val s = (repo.get(id) ?: return).let { cur ->
                cur.copy(baseBranch = cur.baseBranch.ifEmpty { base }, branch = if (gh) cur.branch.ifEmpty { headBranch(cur.title, id) } else cur.branch)
            }
            repo.update(s)
            val history = repo.messageList(id).mapNotNull {
                when (it.role) {
                    Role.USER -> AgentMessage.User(it.text)
                    Role.ASSISTANT -> AgentMessage.Assistant(it.text)
                    else -> null
                }
            }
            val seed = pendingChanges(id).map { it.second }
            val ref = if (!gh) "" else if (s.commitSha.isNotEmpty()) s.branch else s.baseBranch
            val mode = CodeMode.fromLabel(s.mode)
            val run = agent.start(ws, ref, mode, history, seed, s.projectId)
            _live.update { it + (id to CodeLive(progress.live(), "")) }
            run.events.collect { ev ->
                progress.apply(ev)
                if (ev is AgentEvent.ToolFinished) {
                    event(id, EventKind.TOOL, codeEventTitle(ev.call, ev.isError) ?: ev.title, status = if (ev.isError) EventStatus.ERROR else EventStatus.OK)
                }
                _live.update { it + (id to CodeLive(progress.live(), progress.answer)) }
            }
            val answer = progress.answer
            if (answer.isBlank()) throw EmptyReplyException()
            val seconds = progress.seconds(clock())
            event(id, EventKind.STEP, "Ход рассуждений · $seconds с", progress.steps.joinToString("\n"), at = progress.startedAt)
            repo.addMessage(SessionMessageEntity(sessionId = id, role = Role.ASSISTANT, text = answer, createdAt = clock()))
            val changes = run.changes()
            repo.moveChanges(id, ChangeStatus.PENDING, ChangeStatus.SUPERSEDED)
            changes.forEach { ch ->
                val d = TextDiff.summarize(ch.before, ch.after)
                repo.addChange(
                    SessionChangeEntity(sessionId = id, path = ch.path.value, beforeText = ch.before, afterText = ch.after,
                        added = d.added, removed = d.removed, createdAt = clock()),
                )
            }
            val summary = answer.lineSequence().map { it.trim().trimStart('#', '*', '-', ' ') }.firstOrNull { it.isNotEmpty() }.orEmpty().take(120)
            val cur = repo.get(id) ?: return
            when {
                changes.isEmpty() -> repo.update(cur.copy(status = SesStatus.DONE, perm = Perm.ALLOWED, summary = summary.ifEmpty { "Готово" })).also {
                    _events.tryEmit(NotifyEvent.SESSION_DONE)
                    onNotify(NotifyEvent.SESSION_DONE, "Code завершил сессию", cur.title)
                }
                mode == CodeMode.AUTO -> {
                    repo.update(cur.copy(diff = TextDiff.total(changes.map { it.before to it.after }).toString(), summary = "Применяет изменения…"))
                    applyPending(id)
                }
                else -> repo.update(
                    cur.copy(
                        status = SesStatus.WAIT, perm = Perm.PENDING,
                        summary = "Ждёт подтверждения · ${files(changes.size)}",
                        diff = TextDiff.total(changes.map { it.before to it.after }).toString(),
                    ).also {
                        _events.tryEmit(NotifyEvent.APPROVAL_NEEDED)
                        onNotify(NotifyEvent.APPROVAL_NEEDED, "Code ждёт подтверждения", cur.title)
                    }
                )
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                if (progress.answer.isNotBlank()) event(id, EventKind.STEP, "Остановлено пользователем", progress.answer, EventStatus.DENIED)
                edit(id) { it.copy(status = SesStatus.WAIT, summary = "Остановлено") }
            }
            throw e
        } catch (e: Exception) {
            fail(id, e.message ?: "неизвестная ошибка", partial = progress.answer)
        }
    }

    /** Errors stay out of the message history so a retry sends the same conversation again. */
    private suspend fun fail(id: Long, message: String, partial: String = "") {
        event(id, EventKind.ERROR, "Ошибка: $message", partial, EventStatus.ERROR)
        edit(id) { it.copy(status = SesStatus.WAIT, summary = "Ошибка: $message") }
    }

    private suspend fun pendingChanges(id: Long): List<Pair<SessionChangeEntity, WorkspaceChange>> =
        repo.changeList(id).filter { it.status == ChangeStatus.PENDING }.mapNotNull { row ->
            WorkspacePath.parse(row.path).getOrNull()?.let { row to WorkspaceChange(it, row.beforeText, row.afterText) }
        }

    private fun files(n: Int) = when {
        n % 10 == 1 && n % 100 != 11 -> "$n файл"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "$n файла"
        else -> "$n файлов"
    }

    /** Applies the pending changes for real and records exactly what happened. */
    private suspend fun applyPending(id: Long) {
        val s = repo.get(id) ?: return
        if (CodeMode.fromLabel(s.mode) == CodeMode.PLAN) {
            toasts.show("Чтобы применить правки, выйдите из режима «План»")
            return
        }
        val ws = workspaceOf(s) ?: run { fail(id, "Рабочая область удалена"); return }
        val pending = pendingChanges(id)
        if (pending.isEmpty()) { repo.update(s.copy(status = SesStatus.DONE, perm = Perm.ALLOWED)); return }
        val result = agent.apply(ws, s.baseBranch, s.branch, pending.map { it.second }, s.title)
        result.fold(
            onFailure = { e ->
                val msg = e.message ?: "ошибка записи"
                event(id, EventKind.APPLY, "Не удалось применить изменения", msg, EventStatus.ERROR)
                edit(id) { it.copy(status = SesStatus.WAIT, perm = Perm.PENDING, summary = "Ошибка: $msg") }
                toasts.show("Не удалось применить: $msg")
            },
            onSuccess = { r ->
                val applied = r.applied.map { it.value }.toSet()
                val failed = r.failed.associate { it.path.value to it.message }
                repo.setChangeStatus(pending.filter { it.first.path in applied }.map { it.first.id }, ChangeStatus.APPLIED)
                repo.setChangeStatus(pending.filter { it.first.path !in applied }.map { it.first.id }, ChangeStatus.FAILED)
                val title = when {
                    r.commitSha != null -> "Коммит ${r.commitSha.take(7)} в ${r.branch ?: s.branch} · ${files(applied.size)}"
                    else -> "Записал ${files(applied.size)} в «${ws.name}»"
                }
                val detail = failed.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                event(id, EventKind.APPLY, title, detail, if (failed.isEmpty()) EventStatus.OK else EventStatus.ERROR)
                edit(id) {
                    it.copy(
                        perm = Perm.ALLOWED,
                        commitSha = r.commitSha ?: it.commitSha,
                        status = if (failed.isEmpty()) SesStatus.DONE else SesStatus.WAIT,
                        summary = if (failed.isEmpty()) "Изменения применены · ${files(applied.size)}"
                        else "Применено ${applied.size} из ${pending.size} · есть ошибки",
                    )
                }
                toasts.show(if (failed.isEmpty()) "Изменения применены" else "Часть файлов не записана")
                if (failed.isEmpty()) {
                    _events.tryEmit(NotifyEvent.SESSION_DONE)
                    onNotify(NotifyEvent.SESSION_DONE, "Code завершил сессию", s.title)
                }
            },
        )
    }

    /** «Применить»: only for a session waiting for approval. */
    fun approve(id: Long) = sessionJob(id) {
        val s = repo.get(id) ?: return@sessionJob
        if (s.perm != Perm.PENDING || pendingChanges(id).isEmpty()) return@sessionJob
        applyPending(id)
    }

    fun deny(id: Long) = sessionJob(id) {
        repo.moveChanges(id, ChangeStatus.PENDING, ChangeStatus.DENIED)
        event(id, EventKind.APPLY, "Изменения отклонены", status = EventStatus.DENIED)
        edit(id) { it.copy(perm = Perm.DENIED, status = SesStatus.DONE, summary = "Изменения отклонены") }
    }

    /** Opens a real pull request; shown only after a successful GitHub commit. */
    fun createPr(id: Long) = sessionJob(id) {
        val s = repo.get(id) ?: return@sessionJob
        if (CodeMode.fromLabel(s.mode) == CodeMode.PLAN) return@sessionJob
        val ws = workspaceOf(s)
        if (ws == null || ws.kind != "gh") { toasts.show("PR доступен только для GitHub-репозиториев"); return@sessionJob }
        if (s.commitSha.isEmpty()) { toasts.show("Сначала примените изменения"); return@sessionJob }
        if (s.prNumber > 0) { toasts.show("PR уже создан · #${s.prNumber}"); return@sessionJob }
        val body = repo.messageList(id).lastOrNull { it.role == Role.ASSISTANT }?.text.orEmpty().take(4000)
        agent.openPullRequest(ws, s.baseBranch, s.branch, s.title, body).fold(
            onSuccess = { pr ->
                edit(id) { it.copy(prNumber = pr.number, prUrl = pr.url) }
                event(id, EventKind.PR, "PR #${pr.number} создан", pr.url)
                toasts.show("PR создан · #${pr.number}")
            },
            onFailure = { e ->
                val msg = e.message ?: "ошибка GitHub"
                event(id, EventKind.ERROR, "Не удалось создать PR", msg, EventStatus.ERROR)
                toasts.show("Не удалось создать PR: $msg")
            },
        )
    }

    fun addGithubWorkspace(input: String, onDone: (Boolean) -> Unit = {}) = workspaceAction(onDone) {
        val r = SkillParser.parseRepo(input)
        if (r == null) { toasts.show("Укажите репозиторий в формате owner/repo"); return@workspaceAction false }
        val name = r.substringAfter('/')
        if (repo.workspaces.first().any { it.name == name }) { toasts.show("Репозиторий уже добавлен"); return@workspaceAction false }
        val ws = WorkspaceEntity(name = name, kind = "gh", path = r)
        if (!accessible(ws)) return@workspaceAction false
        repo.addWorkspace(ws)
        prefs.setWorkspace(name)
        true
    }

    fun addDeviceWorkspace(name: String, path: String, onDone: (Boolean) -> Unit = {}) = workspaceAction(onDone) {
        val mapped = runCatching { WorkDirMapping.toWorkspace(WorkDir(path, "saf")) }.getOrElse {
            toasts.show("Не удалось открыть папку")
            return@workspaceAction false
        }
        val all = repo.workspaces.first()
        if (all.any { it.path == path }) { toasts.show("Папка уже добавлена"); return@workspaceAction false }
        val base = name.ifBlank { mapped.name }
        val names = all.map { it.name }.toSet() + ALL_WORKSPACES
        val n = generateSequence(2) { it + 1 }.map { "$base $it" }.let { seq -> if (base in names) seq.first { it !in names } else base }
        val ws = mapped.copy(name = n)
        if (!accessible(ws)) return@workspaceAction false
        repo.addWorkspace(ws)
        prefs.setWorkspace(n)
        toasts.show("Папка «$n» добавлена")
        true
    }

    fun deleteWorkspace(id: Long) = workspaceAction({}) {
        val ws = repo.workspaces.first().firstOrNull { it.id == id } ?: return@workspaceAction false
        repo.deleteWorkspace(id)
        if (prefs.workspace.first() == ws.name) prefs.setWorkspace(ALL_WORKSPACES)
        toasts.show("Рабочая область удалена")
        true
    }
}

private const val ALL_WORKSPACES = "Все"
