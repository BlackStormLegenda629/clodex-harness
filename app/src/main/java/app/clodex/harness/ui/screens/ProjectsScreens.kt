package app.clodex.harness.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import app.clodex.harness.data.MEMORY_NOTE_LIMIT
import app.clodex.harness.data.MemoryNote
import app.clodex.harness.data.ProjectEntity
import app.clodex.harness.data.fileList
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HDim
import app.clodex.harness.ui.vm.AppTab
import app.clodex.harness.ui.vm.SheetKind
import app.clodex.harness.util.ArtifactIo
import app.clodex.harness.util.RelativeTime

@Composable
fun ProjectsHeader() {
    val vms = LocalVms.current
    Header("Проекты", big = true, actions = {
        IconBtn(HIcon.Filter, "Фильтр", { vms.app.open(SheetKind.ProjectFilter) }, circle = true)
        GearBtn()
    })
}

@Composable
fun ColumnScope.ProjectsBody() {
    val vms = LocalVms.current; val nav = LocalNav.current
    val all by vms.projects.projects.collectAsStateWithLifecycle()
    val filter by vms.projects.filter.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    val archived = filter == "Архивированные"
    val list = all.filter { it.archived == archived && it.name.lowercase().contains(q.trim().lowercase()) }
    Body {
        Srch(q, { q = it }, "Поиск проектов")
        if (list.isEmpty()) Emp(if (archived) "В архиве пока ничего нет" else "Здесь пока пусто", HIcon.Stack)
        list.forEach { p ->
            It(onClick = { nav.project(p.id) }) {
                Titles(p.name, RelativeTime.modified(p.updatedAt))
                if (p.pinned) Ic(HIcon.Pin, tint = H.c.mu, size = 16.dp)
                Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
            }
        }
    }
}

@Composable
fun ProjectsDock() {
    val vms = LocalVms.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Fab("Новый проект", { vms.app.open(SheetKind.NewProject) }) }
}

@Composable
private fun ProjCard(title: String, action: String, modifier: Modifier, onClick: () -> Unit) {
    val c = H.c
    val shape = RoundedCornerShape(HDim.card)
    Column(
        modifier.heightIn(min = 112.dp).hClick(onClick = onClick).clip(shape).background(c.s1).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = H.t.row14Med, color = c.ink)
        Text(action, style = H.t.row14Med, color = c.ac)
    }
}

@Composable
fun ProjectScreen(id: Long) {
    val nav = LocalNav.current; val vms = LocalVms.current; val ctx = LocalContext.current
    val p by vms.projects.project(id).collectAsStateWithLifecycle(null)
    val chats by vms.projects.chatsOf(id).collectAsStateWithLifecycle(emptyList())
    val sessions by vms.projects.sessionsOf(id).collectAsStateWithLifecycle(emptyList())
    var seg by rememberSaveable { mutableStateOf("Чаты") }
    val proj = p
    if (proj == null) {
        ScreenFrame { Header("", onBack = { nav.pop() }); Emp("Проект не найден", HIcon.Stack) }
        return
    }
    ScreenFrame(dock = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(HIcon.Share, "Поделиться", { ArtifactIo.shareText(ctx, proj.name + if (proj.description.isNotBlank()) "\n" + proj.description else "") }, circle = true, size = 48.dp)
            IconBtn(HIcon.Pen, "Изменить", { vms.app.open(SheetKind.NewProject, proj.id.toString()) }, circle = true, size = 48.dp)
            IconBtn(HIcon.Trash, "Удалить", { vms.app.open(SheetKind.DeleteProject, proj.id.toString()) }, circle = true, size = 48.dp)
            if (seg == "Чаты") Btn("Новый чат", { vms.chat.newChat(proj.id) { nav.toTab(AppTab.CHAT) } }, Modifier.weight(1f), primary = true, icon = HIcon.Plus)
            else Btn("Новая сессия", { vms.app.open(SheetKind.NewSession, id.toString()) }, Modifier.weight(1f), primary = true, icon = HIcon.Plus)
        }
    }) {
        Header("", onBack = { nav.pop() }, actions = { IconBtn(HIcon.Dots, "Меню проекта", { vms.app.open(SheetKind.ProjectMenu, proj.id.toString()) }) })
        Body {
            Text(proj.name, style = H.t.pt, color = H.c.ink, modifier = Modifier.padding(horizontal = 20.dp))
            if (proj.description.isNotBlank()) Text(proj.description, style = H.t.body, color = H.c.mu, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp))
            Row(
                Modifier.padding(start = 20.dp, top = 12.dp).clip(RoundedCornerShape(HDim.chip)).background(H.c.s2).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) { Ic(HIcon.Lock, size = 16.dp, tint = H.c.mu); Text("Приватный", style = H.t.row14, color = H.c.mu) }

            Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val n = proj.fileList().size
                ProjCard("Контекст", if (n > 0) "$n файл." else "Добавить файлы", Modifier.weight(1f)) { nav.push("context/${proj.id}") }
                ProjCard("Пользовательские инструкции", if (proj.instructions.isBlank()) "Добавить инструкции" else "Изменить", Modifier.weight(1f)) {
                    vms.app.open(SheetKind.Instructions, proj.id.toString())
                }
            }
            GrpOf({
                Li(onClick = { nav.push("memory/${proj.id}") }) {
                    Text("Память", style = H.t.bodyMed, color = H.c.ink, modifier = Modifier.weight(1f))
                    Text(if (proj.useMemory) "Вкл." else "Выкл.", style = H.t.row14, color = H.c.mu)
                    Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
                }
            })
            Seg(listOf("Чаты", "Код"), seg, { seg = it }, pad = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 4.dp))
            if (seg == "Чаты") {
                if (chats.isEmpty()) Emp("В проекте пока нет чатов")
                val generating by vms.chat.generatingChatIds.collectAsStateWithLifecycle()
                chats.forEach { ch ->
                    It(onClick = { vms.chat.open(ch.id); nav.toTab(AppTab.CHAT) }) {
                        IconPlate(HIcon.Chat)
                        Titles(ch.title, app.clodex.harness.ui.vm.chatStatus(ch.id in generating, RelativeTime.chat(ch.updatedAt)))
                        if (ch.id in generating) LiveDot()
                    }
                }
            } else {
                if (sessions.isEmpty()) Emp("В проекте пока нет сессий")
                sessions.forEach { s -> It(onClick = { nav.session(s.id) }) { IconPlate(HIcon.Code); Titles(s.title, s.repo + " · " + s.branch, mono = true) } }
            }
        }
    }
}

@Composable
fun ContextScreen(id: Long) {
    val nav = LocalNav.current; val vms = LocalVms.current
    val p by vms.projects.project(id).collectAsStateWithLifecycle(null)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vms.projects.addFiles(id, uris) }
    val files = p?.fileList().orEmpty()
    val pct = minOf(100, files.size * 4)
    ScreenFrame(dock = { Btn("Добавить контент", { pick.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), primary = true, icon = HIcon.Docp) }) {
        Header("Контекст", onBack = { nav.pop() })
        Body {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(H.c.s2)) {
                    Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(H.c.ac))
                }
                Text("Использовано $pct% емкости проекта", style = H.t.small, color = H.c.mu)
            }
            if (files.isEmpty()) Emp("Добавьте документы, код или данные. Claude будет использовать их как контекст.", HIcon.Docp)
            files.forEach { f -> It { IconPlate(HIcon.File); Titles(f) } }
        }
    }
}

@Composable
fun MemoryScreen(id: Long) {
    val nav = LocalNav.current; val vms = LocalVms.current
    val p by remember(id) { vms.projects.project(id) }.collectAsStateWithLifecycle(null)
    val settings by vms.settings.settings.collectAsStateWithLifecycle()
    val global = id == 0L
    val active = if (global) settings.on("p2") else p?.useMemory == true
    val notes by remember(id) { vms.projects.memory(id) }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable(id) { mutableStateOf("") }
    var editing by rememberSaveable(id) { mutableStateOf<Long?>(null) }
    var draft by rememberSaveable(id) { mutableStateOf("") }
    var deleting by rememberSaveable(id) { mutableStateOf<Long?>(null) }
    if (editing != null) AlertDialog(
        onDismissRequest = { editing = null },
        title = { Text(if (editing == 0L) "Новая заметка" else "Изменить заметку") },
        text = { LiField("Текст заметки", draft, { draft = it.take(MEMORY_NOTE_LIMIT) }, singleLine = false, minLines = 3) },
        confirmButton = { TextButton(enabled = draft.isNotBlank(), onClick = {
            editing?.let { vms.projects.saveMemory(id, it, draft) }; editing = null
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { editing = null }) { Text("Отмена") } },
    )
    notes.firstOrNull { it.id == deleting }?.let { note -> AlertDialog(
        onDismissRequest = { deleting = null }, title = { Text("Удалить заметку?") },
        confirmButton = { TextButton(onClick = { vms.projects.deleteMemory(note); deleting = null }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
    ) }
    ScreenFrame {
        Header(if (global) "Общая память" else "Память проекта", onBack = { nav.pop() }, subtitle = p?.name)
        Body {
            if (global || p != null) {
                GrpOf({ Li(onClick = { if (global) vms.settings.toggle("p2") else vms.projects.toggleMemory(id) }, role = androidx.compose.ui.semantics.Role.Switch) {
                    Titles("Добавлять заметки к запросам"); Sw(active)
                } })
                SheetNote(if (global) "Общие заметки могут передаваться провайдеру в Chat и Code." else "Эти заметки могут передаваться провайдеру только в Chat и Code этого проекта.", Modifier.padding(horizontal = 20.dp))
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Добавить заметку", { draft = ""; editing = 0L }, primary = true, icon = HIcon.Plus)
                    if (!global) Btn("Общая память", { nav.push("memory/0") })
                }
                if (notes.isNotEmpty()) Srch(query, { query = it }, "Поиск по заметкам")
                val matches = notes.filter { it.text.contains(query.trim(), ignoreCase = true) }
                if (matches.isEmpty()) Emp(if (notes.isEmpty()) "Заметок пока нет" else "Ничего не найдено")
                else Grp(matches, animate = false) { note, _ ->
                    Li(onClick = { draft = note.text; editing = note.id }) {
                        Titles(note.text, ellipsis = false)
                        IconBtn(HIcon.Trash, "Удалить заметку", { deleting = note.id })
                    }
                }
            }
        }
    }
}
