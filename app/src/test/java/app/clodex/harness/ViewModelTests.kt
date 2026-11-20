package app.clodex.harness

import app.clodex.harness.data.*
import app.clodex.harness.data.mcp.*
import app.clodex.harness.ui.vm.ConnectorsViewModel
import app.clodex.harness.ui.vm.SkillsViewModel
import app.clodex.harness.util.ToastBus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SkillsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val toasts = ToastBus()

    private fun vm(fetcher: FakeFetcher = FakeFetcher(Result.failure(ClaudeException("x"))), repo: FakeSkillRepo = FakeSkillRepo()) =
        SkillsViewModel(repo, fetcher, toasts) to repo

    @Test fun createManualSkill_addsEnabledMineSkillWithDerivedDescription() {
        val (vm, repo) = vm()
        var created: Boolean? = null
        vm.saveManual(null, "Код-ревью", "Проверь diff по чек-листу\nвторая строка") { created = it }
        val s = repo.state.value.single()
        assertEquals(true, created)
        assertEquals("Код-ревью", s.name)
        assertEquals(Src.MINE, s.source)
        assertTrue(s.enabled)
        assertEquals("Проверь diff по чек-листу", s.description)
    }

    @Test fun createManualSkill_withoutNameIsRejected() {
        val (vm, repo) = vm()
        vm.saveManual(null, "  ", "body")
        assertTrue(repo.state.value.isEmpty())
        assertEquals("Введите название", toasts.last)
    }

    @Test fun editManualSkill_updatesInPlace() {
        val (vm, repo) = vm()
        vm.saveManual(null, "A", "one")
        val id = repo.state.value.single().id
        vm.saveManual(id, "B", "two")
        assertEquals(1, repo.state.value.size)
        assertEquals("B", repo.state.value.single().name)
        assertEquals("two", repo.state.value.single().body)
    }

    @Test fun installFromGitHub_addsSkillWithRepoPathAndBranch() {
        val fetcher = FakeFetcher(Result.success(RemoteSkill("pdf-tools", "Работа с PDF", "---\nname: pdf-tools\n---\nbody")))
        val (vm, repo) = vm(fetcher)
        var ok = false
        vm.installFromGitHub("https://github.com/acme/skills", "skills/pdf", "dev") { ok = it }
        assertTrue(ok)
        assertEquals(Triple("acme/skills", "skills/pdf", "dev"), fetcher.lastArgs)
        val s = repo.state.value.single()
        assertEquals(Src.GITHUB, s.source)
        assertEquals("acme/skills", s.repo)
        assertEquals("skills/pdf", s.path)
        assertEquals("dev", s.ref)
        assertEquals("pdf-tools", s.name)
    }

    @Test fun installFromGitHub_defaultsBranchToMain_andUpdatesDuplicateInsteadOfAddingTwice() {
        val fetcher = FakeFetcher(Result.success(RemoteSkill("a", "d1", "b1")))
        val (vm, repo) = vm(fetcher)
        vm.installFromGitHub("acme/skills", "x", "")
        fetcher.result = Result.success(RemoteSkill("a", "d2", "b2"))
        vm.installFromGitHub("acme/skills", "x", "main")
        assertEquals("main", fetcher.lastArgs?.third)
        assertEquals(1, repo.state.value.size)
        assertEquals("d2", repo.state.value.single().description)
    }

    @Test fun installFromGitHub_badRepoAndNetworkFailureAddNothing() {
        val fetcher = FakeFetcher(Result.failure(ClaudeException("Не найден SKILL.md")))
        val (vm, repo) = vm(fetcher)
        var ok = true
        vm.installFromGitHub("not a repo", "", "") { ok = it }
        assertFalse(ok); assertEquals(0, fetcher.calls)
        vm.installFromGitHub("acme/skills", "", "") { ok = it }
        assertFalse(ok); assertEquals(1, fetcher.calls)
        assertTrue(repo.state.value.isEmpty())
        assertEquals("Не найден SKILL.md", toasts.last)
    }

    @Test fun builtinSkillCannotBeDeleted_butToggles() {
        val repo = FakeSkillRepo(listOf(SkillEntity("pdf", "PDF", "d", Src.BUILTIN, true, "Docp")))
        val (vm, _) = vm(repo = repo)
        vm.delete("pdf")
        assertEquals(1, repo.state.value.size)
        vm.toggle("pdf")
        assertFalse(repo.state.value.single().enabled)
    }

    @Test fun parser_readsFrontmatterAndRepoForms() {
        val r = SkillParser.parse("---\nname: \"Commit style\"\ndescription: Правила коммитов\n---\n# Body", "fallback")
        assertEquals("Commit style", r.name); assertEquals("Правила коммитов", r.description)
        assertEquals("acme/skills", SkillParser.parseRepo("acme/skills"))
        assertEquals("acme/skills", SkillParser.parseRepo("https://github.com/acme/skills.git"))
        assertEquals("acme/skills", SkillParser.parseRepo("https://github.com/acme/skills/tree/main/x"))
        assertNull(SkillParser.parseRepo("acme"))
        assertNull(SkillParser.parseRepo("a/b/c"))
    }
}

class ConnectorsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val toasts = ToastBus()
    private val seed = listOf(
        ConnectorEntity("gm", "Gmail", "Google", "d", "#D93025", ConStatus.ON, 0),
        ConnectorEntity("nt", "Notion", "Notion", "d", "ink", ConStatus.WARN, 1),
        ConnectorEntity("sl", "Slack", "Slack", "d", "#611F69", ConStatus.NEW, 2),
    )

    private fun probe() = McpProbe(McpServerInfo("Test", "1"), listOf(McpToolSpec("search")), checkedAt = 1)

    @Test fun toggleTurnsOffButCannotTurnOnWithoutARealSavedConnection() {
        val repo = FakeConnectorRepo(seed)
        val runtime = McpRepository(FakeMcpConfigRepository(), FakeMcpGateway(Result.success(probe())))
        val vm = ConnectorsViewModel(repo, runtime, toasts)
        vm.toggle("gm")
        assertEquals(ConStatus.OFF, repo.state.value.first { it.id == "gm" }.status)
        vm.toggle("gm")
        assertEquals(ConStatus.OFF, repo.state.value.first { it.id == "gm" }.status)
        assertEquals("Сначала настройте подключение", toasts.last)
    }

    @Test fun connectOnlyTurnsOnAfterProbeAndFailureBecomesWarning() {
        val repo = FakeConnectorRepo(seed)
        val gateway = FakeMcpGateway(Result.success(probe()))
        val runtime = McpRepository(FakeMcpConfigRepository(), gateway)
        val vm = ConnectorsViewModel(repo, runtime, toasts)
        val config = McpConnectionConfig("sl", "https://mcp.example")

        vm.connect("sl", config)
        assertEquals(ConStatus.ON, repo.state.value.first { it.id == "sl" }.status)
        assertEquals("Slack подключён · 1 инструмент", toasts.last)
        gateway.probeResult = Result.failure(McpProtocolException("offline"))
        vm.connect("sl")
        assertEquals(ConStatus.WARN, repo.state.value.first { it.id == "sl" }.status)
        vm.disconnect("sl"); assertEquals(ConStatus.NEW, repo.state.value.first { it.id == "sl" }.status)
    }

    @Test fun addByUrlProbesBeforeAddingConnectedCustomServer() {
        val repo = FakeConnectorRepo(seed)
        val runtime = McpRepository(FakeMcpConfigRepository(), FakeMcpGateway(Result.success(probe())))
        val vm = ConnectorsViewModel(repo, runtime, toasts)
        var ok = true
        vm.addByUrl("не адрес") { ok = it }
        assertFalse(ok); assertEquals(3, repo.state.value.size)
        vm.addByUrl("https://mcp.example.com/sse") { ok = it }
        assertTrue(ok)
        val added = repo.state.value.minByOrNull { it.sortKey }!!
        assertEquals("mcp.example.com", added.name); assertEquals(ConStatus.ON, added.status)
    }
}
