package app.clodex.harness

import app.clodex.harness.data.*
import app.clodex.harness.data.agent.*
import app.clodex.harness.data.code.*
import app.clodex.harness.ui.vm.CodeViewModel
import app.clodex.harness.util.ToastBus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real agent loop ([codeRun]) over a scripted provider and an in-memory workspace. */
class FakeCodeAgent(val gateway: FakeWorkspaceGateway, var provider: ProviderAgentClient) : CodeAgent {
    var prepareResult: Result<String> = Result.success("")
    var applyResult: Result<WorkspaceApplyResult>? = null
    var prResult: Result<GitHubPullRequest> = Result.success(GitHubPullRequest(7, "https://github.com/acme/app/pull/7"))
    var hang = false
    var applyBarrier: CompletableDeferred<Unit>? = null
    val refs = mutableListOf<String>()
    val seeds = mutableListOf<List<WorkspaceChange>>()
    val applies = mutableListOf<Pair<String, String>>()
    var prCalls = 0

    override suspend fun prepare(ws: WorkspaceEntity) = prepareResult

    override suspend fun start(ws: WorkspaceEntity, ref: String, mode: CodeMode, history: List<AgentMessage>, seed: List<WorkspaceChange>, projectId: Long?): CodeRun {
        refs += ref; seeds += seed
        if (hang) return CodeRun(flow { emit(AgentEvent.TextDelta("частичный")); awaitCancellation() }) { emptyList() }
        return codeRun(provider, gateway, mode, AgentRequest("m", 100, "sys", history, false), seed)
    }

    override suspend fun apply(ws: WorkspaceEntity, base: String, head: String, changes: List<WorkspaceChange>, message: String): Result<WorkspaceApplyResult> {
        applies += base to head
        applyBarrier?.await()
        return applyResult ?: gateway.apply(changes, message)
    }

    override suspend fun openPullRequest(ws: WorkspaceEntity, base: String, head: String, title: String, body: String): Result<GitHubPullRequest> {
        prCalls++; return prResult
    }
}

/** First call fails mid-stream, later calls answer. */
class FlakyProvider : ProviderAgentClient {
    val requests = mutableListOf<ProviderRequest>()
    override fun stream(request: ProviderRequest): Flow<ProviderEvent> {
        requests += request
        val first = requests.size == 1
        return flow {
            if (first) { emit(ProviderEvent.TextDelta("частичный")); throw ClaudeException("перегрузка (529)") }
            emit(ProviderEvent.TextDelta("Ответ после повтора")); emit(ProviderEvent.Completed("end_turn"))
        }
    }
}

class CodeViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val toasts = ToastBus()

    private fun call(id: String, name: String, path: String, content: String? = null) =
        ProviderToolCall(id, name, buildJsonObject { put("path", path); content?.let { put("content", it) } })

    private fun reply(text: String) = listOf<ProviderEvent>(ProviderEvent.TextDelta(text), ProviderEvent.Completed("end_turn"))

    private fun editRounds(text: String = "Исправил App.kt") = listOf(
        listOf(
            ProviderEvent.ToolCallDelta("1", "workspace_read"), ProviderEvent.ToolCallComplete(call("1", "workspace_read", "src/App.kt")),
            ProviderEvent.ToolCallDelta("2", "workspace_write"), ProviderEvent.ToolCallComplete(call("2", "workspace_write", "src/App.kt", "a\nB\nc\n")),
            ProviderEvent.Completed("tool_use"),
        ),
        listOf(ProviderEvent.SummaryDelta("Проверяю правку"), ProviderEvent.TextDelta(text), ProviderEvent.Completed("end_turn")),
    )

    private class Env(val repo: FakeSessionRepo, val agent: FakeCodeAgent, val vm: CodeViewModel, val prefs: FakePrefs)

    private fun env(rounds: List<List<ProviderEvent>>, kind: String = "dev", sessions: List<SessionEntity> = emptyList()): Env {
        val repo = FakeSessionRepo(sessions)
        runBlocking { repo.addWorkspace(WorkspaceEntity(name = "web", kind = kind, path = if (kind == "gh") "acme/web" else "content://tree/web")) }
        val gw = FakeWorkspaceGateway(mapOf("src/App.kt" to "a\nb\nc\n"))
        val agent = FakeCodeAgent(gw, ScriptedProvider(rounds))
        if (kind == "gh") { agent.prepareResult = Result.success("main"); gw.commitSha = "abc1234def" }
        val prefs = FakePrefs()
        return Env(repo, agent, CodeViewModel(repo, prefs, toasts, agent, clock = { 42L }), prefs)
    }

    private fun Env.launch(mode: String = "Правки", task: String = "Почини App.kt"): Long {
        var id = -1L
        vm.launch(task, "web", mode) { id = it }
        return id
    }

    private fun Env.session(id: Long) = repo.sessions.value.first { it.id == id }
    private fun Env.messages(id: Long) = runBlocking { repo.messageList(id) }
    private fun Env.events(id: Long) = repo.eventRows.value.filter { it.sessionId == id }
    private fun Env.changes(id: Long) = repo.changeRows.value.filter { it.sessionId == id }
    private val Env.provider get() = agent.provider as ScriptedProvider

    @Test fun planModeCannotApplyEditsStagedInAnEarlierMode() {
        val e = env(editRounds())
        val id = e.launch()
        val pending = e.changes(id)
        assertTrue(pending.isNotEmpty())
        e.vm.setSessionMode(id, "План")
        e.vm.approve(id)
        assertTrue(e.agent.applies.isEmpty())
        assertEquals(pending, e.changes(id))
    }

    @Test fun inaccessibleWorkspacesAreNotSavedOrSelected() {
        val e = env(emptyList())
        val before = e.vm.workspaces.value
        e.agent.prepareResult = Result.failure(WorkspaceException("Нет доступа"))
        e.vm.addDeviceWorkspace("private", "content://unavailable") { assertFalse(it) }
        e.vm.addGithubWorkspace("acme/unavailable") { assertFalse(it) }
        assertEquals(before, e.vm.workspaces.value)
        assertEquals("Все", e.prefs.workspace.value)
    }

    @Test fun switchingWorkspaceValidatesAccessWithoutMovingPendingChanges() {
        val e = env(editRounds())
        val id = e.launch()
        val original = e.session(id)
        val pending = e.changes(id)
        assertTrue(pending.isNotEmpty())
        e.agent.prepareResult = Result.failure(WorkspaceException("Доступ отозван"))
        e.vm.selectWorkspace("web")
        assertEquals("Все", e.prefs.workspace.value)
        e.agent.prepareResult = Result.success("")
        e.vm.selectWorkspace("web")
        assertEquals("web", e.prefs.workspace.value)
        assertEquals(original, e.session(id))
        assertEquals(pending, e.changes(id))
    }

    @Test fun changingDefaultsDoesNotRewriteExistingSession() {
        val e = env(editRounds())
        val id = e.launch()
        val original = e.session(id)
        val pending = e.changes(id)
        e.vm.setMode("План")
        assertEquals("План", e.prefs.mode.value)
        assertEquals(original, e.session(id))
        assertEquals(pending, e.changes(id))
        var next = -1L
        e.vm.launch("Изучи проект", "web", e.prefs.mode.value) { next = it }
        assertEquals("План", e.session(next).mode)
    }

    @Test fun launch_streamsRealReply_andFinishesWithoutChanges() {
        val e = env(listOf(reply("## Всё в порядке\nФайл корректен.")))
        val id = e.launch()
        val s = e.session(id)
        assertEquals(SesStatus.DONE, s.status)
        assertEquals("Всё в порядке", s.summary)
        assertEquals(42L, s.startedAt)
        assertEquals("Правки", s.mode)
        assertEquals(listOf(Role.USER, Role.ASSISTANT), e.messages(id).map { it.role })
        assertEquals("## Всё в порядке\nФайл корректен.", e.messages(id).last().text)
        val req = e.provider.requests.single()
        assertEquals("Почини App.kt", (req.messages.single() as AgentMessage.User).text)
        assertTrue(e.events(id).single().title.startsWith("Ход рассуждений"))
        assertTrue(e.vm.live.value.isEmpty()); assertTrue(e.vm.running.value.isEmpty())
        assertTrue(e.agent.gateway.applied.isEmpty())
    }

    @Test fun edits_persistToolEvents_andWaitForApproval() {
        val e = env(editRounds())
        val id = e.launch()
        val s = e.session(id)
        assertEquals(SesStatus.WAIT, s.status); assertEquals(Perm.PENDING, s.perm)
        assertEquals("+1 −1", s.diff)
        assertEquals("Ждёт подтверждения · 1 файл", s.summary)
        assertEquals(listOf("Прочитал src/App.kt", "Подготовил изменение src/App.kt"), e.events(id).filter { it.kind == EventKind.TOOL }.map { it.title })
        assertTrue(e.events(id).first { it.kind == EventKind.STEP }.detail.contains("Проверяю правку"))
        val ch = e.changes(id).single()
        assertEquals(ChangeStatus.PENDING, ch.status); assertEquals("a\nb\nc\n", ch.beforeText); assertEquals(1, ch.added)
        assertEquals("a\nb\nc\n", e.agent.gateway.files["src/App.kt"])
        assertTrue(e.agent.gateway.applied.isEmpty())
    }

    @Test fun approve_appliesForReal() {
        val e = env(editRounds())
        val id = e.launch()
        e.vm.approve(id)
        assertEquals("a\nB\nc\n", e.agent.gateway.files["src/App.kt"])
        assertEquals(ChangeStatus.APPLIED, e.changes(id).single().status)
        val s = e.session(id)
        assertEquals(SesStatus.DONE, s.status); assertEquals(Perm.ALLOWED, s.perm)
        assertEquals("Записал 1 файл в «web»", e.events(id).last().title)
        assertEquals("", s.commitSha)
        e.vm.approve(id)
        assertEquals(1, e.agent.applies.size)
    }

    @Test fun deny_recordsDenialAndWritesNothing() {
        val e = env(editRounds())
        val id = e.launch()
        e.vm.deny(id)
        val s = e.session(id)
        assertEquals(Perm.DENIED, s.perm); assertEquals(SesStatus.DONE, s.status); assertEquals("Изменения отклонены", s.summary)
        assertEquals(ChangeStatus.DENIED, e.changes(id).single().status)
        assertEquals(EventStatus.DENIED, e.events(id).last().status)
        assertTrue(e.agent.applies.isEmpty())
    }

    @Test fun applyFailure_keepsChangesPending() {
        val e = env(editRounds())
        e.agent.applyResult = Result.failure(WorkspaceException("Нет доступа к папке — выберите папку заново", recoverable = true))
        val id = e.launch()
        e.vm.approve(id)
        val s = e.session(id)
        assertEquals(SesStatus.WAIT, s.status); assertEquals(Perm.PENDING, s.perm)
        assertTrue(s.summary.startsWith("Ошибка: Нет доступа"))
        assertEquals(ChangeStatus.PENDING, e.changes(id).single().status)
        assertEquals(EventStatus.ERROR, e.events(id).last().status)
    }

    @Test fun planMode_offersNoWriteTool() {
        val e = env(listOf(reply("План: 1. Прочитать App.kt")))
        val id = e.launch(mode = "План")
        assertEquals(listOf("workspace_list", "workspace_read", "workspace_grep", "workspace_find"), e.provider.requests.single().tools.map { it.name })
        assertEquals(SesStatus.DONE, e.session(id).status)
    }

    @Test fun autoMode_appliesWithoutApproval() {
        val e = env(editRounds())
        val id = e.launch(mode = "Авто")
        assertEquals("a\nB\nc\n", e.agent.gateway.files["src/App.kt"])
        val s = e.session(id)
        assertEquals(SesStatus.DONE, s.status); assertEquals(Perm.ALLOWED, s.perm); assertEquals("+1 −1", s.diff)
        assertEquals(ChangeStatus.APPLIED, e.changes(id).single().status)
    }

    @Test fun prepareFailure_skipsProvider() {
        val e = env(listOf(reply("не должно случиться")))
        e.agent.prepareResult = Result.failure(WorkspaceException("Нет доступа к папке", recoverable = true))
        val id = e.launch()
        assertTrue(e.provider.requests.isEmpty())
        val s = e.session(id)
        assertEquals(SesStatus.WAIT, s.status); assertEquals("Ошибка: Нет доступа к папке", s.summary)
        assertEquals(EventKind.ERROR, e.events(id).single().kind)
        assertEquals(listOf(Role.USER), e.messages(id).map { it.role })
    }

    @Test fun providerError_staysRetryable() {
        val e = env(emptyList())
        val flaky = FlakyProvider(); e.agent.provider = flaky
        val id = e.launch()
        val s = e.session(id)
        assertEquals(SesStatus.WAIT, s.status); assertTrue(s.summary, s.summary.contains("перегрузка"))
        assertEquals(listOf(Role.USER), e.messages(id).map { it.role })
        assertEquals("частичный", e.events(id).single { it.kind == EventKind.ERROR }.detail)
        e.vm.retry(id)
        assertEquals(SesStatus.DONE, e.session(id).status)
        assertEquals(1, flaky.requests[1].messages.size)
        assertEquals("Ответ после повтора", e.messages(id).last().text)
    }

    @Test fun sendMessage_continuesHistory_andCarriesPendingChanges() {
        val e = env(editRounds() + listOf(reply("Оставил правку как есть")))
        val id = e.launch()
        e.vm.sendMessage(id, "А тесты?")
        val last = e.provider.requests.last().messages
        assertEquals(3, last.size)
        assertTrue(last[0] is AgentMessage.User && last[1] is AgentMessage.Assistant && last[2] is AgentMessage.User)
        assertEquals("a\nB\nc\n", e.agent.seeds.last().single().after)
        assertEquals(listOf(ChangeStatus.SUPERSEDED, ChangeStatus.PENDING), e.changes(id).map { it.status })
        assertEquals(SesStatus.WAIT, e.session(id).status)
    }

    @Test fun stop_cancelsRunAndClearsRunState() {
        val e = env(emptyList())
        e.agent.hang = true
        val id = e.launch()
        assertEquals(setOf(id), e.vm.running.value)
        assertEquals("частичный", e.vm.live.value[id]?.text)
        e.vm.sendMessage(id, "ещё")
        assertEquals(1, e.messages(id).size)
        e.vm.stop(id)
        val s = e.session(id)
        assertEquals(SesStatus.WAIT, s.status); assertEquals("Остановлено", s.summary)
        assertTrue(e.vm.running.value.isEmpty()); assertTrue(e.vm.live.value.isEmpty())
        assertEquals(listOf(Role.USER), e.messages(id).map { it.role })
    }

    @Test fun github_commitsToHeadBranch_thenOpensOnePr() {
        val e = env(editRounds(), kind = "gh")
        val id = e.launch(task = "Fix login")
        val s0 = e.session(id)
        assertEquals("main", s0.baseBranch); assertEquals("claude/fix-login-$id", s0.branch)
        assertEquals("main", e.agent.refs.single())
        e.vm.approve(id)
        val s = e.session(id)
        assertEquals("abc1234def", s.commitSha)
        assertEquals("main" to "claude/fix-login-$id", e.agent.applies.single())
        assertTrue(e.events(id).last().title.startsWith("Коммит abc1234"))
        e.vm.setSessionMode(id, "План")
        e.vm.createPr(id)
        assertEquals(0, e.agent.prCalls)
        e.vm.setSessionMode(id, "Правки")
        e.vm.createPr(id)
        assertEquals(7, e.session(id).prNumber); assertEquals("https://github.com/acme/app/pull/7", e.session(id).prUrl)
        assertEquals("PR #7 создан", e.events(id).last().title)
        e.vm.createPr(id)
        assertEquals(1, e.agent.prCalls)
    }

    @Test fun manualApplyBlocksAnotherApplyDenialAndModeChanges() {
        val e = env(editRounds())
        val id = e.launch()
        val barrier = CompletableDeferred<Unit>()
        e.agent.applyBarrier = barrier
        try {
            e.vm.approve(id)
            e.vm.deny(id)
            e.vm.setSessionMode(id, "План")
            e.vm.approve(id)
            assertEquals("Правки", e.session(id).mode)
            assertEquals(ChangeStatus.PENDING, e.changes(id).single().status)
            assertEquals(1, e.agent.applies.size)
        } finally {
            barrier.complete(Unit)
        }
        assertEquals(ChangeStatus.APPLIED, e.changes(id).single().status)
    }

    @Test fun pr_requiresGithubAndACommit() {
        val dev = env(listOf(reply("ok")))
        val id = dev.launch()
        dev.vm.createPr(id)
        assertEquals("PR доступен только для GitHub-репозиториев", toasts.last)
        assertEquals(0, dev.agent.prCalls)
        val gh = env(listOf(reply("ok")), kind = "gh")
        val gid = gh.launch()
        gh.vm.createPr(gid)
        assertEquals("Сначала примените изменения", toasts.last)
        assertEquals(0, gh.agent.prCalls)
    }

    @Test fun prFailure_doesNotInventANumber() {
        val e = env(editRounds(), kind = "gh")
        e.agent.prResult = Result.failure(WorkspaceException("GitHub отклонил запрос (A pull request already exists)"))
        val id = e.launch()
        e.vm.approve(id); e.vm.createPr(id)
        assertEquals(0, e.session(id).prNumber); assertEquals("", e.session(id).prUrl)
        assertEquals(EventKind.ERROR, e.events(id).last().kind)
    }

    @Test fun interruptedRun_becomesWaitOnStart() {
        val stale = SessionEntity(id = 5, title = "Old", repo = "web", branch = "", status = SesStatus.RUN, summary = "Работает…", diff = "", startedAt = 0)
        val e = env(emptyList(), sessions = listOf(stale))
        assertEquals(SesStatus.WAIT, e.session(5).status)
        assertTrue(e.session(5).summary.startsWith("Прервано"))
    }

    @Test fun launch_requiresWorkspaceAndTask() {
        val e = env(listOf(reply("x")))
        e.vm.launch("  ", "web", "Правки")
        assertEquals("Опишите задачу", toasts.last)
        e.vm.launch("Задача", "нет такой", "Правки")
        assertEquals("Сначала добавьте рабочую область", toasts.last)
        assertTrue(e.repo.sessions.value.isEmpty())
    }

    @Test fun githubWorkspace_validatesAndDeduplicates() {
        val repo = FakeSessionRepo(); val vm = CodeViewModel(repo, FakePrefs(), toasts, FakeCodeAgent(FakeWorkspaceGateway(), ScriptedProvider(emptyList())))
        var ok = true
        vm.addGithubWorkspace("nope") { ok = it }; assertFalse(ok)
        vm.addGithubWorkspace("acme/api-server") { ok = it }; assertTrue(ok)
        vm.addGithubWorkspace("https://github.com/acme/api-server") { ok = it }; assertFalse(ok)
        assertEquals(1, repo.workspaces.value.size)
    }

    @Test fun deviceWorkspace_namesAreUnique() {
        val repo = FakeSessionRepo(); val vm = CodeViewModel(repo, FakePrefs(), toasts, FakeCodeAgent(FakeWorkspaceGateway(), ScriptedProvider(emptyList())))
        vm.addDeviceWorkspace("src", "content://a")
        vm.addDeviceWorkspace("src", "content://b")
        vm.addDeviceWorkspace("src", "content://b")
        assertEquals(listOf("src", "src 2"), repo.workspaces.value.map { it.name })
    }
}
