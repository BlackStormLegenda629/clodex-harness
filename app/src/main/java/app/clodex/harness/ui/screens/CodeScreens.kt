package app.clodex.harness.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clodex.harness.data.ChangeStatus
import app.clodex.harness.data.EventKind
import app.clodex.harness.data.EventStatus
import app.clodex.harness.data.Perm
import app.clodex.harness.data.Role
import app.clodex.harness.data.SesStatus
import app.clodex.harness.data.SessionChangeEntity
import app.clodex.harness.data.SessionEntity
import app.clodex.harness.data.SessionEventEntity
import app.clodex.harness.data.SessionMessageEntity
import app.clodex.harness.data.WorkspaceEntity
import app.clodex.harness.data.code.CodeMode
import app.clodex.harness.data.code.DiffKind
import app.clodex.harness.data.code.DiffLine
import app.clodex.harness.data.code.TextDiff
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HDim
import app.clodex.harness.ui.theme.LocalMotion
import app.clodex.harness.ui.vm.SheetKind
import app.clodex.harness.util.RelativeTime

@Composable
fun CodeHeader() {
    val nav = LocalNav.current
    Header("Программирование", big = true, actions = {
        IconBtn(HIcon.Code, "Терминал", { nav.terminal() }, circle = true)
        GearBtn()
    })
}

@Composable
fun CodeDock() {
    val vms = LocalVms.current
    val nav = LocalNav.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Btn("Терминал", { nav.terminal() }, icon = HIcon.Code)
        Box(Modifier.width(10.dp))
        Fab("Новая сессия", { vms.app.open(SheetKind.NewSession) })
    }
}

@Composable
private fun Spinner(tint: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
    val m = LocalMotion.current
    val angle = if (m.enabled) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "angle").value
    } else 0f
    Ic(HIcon.Spin, Modifier.rotate(angle), tint = tint, size = size)
}

@Composable
private fun StatusDot(status: String, perm: Int) {
    val c = H.c
    val (bg, fg) = when (status) { SesStatus.WAIT -> c.wb to c.warn; SesStatus.RUN -> c.bb to c.bt; else -> c.ab to c.at }
    Box(Modifier.size(36.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        when (status) { SesStatus.WAIT -> Ic(HIcon.Wait, tint = fg); SesStatus.RUN -> Spinner(fg); else -> Ic(HIcon.Ck, tint = fg) }
    }
}

@Composable
private fun SessionRow(s: SessionEntity, ws: WorkspaceEntity?, onClick: () -> Unit, index: Int) {
    val c = H.c
    val shape = RoundedCornerShape(HDim.card)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp).rise(index).hClick(onClick = onClick)
            .hCard(shape).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusDot(s.status, s.perm)
        Column(Modifier.weight(1f)) {
            Text(s.title, style = H.t.bodyMed, color = c.ink, maxLines = 1)
            Text(sessionPlace(s, ws), style = H.t.mono12, color = c.mu, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            Text(s.summary, style = H.t.small, color = c.mu, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            if (s.diff.isNotEmpty()) Text(s.diff, style = H.t.mono12, color = c.mu)
            Text(RelativeTime.session(s.startedAt), style = H.t.small, color = c.mu, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun ColumnScope.CodeBody() {
    val vms = LocalVms.current; val nav = LocalNav.current
    val all by vms.code.sessions.collectAsStateWithLifecycle()
    val ws by vms.code.workspace.collectAsStateWithLifecycle()
    val spaces by vms.code.workspaces.collectAsStateWithLifecycle()
    val list = all.filter { ws == "Все" || it.repo == ws }
    val groups = listOf(
        "Нужно внимание" to list.filter { it.status == SesStatus.WAIT },
        "В работе" to list.filter { it.status == SesStatus.RUN },
        "Завершены" to list.filter { it.status == SesStatus.DONE },
    )
    Body {
        val c = H.c
        Card(
            onClick = { vms.app.open(SheetKind.Workspace) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = c.bb, contentColor = c.bt),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Ic(HIcon.Branch, size = 28.dp)
                    Ic(HIcon.Ch)
                }
                Text("Рабочая область", style = MaterialTheme.typography.labelLarge)
                Text(if (ws == "Все") "Все рабочие области" else ws, style = MaterialTheme.typography.headlineSmall)
                Text("Доступно областей: ${spaces.size} · Нажмите, чтобы выбрать", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (list.isEmpty()) Emp("Сессий пока нет. Запустите первую кнопкой ниже.", HIcon.Code)
        groups.filter { it.second.isNotEmpty() }.forEach { (title, items) ->
            Sg(title)
            items.forEachIndexed { i, s -> SessionRow(s, workspaceOf(s, spaces), { nav.session(s.id) }, i) }
        }
    }
}

// ---------------------------------------------------------------- session

/** Subtitle for a session: GitHub branch, or the device folder (no branches there). */
fun sessionPlace(s: SessionEntity, ws: WorkspaceEntity?): String = when {
    ws?.kind == "dev" -> s.repo + " · Папка на устройстве"
    s.branch.isNotEmpty() -> s.repo + " · " + s.branch
    s.baseBranch.isNotEmpty() -> s.repo + " · " + s.baseBranch
    else -> s.repo
}

private fun workspaceOf(s: SessionEntity, all: List<WorkspaceEntity>): WorkspaceEntity? =
    all.firstOrNull { s.workspaceId != 0L && it.id == s.workspaceId } ?: all.firstOrNull { s.workspaceId == 0L && it.name == s.repo }

@Composable
private fun TNode(icon: HIcon?, tint: Color, bg: Color, title: String, last: Boolean, spinning: Boolean = false, content: (@Composable ColumnScope.() -> Unit)? = null) {
    val c = H.c
    Box(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp).drawBehind {
            if (!last) drawLine(c.line, Offset(12.dp.toPx(), 28.dp.toPx()), Offset(12.dp.toPx(), size.height - 2.dp.toPx()), 2.dp.toPx())
        },
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            if (spinning) Spinner(tint, 14.dp) else if (icon != null) Ic(icon, tint = tint, size = 14.dp, stroke = 2.4f)
        }
        Column(Modifier.padding(start = 38.dp, bottom = if (last) 8.dp else 20.dp)) {
            Text(title, style = H.t.row14Semi, color = c.ink)
            content?.invoke(this)
        }
    }
}

@Composable
private fun TBody(text: String, mono: Boolean = false) =
    Text(text, style = if (mono) H.t.mono12 else H.t.row14.copy(lineHeight = H.t.row14.fontSize * 1.5f), color = H.c.mu, modifier = Modifier.padding(top = 2.dp))

private sealed interface TItem {
    val at: Long
    val order: Int
    data class Msg(val m: SessionMessageEntity) : TItem {
        override val at get() = m.createdAt
        override val order get() = if (m.role == Role.USER) 0 else 2
    }
    data class Ev(val e: SessionEventEntity) : TItem {
        override val at get() = e.createdAt
        override val order get() = 1
    }
}

private val THINK_SECONDS = Regex("""Ход рассуждений · (\d+) с""")

@Composable
private fun EventNode(e: SessionEventEntity, last: Boolean, onThinking: (ThinkingPresentation) -> Unit) {
    val c = H.c
    if (e.kind == EventKind.STEP && e.title.startsWith("Ход рассуждений")) {
        val st = remember(e.id) {
            ThinkingPresentation(e.sessionId, thinking = false, seconds = THINK_SECONDS.find(e.title)?.groupValues?.get(1)?.toIntOrNull(), steps = e.detail.lines().filter { it.isNotBlank() })
        }
        ThinkingRow(st, onOpen = { onThinking(st) }, modifier = Modifier.padding(horizontal = 20.dp))
        return
    }
    val uri = LocalUriHandler.current
    val (icon, tint, bg) = when (e.status) {
        EventStatus.ERROR -> Triple(HIcon.X, c.dt, c.db)
        EventStatus.DENIED -> Triple(HIcon.X, c.mu, c.s2)
        else -> if (e.kind == EventKind.PR || e.kind == EventKind.APPLY) Triple(HIcon.Up, c.bg, c.ink) else Triple(HIcon.Ck, c.at, c.ab)
    }
    TNode(icon, tint, bg, e.title, last) {
        if (e.detail.isNotBlank()) {
            if (e.kind == EventKind.PR && e.detail.startsWith("https://")) {
                Text(e.detail, style = H.t.mono12, color = c.ac, modifier = Modifier.padding(top = 2.dp).hClick { runCatching { uri.openUri(e.detail) } })
            } else TBody(e.detail.take(600), mono = e.kind != EventKind.STEP)
        }
    }
}

@Composable
private fun ApprovalCard(ses: SessionEntity, ws: WorkspaceEntity?, pending: List<SessionChangeEntity>, onDiff: () -> Unit) {
    val vms = LocalVms.current
    val c = H.c
    val shape = RoundedCornerShape(HDim.card)
    TNode(HIcon.Wait, c.warn, c.wb, "Нужно подтверждение", true) {
        Column(Modifier.padding(top = 8.dp).fillMaxWidth().hCard(shape).padding(16.dp)) {
            Text("Применить изменения", style = H.t.row14Med, color = c.ink)
            Text(
                if (ws?.kind == "gh") "Будет создан коммит в ветке ${ses.branch} репозитория ${ws.path}." else "Файлы будут записаны в папку «${ses.repo}».",
                style = H.t.small, color = c.mu, modifier = Modifier.padding(top = 4.dp),
            )
            Column(
                Modifier.padding(top = 10.dp, bottom = 14.dp).fillMaxWidth().clip(RoundedCornerShape(HDim.input)).background(c.s2).hClick(onClick = onDiff)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pending.take(8).forEach { ch ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ch.path, style = H.t.mono12, color = c.ink, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(if (ch.beforeText == null) "новый · +${ch.added}" else "+${ch.added} −${ch.removed}", style = H.t.mono12, color = c.mu)
                    }
                }
                if (pending.size > 8) Text("и ещё ${pending.size - 8}", style = H.t.small, color = c.mu)
                Text("Открыть дифф", style = H.t.small, color = c.ac)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Применить", { vms.code.approve(ses.id) }, Modifier.weight(1f), primary = true, enabled = CodeMode.fromLabel(ses.mode) != CodeMode.PLAN)
                Btn("Отклонить", { vms.code.deny(ses.id) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SessionScreen(id: Long) {
    val nav = LocalNav.current; val vms = LocalVms.current
    val s by vms.code.session(id).collectAsStateWithLifecycle(null)
    val msgs by vms.code.messages(id).collectAsStateWithLifecycle(emptyList())
    val events by vms.code.events(id).collectAsStateWithLifecycle(emptyList())
    val changes by vms.code.changes(id).collectAsStateWithLifecycle(emptyList())
    val spaces by vms.code.workspaces.collectAsStateWithLifecycle()
    val running by vms.code.running.collectAsStateWithLifecycle()
    val liveAll by vms.code.live.collectAsStateWithLifecycle()
    val settings by vms.settings.settings.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var summary by remember { mutableStateOf<ThinkingPresentation?>(null) }
    val ses = s
    if (ses == null) { ScreenFrame { Header("", onBack = { nav.pop() }); Emp("Сессия не найдена", HIcon.Code) }; return }
    val c = H.c
    val ws = workspaceOf(ses, spaces)
    val isRunning = id in running
    var showFiles by rememberSaveable(id) { mutableStateOf(false) }
    var replyHtml by remember(id) { mutableStateOf<String?>(null) }
    val ref = if (ses.commitSha.isNotEmpty()) ses.branch else ses.baseBranch
    if (showFiles && ws != null) CodeFilesDialog(ws, ref, vms.code.openFiles) { showFiles = false }
    replyHtml?.let { HtmlViewer("index.html", it) { replyHtml = null } }
    val live = liveAll[id]
    val pending = changes.filter { it.status == ChangeStatus.PENDING }
    val items = remember(msgs, events) {
        (msgs.map { TItem.Msg(it) } + events.map { TItem.Ev(it) }).sortedWith(compareBy({ it.at }, { it.order }))
    }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    summary?.let { st -> ThinkingSummaryDialog(st, onDismiss = { summary = null }) }
    ScreenFrame(dock = {
        if (!isRunning) Seg(CodeMode.entries.map { it.label }, CodeMode.fromLabel(ses.mode).label, { vms.code.setSessionMode(id, it) }, pad = PaddingValues(horizontal = 4.dp))
        Composer(
            text, { text = it }, "Сообщение агенту…", settings.providerId, settings.activeProvider().name, settings.model, settings.effort,
            onPlus = { vms.app.open(SheetKind.Add) }, onModel = { vms.app.open(SheetKind.Model) },
            onSend = { if (!isRunning && vms.code.sendMessage(id, text)) text = "" },
            generating = isRunning, onStop = { vms.code.stop(id) },
        )
    }) {
        Header(ses.title, onBack = { nav.pop() }, subtitle = sessionPlace(ses, ws), actions = {
            if (ws != null) HChip("Файлы", showFiles) { showFiles = true }
            if (changes.any { it.status != ChangeStatus.SUPERSEDED }) HChip(ses.diff.ifEmpty { "Дифф" }, false, mono = true) { nav.push("diff/$id") }
        })
        Body {
            if (ws == null) Text("Рабочая область удалена — сессию можно только просмотреть.", style = H.t.small, color = c.warn, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { i, item ->
                    when (item) {
                        is TItem.Msg -> {
                            val m = item.m
                            if (m.role == Role.USER) Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.CenterEnd) {
                                Text(
                                    m.text, style = H.t.msgUser, color = c.bg,
                                    modifier = Modifier.widthIn(max = if (wide) 440.dp else 290.dp).clip(RoundedCornerShape(HDim.userMsgBig, HDim.userMsgBig, HDim.userMsgSmall, HDim.userMsgBig)).background(c.ink).padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            } else Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { AssistantBlocks(m.text) { replyHtml = it } }
                        }
                        is TItem.Ev -> EventNode(item.e, last = items.getOrNull(i + 1) !is TItem.Ev, onThinking = { summary = it })
                    }
                }
                if (isRunning) {
                    if (live != null) {
                        ThinkingRow(live.thinking, onOpen = { summary = live.thinking }, modifier = Modifier.padding(horizontal = 20.dp))
                        if (live.text.isNotEmpty()) Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { AssistantBlocks(live.text) { replyHtml = it } }
                    } else TNode(null, c.bt, c.bb, "Операция выполняется…", true, spinning = true)
                } else {
                    if (ses.perm == Perm.PENDING && pending.isNotEmpty() && ws != null) ApprovalCard(ses, ws, pending) { nav.push("diff/$id") }
                    val lastMsg = msgs.lastOrNull()
                    if (ses.status == SesStatus.WAIT && ses.perm != Perm.PENDING && lastMsg?.role == Role.USER && ws != null) {
                        Btn("Повторить", { vms.code.retry(id) }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), icon = HIcon.Spin)
                    }
                    if (ws?.kind == "gh" && ses.commitSha.isNotEmpty()) {
                        val uri = LocalUriHandler.current
                        if (ses.prNumber > 0) Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 8.dp).hClick { if (ses.prUrl.isNotEmpty()) runCatching { uri.openUri(ses.prUrl) } },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) { Ic(HIcon.Ck, tint = c.ac, size = 16.dp); Text("PR создан · #${ses.prNumber}", style = H.t.row14Med, color = c.ac) }
                        else if (CodeMode.fromLabel(ses.mode) != CodeMode.PLAN) Btn("Создать PR", { vms.code.createPr(id) }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), icon = HIcon.Branch)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- diff

private const val MAX_DIFF_LINES = 3000

private data class NumberedLine(val n: Int, val kind: DiffKind, val text: String)

private fun numbered(lines: List<DiffLine>): List<NumberedLine> {
    var old = 0; var new = 0
    return lines.map { l ->
        when (l.kind) {
            DiffKind.SAME -> { old++; new++; NumberedLine(new, l.kind, l.text) }
            DiffKind.ADD -> { new++; NumberedLine(new, l.kind, l.text) }
            DiffKind.DEL -> { old++; NumberedLine(old, l.kind, l.text) }
        }
    }
}

private fun changeLabel(status: String) = when (status) {
    ChangeStatus.PENDING -> "ждёт подтверждения"
    ChangeStatus.APPLIED -> "применено"
    ChangeStatus.DENIED -> "отклонено"
    ChangeStatus.FAILED -> "не записано"
    else -> status
}

@Composable
fun DiffScreen(id: Long) {
    val nav = LocalNav.current; val vms = LocalVms.current
    val s by vms.code.session(id).collectAsStateWithLifecycle(null)
    val all by vms.code.changes(id).collectAsStateWithLifecycle(emptyList())
    val spaces by vms.code.workspaces.collectAsStateWithLifecycle()
    val running by vms.code.running.collectAsStateWithLifecycle()
    val c = H.c
    val ses = s
    // Latest version of each file; superseded drafts are history, not the current diff.
    val changes = remember(all) { all.filter { it.status != ChangeStatus.SUPERSEDED }.groupBy { it.path }.map { it.value.last() } }
    var file by rememberSaveable { mutableStateOf("") }
    val cur = changes.firstOrNull { it.path == file } ?: changes.firstOrNull()
    val ws = ses?.let { workspaceOf(it, spaces) }
    val canDecide = ses != null && ses.perm == Perm.PENDING && id !in running && changes.any { it.status == ChangeStatus.PENDING } && ws != null
    val allowsWrites = ses != null && CodeMode.fromLabel(ses.mode) != CodeMode.PLAN
    val canPr = ses != null && allowsWrites && id !in running && ws?.kind == "gh" && ses.commitSha.isNotEmpty() && ses.prNumber == 0
    val total = diffTotals(changes)
    ScreenFrame(dock = if (!canDecide && !canPr) null else ({
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canDecide) {
                Btn("Отклонить", { vms.code.deny(id); nav.pop() }, Modifier.weight(1f))
                Btn("Применить", { vms.code.approve(id) }, Modifier.weight(1f), primary = true, enabled = allowsWrites)
            } else Btn("Создать PR", { vms.code.createPr(id) }, Modifier.weight(1f), primary = true, icon = HIcon.Branch)
        }
    })) {
        Header("Изменения", onBack = { nav.pop() }, subtitle = if (changes.isEmpty()) "нет изменений" else "${changes.size} файл(ов) · $total")
        Body {
            if (cur == null) { Emp("Агент пока ничего не менял", HIcon.Code); return@Body }
            if (changes.size > 1) Seg(changes.map { it.path }, cur.path, { file = it })
            Text(
                (if (cur.beforeText == null) "Новый файл · " else "") + changeLabel(cur.status) + " · +${cur.added} −${cur.removed}",
                style = H.t.small, color = if (cur.status == ChangeStatus.FAILED) c.warn else c.mu,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = if (changes.size > 1) 0.dp else 8.dp, bottom = 8.dp),
            )
            val lines = remember(cur.id) { numbered(TextDiff.lines(cur.beforeText, cur.afterText)) }
            val shape = RoundedCornerShape(HDim.card)
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().hCard(shape)) {
                Column(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = 320.dp).width(IntrinsicSize.Max)) {
                    lines.take(MAX_DIFF_LINES).forEach { l ->
                        val (bg, fg, mark) = when (l.kind) { DiffKind.ADD -> Triple(c.ab, c.at, '+'); DiffKind.DEL -> Triple(c.db, c.dt, '-'); else -> Triple(Color.Transparent, c.ink, ' ') }
                        Row(Modifier.fillMaxWidth().background(bg).padding(end = 12.dp)) {
                            Text(l.n.toString(), style = H.t.monoDiff, color = c.mu, textAlign = TextAlign.End, modifier = Modifier.width(44.dp).padding(end = 8.dp))
                            Text("$mark ${l.text}", style = H.t.monoDiff, color = fg, softWrap = false)
                        }
                    }
                    if (lines.size > MAX_DIFF_LINES) Text("… ещё ${lines.size - MAX_DIFF_LINES} строк", style = H.t.small, color = c.mu, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

private fun diffTotals(changes: List<SessionChangeEntity>): String =
    "+${changes.sumOf { it.added }} −${changes.sumOf { it.removed }}"
