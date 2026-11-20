package app.clodex.harness.ui.screens

import app.clodex.harness.ui.vm.SheetKind

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clodex.harness.data.ArtifactEntity
import app.clodex.harness.data.MessageEntity
import app.clodex.harness.data.Role
import app.clodex.harness.data.ThinkingCodec
import app.clodex.harness.data.agent.PendingToolCall
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalToasts
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HDim
import app.clodex.harness.ui.theme.scrollBlur
import app.clodex.harness.ui.vm.Attachment
import app.clodex.harness.ui.vm.AppTab
import app.clodex.harness.ui.vm.ProviderOffer
import app.clodex.harness.ui.vm.chatStatus
import app.clodex.harness.util.ArtifactIo
import app.clodex.harness.util.MdBlock
import app.clodex.harness.util.RelativeTime
import app.clodex.harness.util.highlight
import app.clodex.harness.util.inlineMd
import app.clodex.harness.util.parseBlocks
import app.clodex.harness.util.rememberDownloader
import app.clodex.harness.util.rememberSpeech

@Composable
fun GearBtn() {
    val nav = LocalNav.current
    IconBtn(HIcon.Gear, "Настройки", { nav.settings("root") }, circle = true)
}

@Composable
fun ChatHeader() {
    val nav = LocalNav.current; val vms = LocalVms.current
    val chat by vms.chat.current.collectAsStateWithLifecycle()
    Header(
        title = chat?.title ?: "Новый чат",
        leading = { IconBtn(HIcon.List, "Все чаты", { nav.push("chats") }) },
        actions = {
            IconBtn(HIcon.Plus, "Новый чат", { vms.chat.newChat() }, circle = true)
            GearBtn()
        },
    )
}

@Composable
fun androidx.compose.foundation.layout.ColumnScope.ChatBody(onPrefill: ((String) -> Unit)? = null) {
    val vms = LocalVms.current; val nav = LocalNav.current
    val ctx = LocalContext.current; val toasts = LocalToasts.current
    val chat by vms.chat.current.collectAsStateWithLifecycle()
    val messages by vms.chat.messages.collectAsStateWithLifecycle()
    val draft by vms.chat.draft.collectAsStateWithLifecycle()
    val live by vms.chat.thinking.collectAsStateWithLifecycle()
    val artifacts by vms.chat.artifacts.collectAsStateWithLifecycle()
    val connectors by vms.connectors.connectors.collectAsStateWithLifecycle()
    val configs by vms.connectors.configs.collectAsStateWithLifecycle()
    val usableConnectors = remember(connectors, configs) {
        connectors.map { connector ->
            if (configs[connector.id]?.enabled == true) connector else connector.copy(status = app.clodex.harness.data.ConStatus.NEW)
        }
    }
    val promptByReply = remember(messages) {
        var prompt = ""
        buildMap {
            messages.forEach {
                if (it.role == Role.USER) prompt = it.text
                else if (it.role == Role.ASSISTANT || it.role == Role.ERROR) put(it.id, prompt)
            }
        }
    }
    var summary by remember { mutableStateOf<ThinkingPresentation?>(null) }
    var replyHtml by remember(chat?.id) { mutableStateOf<String?>(null) }
    replyHtml?.let { HtmlViewer("index.html", it) { replyHtml = null } }
    summary?.let { st -> ThinkingSummaryDialog(if (st.thinking) live?.takeIf { it.chatId == st.chatId } ?: st.copy(thinking = false) else st, onDismiss = { summary = null }) }
    val chatId = chat?.id ?: -1L
    val showDraft = draft?.chatId == chatId
    val total = messages.size + if (showDraft) 1 else 0
    val initialCount = remember(chatId) { messages.size }
    val state = rememberLazyListState()
    val download = rememberDownloader { r -> toasts.show(r.fold({ "Скачано: $it" }, { it.message ?: "Не удалось сохранить" })) }

    LaunchedEffect(chatId, total, draft?.text?.length) { if (total > 0) state.animateScrollToItem(total - 1) }

    if (messages.isEmpty() && !showDraft) {
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = if (H.studio) Alignment.Center else Alignment.TopStart,
        ) {
            if (H.studio) StudioWelcome(onPrefill)
            else Emp(title = "Чем помочь?", icon = HIcon.Chat)
        }
        return
    }
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth().scrollBlur(), state = state,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
            val art = m.artifactId?.let { id -> artifacts.firstOrNull { it.id == id } }
            Box(if (i >= initialCount - 2) Modifier.rise(0, 300) else Modifier) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MessageItem(m, art, onOpen = { nav.artifact(it.id) }, onDownload = { a -> download(a.name, a.content) }, onThinking = { summary = it }, onHtmlPreview = { replyHtml = it })
                    if (!showDraft || i != messages.lastIndex) promptByReply[m.id]?.let { ConnectorSuggestions(it, usableConnectors) }
                }
            }
        }
        if (showDraft) item(key = "draft") {
            Box(Modifier.rise(0, 300)) {
                val t = draft?.text.orEmpty()
                val th = live?.takeIf { it.chatId == chatId }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (th != null) ThinkingRow(th, onOpen = { summary = th }, expanded = summary?.thinking == true)
                    else if (t.isEmpty()) Text("…", style = H.t.msgAi, color = H.c.mu)
                    if (t.isNotEmpty()) AssistantBlocks(t) { replyHtml = it }
                    ConnectorSuggestions(messages.lastOrNull { it.role == Role.USER }?.text.orEmpty(), usableConnectors)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioWelcome(onPrefill: ((String) -> Unit)?) {
    val c = H.c
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconPlate(HIcon.Chat)
        Text("Чем помочь?", style = H.t.big, color = c.ink, textAlign = TextAlign.Center)
        if (onPrefill != null) {
            Text("Начните с шаблона или напишите свой запрос.", style = H.t.note, color = c.mu, textAlign = TextAlign.Center)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "Объяснить код" to "Объясни, как работает этот код, и разберись, где могут быть ошибки:\n",
                    "Кратко пересказать" to "Кратко перескажи текст и выдели главные мысли:\n",
                    "Составить план" to "Составь пошаговый план и предложи, с чего начать. Цель: ",
                ).forEach { (label, prompt) ->
                    Box(
                        Modifier.clip(shape).background(c.s1).border(1.dp, c.line, shape)
                            .hClick { onPrefill(prompt) }
                            .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = H.t.btn, color = c.ink, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

@Composable
private fun ConnectorSuggestions(prompt: String, connectors: List<app.clodex.harness.data.ConnectorEntity>) {
    val sheets = LocalVms.current.app
    val suggestions = remember(prompt) { app.clodex.harness.data.ConnectorSuggester.find(prompt) }
    suggestions.forEach { entry ->
        val connector = connectors.firstOrNull { it.id == entry.id } ?: return@forEach
        val connected = connector.status == app.clodex.harness.data.ConStatus.ON
        val shape = RoundedCornerShape(14.dp)
        Row(Modifier.fillMaxWidth().clip(shape).background(H.c.s1).border(1.dp, H.c.line, shape).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ConnectorMark(connector, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = H.t.bodyMed, color = H.c.ink)
                Text(app.clodex.harness.data.ConnectorCatalog.browserLimit(entry.id) ?: entry.description, style = H.t.small, color = H.c.mu, maxLines = 3)
            }
            Btn(if (connected) "Подключено" else "Подключить", { sheets.open(SheetKind.ConnectorDetail, entry.id) }, enabled = !connected)
        }
    }
}

/** Offer to continue on another provider after a quota / zero-balance error. */
@Composable
fun ProviderOfferBar(o: ProviderOffer, onSwitch: () -> Unit, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val c = H.c
    val shape = RoundedCornerShape(HDim.card)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(c.s1)
            .border(1.dp, c.warn.copy(alpha = 0.35f), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconPlate(HIcon.Wait, circle = true, size = 30.dp, iconSize = 16.dp, tint = c.warn, bg = c.wb)
            Text(o.title, style = H.t.row14Med, color = c.ink, modifier = Modifier.weight(1f))
            IconBtn(HIcon.X, "Закрыть", onDismiss, size = 30.dp)
        }
        Text(o.message, style = H.t.note, color = c.mu)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (o.providerId != null) Btn("На ${o.providerName}", onSwitch, Modifier.weight(1f), primary = true)
            Btn("Настроить", onOpen, Modifier.weight(1f))
        }
    }
}

/** A connector tool that may change data waits for the user's decision. */
@Composable
fun ToolApprovalBar(p: PendingToolCall, onAllow: () -> Unit, onDeny: () -> Unit, onRemember: () -> Unit) {
    val c = H.c
    val shape = RoundedCornerShape(HDim.card)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.s1).border(1.dp, c.line, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconPlate(HIcon.Plug, circle = true, size = 30.dp, iconSize = 16.dp)
            Column(Modifier.weight(1f)) {
                Text("Разрешить действие?", style = H.t.row14Med, color = c.ink)
                Text("${p.source} · ${p.tool}", style = H.t.small, color = c.mu, maxLines = 2)
            }
        }
        Text("Инструмент может изменить данные в подключённом сервисе.", style = H.t.note, color = c.mu)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Отклонить", onDeny, Modifier.weight(1f))
            Btn("Разрешить", onAllow, Modifier.weight(1f), primary = true)
        }
        if (p.permission != null) Btn("Разрешать этот инструмент в этом чате", onRemember, Modifier.fillMaxWidth())
    }
}

/** Accent dot next to a chat whose reply is still being generated. */
@Composable
fun LiveDot() {
    Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(H.c.ac))
}

@Composable
fun MessageItem(
    m: MessageEntity, artifact: ArtifactEntity?, onOpen: (ArtifactEntity) -> Unit, onDownload: (ArtifactEntity) -> Unit,
    onThinking: (ThinkingPresentation) -> Unit = {},
    onHtmlPreview: ((String) -> Unit)? = null,
) {
    val c = H.c; val t = H.t
    if (m.role == Role.USER) {
        val wide = LocalConfiguration.current.screenWidthDp >= 600
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    m.text, style = t.msgUser, color = c.bg,
                    modifier = Modifier.widthIn(max = if (wide) 440.dp else 290.dp)
                        .clip(RoundedCornerShape(HDim.userMsgBig, HDim.userMsgBig, HDim.userMsgSmall, HDim.userMsgBig))
                        .background(c.ink).padding(horizontal = 16.dp, vertical = 12.dp),
                )
                if (m.attachments.isNotBlank()) Text(m.attachments.split('\n').joinToString(" · "), style = t.small, color = c.mu)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (m.role == Role.ASSISTANT && (m.thinkingSeconds > 0 || m.thinkingSteps.isNotBlank())) {
                val st = remember(m.id) { ThinkingPresentation(m.chatId, thinking = false, seconds = m.thinkingSeconds, steps = ThinkingCodec.decode(m.thinkingSteps)) }
                ThinkingRow(st, onOpen = { onThinking(st) })
            }
            if (m.role == Role.ERROR) Text(m.text, style = t.msgAi, color = c.warn) else AssistantBlocks(m.text, onHtmlPreview.takeUnless { artifact?.kind == "HTML" })
            if (artifact != null) ArtifactCard(artifact, onOpen = { onOpen(artifact) }, onDownload = { onDownload(artifact) })
        }
    }
}

@Composable
fun AssistantBlocks(text: String, onHtmlPreview: ((String) -> Unit)? = null) {
    val c = H.c; val t = H.t
    val link = SpanStyle(color = c.ac, textDecoration = TextDecoration.Underline)
    val code = SpanStyle(fontFamily = t.mono, fontSize = 13.sp, background = c.s2)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        parseBlocks(text).forEach { b ->
            when (b) {
                is MdBlock.Text -> Text(inlineMd(b.text, link, code), style = t.msgAi, color = c.ink)
                is MdBlock.Code -> {
                    if (onHtmlPreview != null && isHtmlReply(b.lang, b.body)) {
                        val shape = RoundedCornerShape(HDim.card)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(c.s1).border(1.dp, c.line, shape)
                                .hClick(onClick = { onHtmlPreview(b.body) }).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconPlate(HIcon.Code)
                            Column(Modifier.weight(1f)) {
                                Text("index.html", style = t.bodyMed, color = c.ink)
                                Text("HTML · Открыть предпросмотр", style = t.small, color = c.mu)
                            }
                            Ic(HIcon.Ch, tint = c.mu)
                        }
                    } else {
                        CodeBlock(b.lang, b.body)
                    }
                }
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, body: String) {
    val c = H.c; val t = H.t
    val ctx = LocalContext.current; val toasts = LocalToasts.current
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.s1).border(1.dp, c.line, shape)) {
        Row(Modifier.fillMaxWidth().background(c.s2).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(lang.ifEmpty { "код" }, style = t.mono12, color = c.mu)
            Row(
                Modifier.height(36.dp).hClick(onClick = { ArtifactIo.copy(ctx, body); toasts.show("Код скопирован") }).clip(RoundedCornerShape(18.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) { Ic(HIcon.Copy, tint = c.mu, size = 16.dp); Text("Копировать", style = t.small, color = c.mu) }
        }
        Text(
            highlight(body, c), style = t.monoCode, color = c.ink, softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun ArtifactCard(a: ArtifactEntity, onOpen: () -> Unit, onDownload: () -> Unit) {
    val c = H.c
    val shape = RoundedCornerShape(HDim.card)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.s1).border(1.dp, c.line, shape).padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.weight(1f).hClick(onClick = onOpen), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconPlate(HIcon.File)
            Titles(a.name, a.kind + " · лежит в Артефактах")
        }
        IconBtn(HIcon.Dl, "Скачать ${a.name}", onDownload, circle = true)
    }
}

/** Composer (.cmp): auto-growing field, +, model chip with effort, mic, send. */
@Composable
fun Composer(
    value: String, onValue: (String) -> Unit, placeholder: String, providerId: String, providerName: String, model: String, effort: String,
    onPlus: () -> Unit, onModel: () -> Unit, onSend: () -> Unit,
    attachments: List<Attachment> = emptyList(), onRemoveAttachment: (Int) -> Unit = {},
    generating: Boolean = false, onStop: () -> Unit = {},
) {
    val c = H.c; val t = H.t
    val toasts = LocalToasts.current
    val cur by rememberUpdatedState(value)
    val speech = rememberSpeech(
        onText = { onValue(if (cur.isBlank()) it else cur.trimEnd() + " " + it) },
        onError = { toasts.show(it) },
    )
    val shape = RoundedCornerShape(HDim.composer)
    val toolShape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.s1).border(1.dp, c.line, shape).padding(horizontal = 8.dp, vertical = 8.dp)) {
        if (attachments.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                attachments.forEachIndexed { i, a ->
                    Row(
                        Modifier.clip(toolShape).background(c.s2).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Ic(HIcon.File, size = 16.dp, tint = c.mu)
                        Text(a.name, style = t.row14, color = c.ink, maxLines = 1, modifier = Modifier.widthIn(max = 160.dp))
                        IconBtn(HIcon.X, "Убрать ${a.name}", { onRemoveAttachment(i) }, size = 28.dp)
                    }
                }
            }
        }
        BasicTextField(
            value, onValue, textStyle = t.body.copy(color = c.ink), cursorBrush = SolidColor(c.ac), maxLines = 6,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            decorationBox = { inner -> Box(Modifier.heightIn(min = 36.dp), contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(placeholder, style = t.body, color = c.mu); inner() } },
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconBtn(HIcon.Plus, "Добавить", onPlus, tint = c.mu)
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.height(44.dp).clip(toolShape).background(c.s2).hClick(onClick = onModel).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ProviderMark(providerId, providerName, 22.dp, model = model)
                    Text(model, style = t.row14Med, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(effort, style = t.small, color = c.mu, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 56.dp))
                    Ic(HIcon.Ch, tint = c.mu, size = 12.dp)
                }
            }
            IconBtn(HIcon.Mic, if (speech.listening) "Остановить запись" else "Голос", speech.toggle, circle = speech.listening, tint = if (speech.listening) c.ac else c.mu)
            if (generating) IconBtn(HIcon.X, "Остановить ответ", onStop, send = true)
            else IconBtn(HIcon.Up, "Отправить", onSend, send = true)
        }
    }
}

@Composable
fun ChatsListScreen() {
    val nav = LocalNav.current; val vms = LocalVms.current
    val chats by vms.chat.chats.collectAsStateWithLifecycle()
    val current by vms.chat.current.collectAsStateWithLifecycle()
    val generating by vms.chat.generatingChatIds.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    val list = chats.filter { it.title.lowercase().contains(q.trim().lowercase()) }
    ScreenFrame {
        Header("Чаты", onBack = { nav.pop() }, actions = { IconBtn(HIcon.Plus, "Новый чат", { vms.chat.newChat { nav.toTab(AppTab.CHAT) } }, circle = true) })
        Body {
            Srch(q, { q = it }, "Поиск чатов")
            if (list.isEmpty()) Emp("Ничего не найдено", HIcon.Chat)
            list.forEach { ch ->
                It(onClick = { vms.chat.open(ch.id); nav.toTab(AppTab.CHAT) }) {
                    IconPlate(HIcon.Chat)
                    Titles(ch.title, chatStatus(ch.id in generating, RelativeTime.chat(ch.updatedAt)))
                    if (ch.id in generating) LiveDot()
                    if (ch.id == current?.id) Ic(HIcon.Ck, tint = H.c.ac)
                }
            }
        }
    }
}
