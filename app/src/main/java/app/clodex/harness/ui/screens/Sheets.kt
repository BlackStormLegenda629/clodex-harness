package app.clodex.harness.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clodex.harness.data.ConStatus
import app.clodex.harness.data.ConnectorCatalog
import app.clodex.harness.data.ModelCatalog
import app.clodex.harness.data.ProviderCfg
import app.clodex.harness.data.Src
import app.clodex.harness.data.agent.isOpenRouterSearchEndpoint
import app.clodex.harness.data.fileList
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.icons.HIcons
import app.clodex.harness.ui.nav.LocalAttach
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HDim
import app.clodex.harness.util.Saf
import app.clodex.harness.ui.vm.AppTab
import app.clodex.harness.ui.vm.Sheet
import app.clodex.harness.ui.vm.SheetKind

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit, placeholder: String, minLines: Int = 1, modifier: Modifier = Modifier) {
    val c = H.c; val t = H.t
    val shape = RoundedCornerShape(HDim.input)
    Column(modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = t.note, color = c.mu, modifier = Modifier.padding(horizontal = 4.dp))
        BasicTextField(
            value, onChange, textStyle = t.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink), minLines = minLines, singleLine = minLines == 1,
            modifier = Modifier.fillMaxWidth().clip(shape).background(c.s2).padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, style = t.body, color = c.mu); inner() } },
        )
    }
}

@Composable
private fun Cdet(mark: @Composable () -> Unit, title: String, sub: @Composable () -> Unit, desc: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        mark()
        Text(title, style = H.t.detTitle, color = H.c.ink, textAlign = TextAlign.Center)
        sub()
        Text(desc, style = H.t.body, color = H.c.mu, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BtnPair(left: String, onLeft: () -> Unit, right: String, onRight: () -> Unit, rightEnabled: Boolean = true, danger: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(left, onLeft, Modifier.weight(1f))
        Btn(right, onRight, Modifier.weight(1f), primary = !danger, enabled = rightEnabled, container = if (danger) H.c.dt else null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.SheetContent(sheet: Sheet) {
    val vms = LocalVms.current; val nav = LocalNav.current
    val app = vms.app
    val close = { app.close() }
    val arg = sheet.arg
    when (sheet.kind) {
        SheetKind.Add -> AddSheet()
        SheetKind.Model -> ModelSheet(close)
        SheetKind.Effort -> {
            val s by vms.settings.settings.collectAsStateWithLifecycle()
            SheetHeader("Уровень усилий", close, onBack = { app.open(SheetKind.Model) })
            listOf("Низкий", "Средний", "Высокий", "Сверхвысокий", "Максимальный").forEach { n ->
                R2Choice(
                    n, s.effort == n, { vms.settings.setEffort(n) },
                    badges = when (n) { "Средний" -> listOf("Рекомендуется" to BdgKind.Grey); "Максимальный" -> listOf("5× и более использования" to BdgKind.Warn); else -> emptyList() },
                )
            }
            SheetNote("Чем выше уровень усилий, тем тщательнее ответы, но это занимает больше времени и быстрее расходует лимиты использования.")
        }
        SheetKind.MoreModels -> MoreModelsSheet(close, { app.open(SheetKind.Model) })
        SheetKind.AddToProject -> {
            val projects by vms.chat.projects.collectAsStateWithLifecycle()
            val chat by vms.chat.current.collectAsStateWithLifecycle()
            SheetHeader("Добавить в проект", close, onBack = { app.open(SheetKind.Add) })
            R2Choice("Без проекта", chat?.projectId == null, { vms.chat.assignProject(null); close() })
            projects.filter { !it.archived }.forEach { p -> R2Choice(p.name, chat?.projectId == p.id, { vms.chat.assignProject(p.id); close() }, p.description.ifBlank { null }) }
        }
        SheetKind.NewProject -> {
            val editId = arg?.toLongOrNull()
            val existing by (if (editId != null) vms.projects.project(editId) else kotlinx.coroutines.flow.flowOf(null)).collectAsStateWithLifecycle(null)
            var name by rememberSaveable(editId, existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
            var desc by rememberSaveable(editId, existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
            SheetHeader(if (editId != null) "Изменить сведения" else "Создание проекта", close)
            FormField("Над чем вы работаете?", name, { name = it }, "Введите название проекта")
            FormField("Чего вы хотите достичь?", desc, { desc = it }, "Опишите проект, цели, тему и т. д…", minLines = 4)
            Btn(
                if (editId != null) "Сохранить" else "Создать проект",
                { if (editId != null) { vms.projects.update(editId, name, desc); close() } else vms.projects.create(name, desc) { id -> close(); nav.project(id) } },
                Modifier.fillMaxWidth(), primary = true, enabled = name.isNotBlank(),
            )
        }
        SheetKind.ProjectMenu -> {
            val id = arg?.toLongOrNull() ?: return
            val p by vms.projects.project(id).collectAsStateWithLifecycle(null)
            Box(Modifier.padding(top = 20.dp))
            val rows = listOf(
                Triple(HIcon.Pin, if (p?.pinned == true) "Открепить" else "Закрепить", { vms.projects.togglePin(id); close() }),
                Triple(HIcon.Pen, "Изменить сведения", { app.open(SheetKind.NewProject, id.toString()) }),
                Triple(HIcon.Arch, "Архивировать", { vms.projects.archive(id); close(); nav.pop() }),
                Triple(HIcon.Trash, "Удалить", { app.open(SheetKind.DeleteProject, id.toString()) }),
            )
            rows.forEachIndexed { i, (ic, label, act) ->
                R2(onClick = { act() }) {
                    Ic(ic, tint = if (i == 3) H.c.dt else H.c.ink)
                    Text(label, style = H.t.row16, color = if (i == 3) H.c.dt else H.c.ink, modifier = Modifier.weight(1f))
                }
            }
        }
        SheetKind.DeleteProject -> {
            val id = arg?.toLongOrNull() ?: return
            val p by vms.projects.project(id).collectAsStateWithLifecycle(null)
            SheetHeader("Удалить проект?", close)
            SheetNote("Проект «${p?.name.orEmpty()}» и его контекст будут удалены. Это действие нельзя отменить.")
            BtnPair("Отмена", close, "Удалить", { vms.projects.delete(id) { close(); nav.pop() } }, danger = true)
        }
        SheetKind.Instructions -> {
            val id = arg?.toLongOrNull() ?: return
            val p by vms.projects.project(id).collectAsStateWithLifecycle(null)
            var text by rememberSaveable(id, p?.id) { mutableStateOf(p?.instructions.orEmpty()) }
            SheetHeader("Пользовательские инструкции", close)
            Text("Укажите Claude, как вести себя и отвечать в проекте ${p?.name.orEmpty()}", style = H.t.note, color = H.c.mu, modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp).padding(bottom = 14.dp))
            FormField("Инструкции", text, { text = it }, "Дайте Claude указания с помощью промптов, например: используй профессиональный тон, пиши кратко и просто, ты — эксперт в астрофизике и т. д.", minLines = 5)
            BtnPair("Отмена", close, "Сохранить", { vms.projects.saveInstructions(id, text); close() })
        }
        SheetKind.ProjectFilter -> {
            val f by vms.projects.filter.collectAsStateWithLifecycle()
            Box(Modifier.padding(top = 20.dp))
            listOf(HIcon.User to "Ваши", HIcon.Arch to "Архивированные").forEach { (ic, l) ->
                R2(onClick = { vms.projects.setFilter(l); close() }) {
                    Ic(ic); Text(l, style = H.t.row16, color = H.c.ink, modifier = Modifier.weight(1f)); if (f == l) Ic(HIcon.Ck, tint = H.c.ac)
                }
            }
        }
        SheetKind.NewSession -> NewSessionSheet(arg?.toLongOrNull())
        SheetKind.Workspace -> WorkspaceSheet()
        SheetKind.GhWorkspace -> {
            var input by rememberSaveable { mutableStateOf("") }
            val busy by vms.code.checkingWorkspace.collectAsStateWithLifecycle()
            SheetHeader("Подключить репозиторий", close, onBack = { app.open(SheetKind.Workspace) })
            FormField("Репозиторий", input, { input = it }, "owner/repo или ссылка на GitHub")
            Btn(if (busy) "Проверяем доступ…" else "Подключить", {
                vms.code.addGithubWorkspace(input) { ok -> if (ok) app.closeIfOpen(SheetKind.GhWorkspace) }
            }, Modifier.fillMaxWidth(), primary = true, enabled = input.isNotBlank() && !busy)
        }
        SheetKind.SkillAdd -> {
            SheetHeader("Добавить скилл", close)
            R2(onClick = { app.open(SheetKind.SkillNew) }) {
                IconPlate(HIcon.Pen, circle = true)
                Titles("Создать вручную", "Инструкции, которые Claude будет учитывать")
                Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
            }
            R2(onClick = { app.open(SheetKind.SkillGh) }) {
                IconPlate(HIcon.Branch, circle = true)
                Titles("Из GitHub", "Папка с SKILL.md в вашем репозитории")
                Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
            }
        }
        SheetKind.SkillNew -> {
            val skills by vms.skills.skills.collectAsStateWithLifecycle()
            val e = arg?.let { id -> skills.firstOrNull { it.id == id } }
            var name by rememberSaveable(arg) { mutableStateOf(e?.name.orEmpty()) }
            var body by rememberSaveable(arg) { mutableStateOf(e?.body.orEmpty()) }
            SheetHeader(if (e != null) "Изменить скилл" else "Новый скилл", close, onBack = { if (e != null) app.open(SheetKind.SkillDetail, e.id) else app.open(SheetKind.SkillAdd) })
            FormField("Название", name, { name = it }, "Например, Код-ревью")
            FormField("Инструкции", body, { body = it }, "Опишите, как Claude должен выполнять задачу: шаги, правила, формат ответа…", minLines = 7)
            Btn(if (e != null) "Сохранить" else "Создать скилл", { vms.skills.saveManual(arg, name, body) { r -> if (r != null) close() } }, Modifier.fillMaxWidth(), primary = true)
        }
        SheetKind.SkillGh -> {
            var repo by rememberSaveable { mutableStateOf("") }
            var path by rememberSaveable { mutableStateOf("") }
            var branch by rememberSaveable { mutableStateOf("") }
            var busy by rememberSaveable { mutableStateOf(false) }
            SheetHeader("Из GitHub", close, onBack = { app.open(SheetKind.SkillAdd) })
            SheetNote("Укажите репозиторий и папку, где лежит SKILL.md. Claude прочитает инструкции и подключит скилл.")
            GrpOf(
                { LiField("Репозиторий", repo, { repo = it }, "owner/repo или ссылка на GitHub") },
                { LiField("Папка со скиллом", path, { path = it }, "skills/pdf (необязательно)") },
                { LiField("Ветка", branch, { branch = it }, "main") },
            )
            Box(Modifier.padding(top = 16.dp)) {
                Btn(if (busy) "Устанавливаю…" else "Установить", {
                    busy = true
                    vms.skills.installFromGitHub(repo, path, branch) { ok -> busy = false; if (ok) close() }
                }, Modifier.fillMaxWidth(), primary = true, enabled = !busy)
            }
        }
        SheetKind.SkillDetail -> {
            val skills by vms.skills.skills.collectAsStateWithLifecycle()
            val s = skills.firstOrNull { it.id == arg }
            SheetHeader("Скилл", close)
            if (s == null) { Emp("Скилл не найден"); return }
            Cdet({ IconPlate(HIcons.byName(s.icon), size = 56.dp, iconSize = 26.dp) }, s.name, {
                Text(s.source + (s.repo?.let { " · $it" } ?: ""), style = H.t.small, color = H.c.mu)
            }, s.description)
            GrpOf({ Li(onClick = { vms.skills.toggle(s.id) }, role = Role.Switch) { Text("Включён", style = H.t.body, color = H.c.ink, modifier = Modifier.weight(1f)); Sw(s.enabled) } })
            if (s.source == Src.GITHUB) {
                Box(Modifier.padding(top = 12.dp))
                GrpOf(
                    { Li { Text("Путь", style = H.t.body, color = H.c.ink, modifier = Modifier.weight(1f)); Text(s.path.orEmpty(), style = H.t.mono12, color = H.c.mu) } },
                    { Li { Text("Ветка", style = H.t.body, color = H.c.ink, modifier = Modifier.weight(1f)); Text(s.ref.orEmpty(), style = H.t.mono12, color = H.c.mu) } },
                )
                Btn("Обновить из GitHub", { vms.skills.refreshFromGitHub(s.id) }, Modifier.padding(top = 12.dp).fillMaxWidth())
            }
            if (s.source == Src.MINE) Btn("Изменить", { app.open(SheetKind.SkillNew, s.id) }, Modifier.padding(top = 12.dp).fillMaxWidth())
            if (s.source == Src.BUILTIN) SheetNote("Встроенный скилл нельзя удалить, но его можно выключить.", Modifier.fillMaxWidth())
            else Btn("Удалить", { vms.skills.delete(s.id) { close() } }, Modifier.padding(top = 12.dp).fillMaxWidth(), danger = true)
        }
        SheetKind.ConnectorDetail -> {
            val cons by vms.connectors.connectors.collectAsStateWithLifecycle()
            val c = cons.firstOrNull { it.id == arg }
            if (c == null) { SheetHeader("Коннектор", close); Emp("Коннектор не найден") }
            else ConnectorDetails(c, close)
        }
        SheetKind.ConnectorConfig -> ConnectorConfigSheet(arg, close)
        SheetKind.ConnectorAdd -> ConnectorConfigSheet(null, close)
        SheetKind.ProviderEdit -> ProviderEditSheet(arg, close)
        SheetKind.FilePreview -> {
            val uri = arg
            val ctx = LocalContext.current
            val name = rememberSaveable(uri) { uri?.let { Saf.name(ctx, android.net.Uri.parse(it)) } ?: "Файл" }
            val text = remember(uri) { uri?.let { runCatching { Saf.readText(ctx, android.net.Uri.parse(it)) }.getOrNull() } }
            SheetHeader(name, close)
            if (text == null) Emp("Предпросмотр недоступен для этого файла", HIcon.File)
            else {
                val shape = RoundedCornerShape(HDim.card)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text, style = H.t.monoCode, color = H.c.ink,
                        modifier = Modifier.fillMaxWidth().clip(shape).background(H.c.s1).padding(16.dp),
                    )
                }
            }
        }
        SheetKind.WipeConfirm -> {
            SheetHeader("Удалить все данные?", close)
            SheetNote("Чаты, проекты, сессии, артефакты, свои скиллы и настройки будут удалены с этого устройства. Это действие нельзя отменить.")
            BtnPair("Отмена", close, "Удалить", { vms.settings.wipeAll { close(); nav.toTab(AppTab.CHAT) } }, danger = true)
        }
        SheetKind.Licenses -> {
            SheetHeader("Лицензии", close)
            listOf(
                "Jetpack Compose, Room, DataStore, Navigation" to "Apache 2.0",
                "Ktor, OkHttp, kotlinx.coroutines, kotlinx.serialization" to "Apache 2.0",
                "Inter" to "SIL Open Font License 1.1",
                "JetBrains Mono" to "SIL Open Font License 1.1",
            ).forEach { (n, l) -> R2 { Titles(n, l, ellipsis = false) } }
        }
    }
}

@Composable
private fun ColumnScope.AddSheet() {
    val vms = LocalVms.current; val nav = LocalNav.current; val attach = LocalAttach.current
    val s by vms.settings.settings.collectAsStateWithLifecycle()
    val chat by vms.chat.current.collectAsStateWithLifecycle()
    val projects by vms.chat.projects.collectAsStateWithLifecycle()
    val close = { vms.app.close() }
    SheetHeader("Добавление в чат", close)
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(HIcon.Cam, "Камера", attach.camera), Triple(HIcon.Img, "Фото", attach.photo), Triple(HIcon.File, "Файлы", attach.files)).forEach { (ic, l, act) ->
            Column(
                Modifier.weight(1f).hClick { act(); close() }.clip(RoundedCornerShape(HDim.card)).background(H.c.s1).heightIn(min = 104.dp).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) { IconPlate(ic, circle = true); Text(l, style = H.t.row14Med, color = H.c.ink) }
        }
    }
    R2(onClick = { attach.video(); close() }) { IconPlate(HIcon.Vid, circle = true); Text("Записать видео", style = H.t.bodyMed, color = H.c.ink, modifier = Modifier.weight(1f)) }
    R2(onClick = { vms.app.open(SheetKind.AddToProject) }) {
        IconPlate(HIcon.Stack, circle = true)
        Titles("Добавить в проект", projects.firstOrNull { it.id == chat?.projectId }?.name ?: "Нет")
        Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
    }
    val provider = s.activeProvider()
    val openRouterSearch = provider.openAi && isOpenRouterSearchEndpoint(provider.url)
    val searchSupported = !provider.openAi || openRouterSearch
    R2(onClick = if (searchSupported || s.on("web")) ({ vms.settings.toggle("web") }) else null, role = Role.Switch) {
        IconPlate(HIcon.Globe, circle = true)
        Titles("Веб-поиск", when {
            !searchSupported -> "Недоступен у этого провайдера"
            openRouterSearch -> "OpenRouter · возможна доплата"
            else -> null
        })
        if (searchSupported || s.on("web")) Sw(s.on("web"))
    }
    R2(onClick = { close(); nav.settings("mcp") }) {
        IconPlate(HIcon.Plug, circle = true); Text("Коннекторы", style = H.t.bodyMed, color = H.c.ink, modifier = Modifier.weight(1f)); Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
    }
    R2(onClick = { close(); nav.settings("schedule") }) {
        IconPlate(HIcon.Wait, circle = true)
        Titles("Запланированные сообщения")
        Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
    }
}

@Composable
private fun ColumnScope.NewSessionSheet(projectId: Long?) {
    val vms = LocalVms.current; val nav = LocalNav.current
    val spaces by vms.code.workspaces.collectAsStateWithLifecycle()
    val ws by vms.code.workspace.collectAsStateWithLifecycle()
    val mode by vms.code.mode.collectAsStateWithLifecycle()
    val names = spaces.map { it.name }
    var repo by rememberSaveable { mutableStateOf("") }
    var task by rememberSaveable { mutableStateOf("") }
    var m by rememberSaveable { mutableStateOf("") }
    val effRepo = repo.ifEmpty { if (ws != "Все" && ws in names) ws else names.firstOrNull().orEmpty() }
    val effMode = m.ifEmpty { mode }
    SheetHeader("Новая сессия", { vms.app.close() })
    Text("Репозиторий", style = H.t.note, color = H.c.mu, modifier = Modifier.padding(horizontal = 4.dp))
    if (names.isEmpty()) SheetNote("Сначала добавьте репозиторий в рабочем пространстве.")
    else Seg(names, effRepo, { repo = it }, pad = PaddingValues(top = 8.dp, bottom = 12.dp))
    FormField("", task, { task = it }, "Что нужно сделать?", minLines = 3)
    Text("Режим", style = H.t.note, color = H.c.mu, modifier = Modifier.padding(horizontal = 4.dp))
    Seg(listOf("План", "Правки", "Авто"), effMode, { m = it }, pad = PaddingValues(top = 8.dp, bottom = 4.dp))
    SheetNote(
        when (effMode) {
            "План" -> "Агент только читает файлы и предлагает план — ничего не меняет."
            "Авто" -> "Изменения применяются сами в конце задачи: запись в папку или коммит в ветку claude/…"
            else -> "Агент готовит правки, вы смотрите дифф и решаете, применять ли их."
        } + " Команды, сборка и тесты не запускаются.",
    )
    Btn("Запустить", { vms.code.launch(task, effRepo, effMode, projectId) { id -> vms.app.close(); nav.session(id) } }, Modifier.fillMaxWidth().padding(top = 8.dp), primary = true, enabled = names.isNotEmpty() && task.isNotBlank())
}

@Composable
private fun ColumnScope.WorkspaceSheet() {
    val vms = LocalVms.current; val ctx = LocalContext.current; val nav = LocalNav.current
    val spaces by vms.code.workspaces.collectAsStateWithLifecycle()
    val ws by vms.code.workspace.collectAsStateWithLifecycle()
    val busy by vms.code.checkingWorkspace.collectAsStateWithLifecycle()
    val closeOnSuccess: (Boolean) -> Unit = { ok -> if (ok) vms.app.closeIfOpen(SheetKind.Workspace) }
    var wt by rememberSaveable { mutableStateOf("Устройство") }
    val gh = wt == "GitHub"
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            Saf.takePermission(ctx, uri)
            val name = Saf.name(ctx, uri)
            vms.code.addDeviceWorkspace(name, uri.toString(), closeOnSuccess)
        }
    }
    SheetHeader("Сменить рабочую область", { vms.app.close() })
    if (busy) SheetNote("Проверяем доступ…")
    R2(onClick = if (busy) null else ({ vms.code.selectWorkspace("Все", closeOnSuccess) })) {
        IconPlate(HIcon.Stack, circle = true); Text("Все пространства", style = H.t.bodyMed, color = H.c.ink, modifier = Modifier.weight(1f)); if (ws == "Все") Ic(HIcon.Ck, tint = H.c.ac)
    }
    Seg(listOf("Устройство", "GitHub"), wt, { wt = it }, pad = PaddingValues(top = 4.dp, bottom = 12.dp))
    val list = spaces.filter { it.kind == if (gh) "gh" else "dev" }
    if (list.isEmpty()) SheetNote(if (gh) "Репозиториев пока нет. Подключите GitHub-репозиторий." else "Папок пока нет. Выберите любую папку — доступ сохранится после перезапуска.")
    list.forEach { w ->
        R2(onClick = if (busy) null else ({ vms.code.selectWorkspace(w.name, closeOnSuccess) })) {
            IconPlate(if (gh) HIcon.Branch else HIcon.Folder, circle = true)
            Titles(w.name, if (gh) w.path else "Папка на устройстве", mono = gh)
            if (ws == w.name) Ic(HIcon.Ck, tint = H.c.ac)
            if (!gh && w.path.startsWith("content:")) {
                IconBtn(HIcon.Folder, "Открыть ${w.name}", { vms.app.close(); nav.browse(w.path) }, size = 36.dp)
            }
            if (!busy) IconBtn(HIcon.Trash, "Удалить ${w.name}", { vms.code.deleteWorkspace(w.id) }, size = 36.dp)
        }
    }
    Btn(
        if (gh) "Подключить репозиторий GitHub" else "Выбрать папку на устройстве",
        { if (gh) vms.app.open(SheetKind.GhWorkspace) else pick.launch(null) },
        Modifier.padding(top = 8.dp).fillMaxWidth(), icon = HIcon.Plus, enabled = !busy,
    )
    Btn("Открыть терминал", { vms.app.close(); nav.terminal() }, Modifier.padding(top = 8.dp).fillMaxWidth(), icon = HIcon.Code)
    SheetNote("Терминал работает в песочнице приложения: /system/bin/sh и toybox. Git, Node и Python в неё не входят.")
}

// ---------------------------------------------------------------- models & providers

@Composable
private fun ModelRow(m: app.clodex.harness.data.ModelInfo, selected: Boolean, onClick: () -> Unit) {
    val settings by LocalVms.current.settings.settings.collectAsStateWithLifecycle()
    val provider = settings.provider(m.providerId)
    R2(onClick = onClick) {
        ProviderMark(m.providerId, provider?.name ?: m.providerId, 32.dp, provider?.url.orEmpty(), m.apiId)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(m.display, style = H.t.row16, color = H.c.ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                m.badge?.let { Bdg(it, if (it == "Лёгкая" || m.verified == false) BdgKind.Grey else BdgKind.Blue) }
            }
            if (m.desc.isNotBlank()) Text(m.desc, style = H.t.small, color = H.c.mu, modifier = Modifier.padding(top = 2.dp))
        }
        if (selected) Ic(HIcon.Ck, tint = H.c.ac)
    }
}

@Composable
private fun ColumnScope.ModelSheet(close: () -> Unit) {
    val vms = LocalVms.current; val app = vms.app
    val s by vms.settings.settings.collectAsStateWithLifecycle()
    val p = s.activeProvider()
    SheetHeader("Выбор модели", close)
    R2(onClick = { app.open(SheetKind.ProviderEdit, p.id) }) {
        ProviderMark(p.id, p.name, url = p.url)
        Column(Modifier.weight(1f)) {
            Text(p.name, style = H.t.row16, color = H.c.ink)
            Text(if (p.key.isBlank()) "Ключ не задан" else "Подключён", style = H.t.small, color = if (p.key.isBlank()) H.c.warn else H.c.mu)
        }
        Text("Сменить", style = H.t.row14, color = H.c.ac)
        Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
    }
    Sg("Основные модели")
    val own = s.modelsFor(p.id)
    val featured = if (p.builtin && p.models.isEmpty()) ModelCatalog.featured(p.id) else own.filter { it.verified != false }.take(4)
    if (featured.isEmpty()) SheetNote("У провайдера пока нет моделей. Добавьте их в настройках провайдера.")
    featured.forEach { m ->
        ModelRow(m, s.model == m.display && s.providerId == m.providerId, { vms.settings.setModel(m.display); close() })
    }
    if (p.builtin && p.id != "deepseek") {
        Sg("Для лёгких задач")
        ModelCatalog.deepseek.firstOrNull()?.let { m ->
            ModelRow(m, s.model == m.display && s.providerId == m.providerId, { vms.settings.setModel(m.display); close() })
        }
    }
    R2(onClick = { app.open(SheetKind.MoreModels) }) {
        IconPlate(HIcon.Stack, circle = true)
        Text("Другие модели", style = H.t.row16, color = H.c.ink, modifier = Modifier.weight(1f))
        Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
    }
    R2(onClick = { app.open(SheetKind.Effort) }) {
        IconPlate(HIcon.Gear, circle = true)
        Text("Уровень усилий", style = H.t.row16, color = H.c.ink, modifier = Modifier.weight(1f))
        Bdg("Рекомендуется", BdgKind.Grey)
        Text(s.effort, style = H.t.row14, color = H.c.mu)
        Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
    }
    SheetNote("Выбор модели также переключает провайдера. Если провайдер упрётся в лимит, приложение предложит продолжить на другом.")
}

@Composable
private fun ColumnScope.MoreModelsSheet(close: () -> Unit, onBack: () -> Unit) {
    val vms = LocalVms.current; val app = vms.app
    val s by vms.settings.settings.collectAsStateWithLifecycle()
    var pid by rememberSaveable { mutableStateOf(s.providerId) }
    val active = s.provider(pid) ?: s.activeProvider()
    SheetHeader("Другие модели", close, onBack = onBack)
    Seg(s.providers.map { it.name }, active.name, { n -> s.providers.firstOrNull { it.name == n }?.let { pid = it.id } })
    val models = s.modelsFor(active.id)
    if (models.isEmpty()) {
        SheetNote("У «${active.name}» нет списка моделей. Загрузите их с сервера в настройках провайдера.")
        Btn("Настроить провайдера", { app.open(SheetKind.ProviderEdit, active.id) }, Modifier.fillMaxWidth(), icon = HIcon.Gear)
    }
    models.forEach { m ->
        ModelRow(m, s.model == m.display && s.providerId == m.providerId, { vms.settings.setModel(m.display); close() })
    }
    if (active.builtin && active.models.isEmpty()) SheetNote("Это встроенный список. Нажмите «Проверить и модели» в настройках провайдера, чтобы получить точные ID с сервера.")
    else if (active.builtin) SheetNote("Модели с пометкой «Не найдена у провайдера» есть в каталоге, но сервер их не вернул.")
}

@Composable
private fun ColumnScope.ProviderEditSheet(id: String?, close: () -> Unit) {
    val vms = LocalVms.current
    val s by vms.settings.settings.collectAsStateWithLifecycle()
    val existing = id?.let { s.provider(it) }
    val isNew = existing == null
    var name by rememberSaveable(id, existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var url by rememberSaveable(id, existing?.id) { mutableStateOf(existing?.url.orEmpty()) }
    var key by rememberSaveable(id, existing?.id) { mutableStateOf(existing?.key.orEmpty()) }
    var fmt by rememberSaveable(id, existing?.id) { mutableStateOf(existing?.format ?: "OpenAI") }
    var models by rememberSaveable(id, existing?.id) { mutableStateOf(existing?.models?.joinToString(", ").orEmpty()) }
    var headers by rememberSaveable(id, existing?.id) { mutableStateOf(existing?.headers.orEmpty()) }
    androidx.compose.runtime.DisposableEffect(id) { vms.settings.clearFetch(); onDispose { vms.settings.clearFetch() } }
    val fetched by vms.settings.fetch.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(fetched.models) {
        if (fetched.models.isNotEmpty()) models = fetched.models.joinToString(", ")
    }
    val kind = existing?.kind ?: "custom"
    val builtin = kind == "anthropic" || kind == "openai" || kind == "deepseek"
    SheetHeader(if (isNew) "Новый провайдер" else existing?.name ?: "Провайдер", close)
    if (builtin) SheetNote("Встроенный провайдер. Можно заменить ключ, базовый URL (например, на прокси) и заголовки. «Проверить и модели» загрузит точные ID моделей с сервера.")
    GrpOf(
        { LiField("Название", name, { name = it }, "Мой провайдер") },
        { LiField("Базовый URL", url, { url = it }, "https://api.example.com/v1") },
        { LiField("Ключ API", key, { key = it }, "sk-…", password = true) },
        { LiField("Заголовки (Имя: значение; …)", headers, { headers = it }, "X-Org: team") },
    )
    if (!builtin) {
        Sg("Формат API")
        Seg(listOf("OpenAI", "Anthropic"), fmt, { fmt = it }, pad = PaddingValues(horizontal = 20.dp))
        Sg("Модели")
        GrpOf({ LiField("Через запятую", models, { models = it }, "model-a, model-b", singleLine = false, minLines = 2) })
        if (fetched.loading) SheetNote("Загружаю список моделей…")
        fetched.error?.let { SheetNote("Не удалось: $it") }
        if (fetched.models.isNotEmpty()) SheetNote("С сервера получено моделей: ${fetched.models.size}. Список подставлен выше — можно отредактировать.")
    }
    if (builtin) {
        if (fetched.loading) SheetNote("Загружаю список моделей…")
        fetched.error?.let { SheetNote("Не удалось: $it") }
        if (fetched.models.isNotEmpty()) SheetNote("С сервера получено моделей: ${fetched.models.size}. После сохранения они появятся в выборе модели.")
        else if (existing?.models?.isNotEmpty() == true) SheetNote("Сохранено моделей с сервера: ${existing.models.size}.")
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(if (fetched.loading) "Проверяю…" else "Проверить и модели", {
            vms.settings.fetchModels(url, key, if (builtin && kind == "anthropic") "Anthropic" else fmt, headers)
        }, Modifier.weight(1f), enabled = !fetched.loading, icon = HIcon.Globe)
        Btn("Сохранить", {
            val cfg = ProviderCfg(
                id = existing?.id ?: "own-${System.currentTimeMillis()}",
                kind = kind, name = name.trim().ifBlank { "Свой провайдер" }, url = url.trim(), key = key.trim(),
                models = models.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() },
                format = if (builtin && kind == "anthropic") "Anthropic" else fmt,
                headers = headers.trim(),
            )
            vms.settings.saveProvider(cfg); close()
        }, Modifier.weight(1f), primary = true, enabled = name.isNotBlank() && url.isNotBlank())
    }
    if (!builtin && existing != null) {
        val old = existing
        Btn("Удалить провайдера", { vms.settings.deleteProvider(old.id); close() }, Modifier.padding(top = 10.dp).fillMaxWidth(), danger = true, icon = HIcon.Trash)
    }
}
