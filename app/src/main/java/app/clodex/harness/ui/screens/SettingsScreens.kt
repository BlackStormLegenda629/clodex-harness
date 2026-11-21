package app.clodex.harness.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clodex.harness.data.AppStyle
import app.clodex.harness.data.ConStatus
import app.clodex.harness.data.K
import app.clodex.harness.data.ProgressStyle
import app.clodex.harness.data.agent.isOpenRouterSearchEndpoint
import app.clodex.harness.data.code.CodeMode
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.theme.Accents
import app.clodex.harness.ui.theme.CodeFontCatalog
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.UiFontCatalog
import app.clodex.harness.ui.theme.manualAccentAvailable
import app.clodex.harness.ui.vm.SheetKind
import app.clodex.harness.ui.vm.AppTab

@Composable
private fun ToggleRow(title: String, on: Boolean, sub: String? = null, icon: HIcon? = null, onToggle: () -> Unit) {
    Li(onClick = onToggle, role = Role.Switch) {
        if (icon != null) IconPlate(icon, circle = true, size = 34.dp, iconSize = 17.dp, tint = H.c.ac, bg = H.c.ac.copy(alpha = 0.12f))
        Titles(title, sub)
        Sw(on)
    }
}

@Composable
private fun RadioRow(title: String, selected: Boolean, sub: String? = null, onClick: () -> Unit) {
    Li(onClick = onClick, role = Role.RadioButton) { Titles(title, sub); if (selected) Ic(HIcon.Ck, tint = H.c.ac) }
}

@Composable
fun SettingsScreen(id: String, onDismissUpdate: (Long) -> Unit) {
    when (id) {
        "skills" -> SkillsScreen()
        "mcp" -> ConnectorsScreen()
        "schedule" -> ScheduleScreen()
        else -> SettingsPage(id, onDismissUpdate)
    }
}

@Composable
private fun SettingsPage(id: String, onDismissUpdate: (Long) -> Unit) {
    val nav = LocalNav.current; val vms = LocalVms.current; val ctx = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val s by vms.settings.settings.collectAsStateWithLifecycle()
    val workspaces by vms.code.workspaces.collectAsStateWithLifecycle()
    val folders = workspaces.filter { it.kind == "dev" }
    val skills by vms.skills.skills.collectAsStateWithLifecycle()
    val cons by vms.connectors.connectors.collectAsStateWithLifecycle()
    val sv = vms.settings
    val title = when (id) {
        "prov" -> "Провайдер"; "perm" -> "Разрешения"; "instr" -> "Инструкции"; "dirs" -> "Рабочие папки"
        "look" -> "Внешний вид"; "notif" -> "Уведомления"; "priv" -> "Данные и приватность"; "about" -> "О приложении"; "ghtoken" -> "Токен GitHub"
        "updates" -> "Обновления"; "setup" -> "Руководство по настройке"; "search" -> "Поиск в интернете"
        "coding" -> "Программирование"
        else -> "Настройки"
    }
    ScreenFrame {
        Header(title, onBack = { nav.pop() })
        Body {
            when (id) {
                "root" -> {
                    Sg("Подключения")
                    GrpOf(
                        { ActionRow("Провайдер", s.activeProvider().name, s.model, HIcon.Plug) { nav.settings("prov") } },
                        { ActionRow("Скиллы", "${skills.count { it.enabled }} вкл.", icon = HIcon.Pen) { nav.settings("skills") } },
                        { ActionRow("Коннекторы", "${cons.count { it.status == ConStatus.ON }} подключено", icon = HIcon.Globe) { nav.settings("mcp") } },
                        { ActionRow("Токен GitHub", if (s.githubToken.isBlank()) "не задан" else "задан", icon = HIcon.Branch) { nav.settings("ghtoken") } },
                    )
                    Sg("Агент")
                    GrpOf(
                        { ActionRow("Программирование", icon = HIcon.Code) { nav.settings("coding") } },
                        { ActionRow("Разрешения", icon = HIcon.Lock) { nav.settings("perm") } },
                        { ActionRow("Поиск в интернете", icon = HIcon.Globe) { nav.settings("search") } },
                        { ActionRow("Запланированные сообщения", icon = HIcon.Wait) { nav.settings("schedule") } },
                        { ActionRow("Инструкции", icon = HIcon.Docp) { nav.settings("instr") } },
                        { ActionRow("Рабочие папки", folders.size.toString(), icon = HIcon.Folder) { nav.settings("dirs") } },
                    )
                    Sg("Приложение")
                    GrpOf(
                        { ActionRow("Внешний вид", icon = HIcon.Img) { nav.settings("look") } },
                        { ActionRow("Уведомления", icon = HIcon.Wait) { nav.settings("notif") } },
                        { ActionRow("Данные и приватность", icon = HIcon.Trash) { nav.settings("priv") } },
                        { ActionRow("О приложении", icon = HIcon.Stack) { nav.settings("about") } },
                        { ActionRow("Обновления", icon = HIcon.Dl) { nav.settings("updates") } },
                        { ActionRow("Руководство по настройке", icon = HIcon.Docp) { nav.settings("setup") } },
                    )
                }
                "prov" -> {
                    Grp(s.providers) { p, _ ->
                        Li(onClick = { sv.setProvider(p.id) }) {
                            ProviderMark(p.id, p.name, 40.dp, p.url)
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(p.name, style = H.t.bodyMed, color = H.c.ink, maxLines = 1)
                                    if (!p.builtin) Bdg("Свой", BdgKind.Grey)
                                }
                                Text(
                                    when {
                                        p.key.isBlank() -> "Ключ не задан"
                                        p.builtin -> "Подключён"
                                        else -> p.url.ifBlank { "Свой сервер" }
                                    },
                                    style = H.t.small, color = if (p.key.isBlank()) H.c.warn else H.c.mu,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (s.providerId == p.id) Ic(HIcon.Ck, tint = H.c.ac)
                            IconBtn(HIcon.Pen, "Настроить ${p.name}", { vms.app.open(SheetKind.ProviderEdit, p.id) }, size = 36.dp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Btn("Добавить провайдера", { vms.app.open(SheetKind.ProviderEdit) }, Modifier.fillMaxWidth(), primary = true, icon = HIcon.Plus)
                    }
                    SheetNote("Активный провайдер отвечает в чатах. При лимите или нулевом балансе приложение предложит переключиться на другого. Свои серверы читают список моделей из /models.", Modifier.padding(horizontal = 20.dp))
                }
                "coding" -> ProgrammingScreen()
                "perm" -> {
                    val mode by vms.code.mode.collectAsStateWithLifecycle()
                    Sg("Режим новых сессий Code")
                    Grp(CodeMode.entries) { choice, _ ->
                        val description = when (choice) {
                            CodeMode.PLAN -> "Только чтение и план, без записи."
                            CodeMode.EDITS -> "Просмотр правок и подтверждение перед записью."
                            CodeMode.AUTO -> "Запись в папку или коммит в отдельную ветку GitHub без отдельного подтверждения."
                        }
                        RadioRow(choice.label, mode == choice.label, description) { vms.code.setMode(choice.label) }
                    }
                    SheetNote("Режим можно поменять в самой сессии. Code не запускает команды, сборки и тесты.", Modifier.padding(horizontal = 20.dp))
                    Sg("Коннекторы в чате")
                    SheetNote("Для вызовов, не помеченных коннектором как чтение, нужно подтверждение. Можно разрешить один вызов или запомнить конкретный инструмент только для этого чата. Смена подключения или инструмента требует нового согласия.", Modifier.padding(horizontal = 20.dp))
                    val configs by sv.mcpConfigs.collectAsStateWithLifecycle()
                    val chats by vms.chat.chats.collectAsStateWithLifecycle()
                    val grants = configs.values.flatMap { it.grants }
                    if (grants.isNotEmpty()) {
                        Sg("Сохранённые разрешения")
                        Grp(grants) { grant, _ -> Li {
                            Titles("${grant.permission.connectorId} · ${grant.permission.tool}", chats.firstOrNull { it.id == grant.chatId }?.title ?: "Чат ${grant.chatId}")
                            IconBtn(HIcon.X, "Отозвать разрешение", { sv.revokeMcp(grant) })
                        } }
                    }
                    GrpOf({ ActionRow("Управлять подключениями", icon = HIcon.Plug) { nav.settings("mcp") } })
                }
                "search" -> {
                    val provider = s.activeProvider()
                    val router = provider.openAi && isOpenRouterSearchEndpoint(provider.url)
                    val supported = !provider.openAi || router
                    val enabled = s.on("web")
                    Sg("Активный провайдер")
                    GrpOf({ ActionRow("Выбрать провайдера", provider.name, icon = HIcon.Plug) { nav.settings("prov") } })
                    Sg("Веб-поиск")
                    GrpOf({
                        if (supported || enabled) ToggleRow("Поиск в интернете", enabled, icon = HIcon.Globe) { sv.toggle("web") }
                        else Li { Titles("Поиск недоступен", "Выберите Anthropic или OpenRouter", ellipsis = false) }
                    })
                    val detail = when {
                        !supported && enabled -> "Поиск включён, но этот провайдер его не поддерживает. Выключите поиск или смените провайдера перед отправкой запроса."
                        !supported -> "Текущий провайдер не поддерживает веб-поиск в Clodex."
                        router -> "OpenRouter может брать дополнительную плату за веб-поиск."
                        else -> "Используется поиск Anthropic. Доступ зависит от модели и настроек API-аккаунта."
                    }
                    SheetNote("$detail Настройка общая для Chat и Code. Ссылки отображаются, если провайдер их вернул.", Modifier.padding(horizontal = 20.dp))
                }
                "instr" -> {
                    GrpOf({ LiField("Инструкции для агента", s.instructions, { sv.set(K.instructions, it) }, "Как отвечать и работать с кодом", singleLine = false, minLines = 5) })
                    SheetNote("Сохраняется автоматически и добавляется к каждому запросу.", Modifier.padding(horizontal = 20.dp))
                }
                "dirs" -> {
                    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                        if (uri != null) {
                            app.clodex.harness.util.Saf.takePermission(ctx, uri)
                            vms.code.addDeviceWorkspace("", uri.toString())
                        }
                    }
                    if (folders.isEmpty()) Emp("Папок пока нет", HIcon.Folder)
                    else Grp(folders) { folder, _ -> Li { Titles(folder.name) } }
                    SheetNote("Если папка, выбранная в старой версии, не видна, добавьте её заново.", Modifier.padding(horizontal = 20.dp))
                    Box(Modifier.fillMaxWidth().padding(20.dp)) { Btn("Добавить папку", { pick.launch(null) }, Modifier.fillMaxWidth(), icon = HIcon.Plus) }
                }
                "look" -> {
                    Sg("Стиль интерфейса")
                    val styleLabels = linkedMapOf(
                        "Классика" to AppStyle.Classic,
                        "Студия" to AppStyle.Studio,
                        "Material You" to AppStyle.Material,
                    )
                    val styleLabel = styleLabels.entries.firstOrNull { it.value == s.style }?.key ?: "Material You"
                    Seg(styleLabels.keys.toList(), styleLabel, { sv.set(K.style, styleLabels.getValue(it).name) }, pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp))
                    Sg("Тема")
                    Seg(listOf("Авто", "Светлая", "Тёмная"), s.theme, { sv.set(K.theme, it) }, pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp))
                    Sg("Цвет акцента")
                    if (manualAccentAvailable(s.style)) {
                        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Accents.forEach { a ->
                                val on = a.name == s.accent
                                val col = if (H.c.dark) a.dark else a.light
                                Box(
                                    Modifier.size(44.dp).hClick(0.92f, role = Role.RadioButton, label = a.name) { sv.set(K.accent, a.name) }
                                        .clip(CircleShape).background(col).border(if (on) 3.dp else 0.dp, if (on) H.c.ink else col, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { if (on) Ic(HIcon.Ck, tint = H.c.onAc) }
                            }
                        }
                    } else {
                        SheetNote("Цвета берутся из обоев на Android 12+. На более старых версиях используется палитра Material You.", Modifier.padding(horizontal = 20.dp))
                    }
                    Sg("Размер текста")
                    Seg(listOf("Мелкий", "Обычный", "Крупный"), s.textSize, { sv.set(K.textSize, it) }, pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp))
                    Sg("Шрифт интерфейса")
                    Grp(UiFontCatalog.names) { f, _ -> RadioRow(f, s.uiFont == f) { sv.set(K.uiFont, f) } }
                    Sg("Шрифт кода")
                    Grp(CodeFontCatalog.names) { f, _ -> RadioRow(f, s.codeFont == f) { sv.set(K.codeFont, f) } }
                    Sg("Индикатор прогресса")
                    Grp(ProgressStyle.entries) { style, _ ->
                        Column {
                            RadioRow(style.label, s.progressStyle == style) { sv.set(K.progressStyle, style.name) }
                            TransferProgress(style, 0.65f, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
                        }
                    }
                    Sg("Движение")
                    GrpOf({
                        ToggleRow("Размытие при прокрутке", s.scrollBlur, "Лёгкий эффект в списках на Android 12 и новее") {
                            sv.set(K.scrollBlur, !s.scrollBlur)
                        }
                    })
                }
                "notif" -> {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    GrpOf(
                    { ToggleRow("Нужно подтверждение", s.on("n1"), "Когда агент ждёт разрешения", HIcon.Wait) { sv.toggle("n1") } },
                    { ToggleRow("Сессия завершена", s.on("n2"), icon = HIcon.Ck) { sv.toggle("n2") } },
                    { ToggleRow("Ответ готов", s.on("n3"), "Для длинных ответов", HIcon.Chat) { sv.toggle("n3") } },
                    )
                }
                "priv" -> {
                    val busy by sv.backupBusy.collectAsStateWithLifecycle()
                    var pending by rememberSaveable { mutableStateOf<String?>(null) }
                    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
                        if (it != null) sv.exportBackup(it)
                    }
                    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                        pending = it?.toString()
                    }
                    pending?.let { uri ->
                        AlertDialog(
                            onDismissRequest = { pending = null },
                            title = { Text("Импортировать копию?") },
                            text = { Text("Данные будут добавлены к существующим. Повторный импорт создаст дубликаты. Задания из копии не запустятся.") },
                            confirmButton = { TextButton(onClick = { pending = null; sv.importBackup(Uri.parse(uri)) }) { Text("Импортировать") } },
                            dismissButton = { TextButton(onClick = { pending = null }) { Text("Отмена") } },
                        )
                    }
                    GrpOf(
                        { ActionRow("Общая память", icon = HIcon.Lock) { nav.push("memory/0") } },
                        { ActionRow("Экспорт данных", icon = HIcon.Dl) { if (!busy) save.launch("clodex-backup.json") } },
                        { ActionRow("Импорт данных", icon = HIcon.Docp) { if (!busy) load.launch(arrayOf("application/json", "text/plain")) } },
                    )
                    SheetNote("Копия не шифруется и может содержать личные данные. Токены, вложения и доступ к папкам не переносятся.", Modifier.padding(horizontal = 20.dp))
                    if (busy) SheetNote("Обработка резервной копии…", Modifier.padding(horizontal = 20.dp))
                    Box(Modifier.fillMaxWidth().padding(20.dp)) {
                        Btn("Удалить все данные", { if (!busy) vms.app.open(SheetKind.WipeConfirm) }, Modifier.fillMaxWidth(), danger = true, icon = HIcon.Trash)
                    }
                }
                "about" -> {
                    val ver = remember_versionName()
                    Sg("Clodex Harness")
                    SheetNote("Android-клиент для чата с ИИ и работы с кодом в локальных папках и репозиториях GitHub.", Modifier.padding(horizontal = 20.dp))
                    GrpOf(
                        { Li { Text("Версия", style = H.t.body, color = H.c.ink, modifier = Modifier.weight(1f)); Text(ver, style = H.t.row14, color = H.c.mu) } },
                        { ActionRow("Лицензии", icon = HIcon.Docp) { vms.app.open(SheetKind.Licenses) } },
                        { ActionRow("Обновления", icon = HIcon.Dl) { nav.settings("updates") } },
                    )
                }
                "updates" -> {
                    Sg("Установлена версия ${remember_versionName()}")
                    UpdateControls(dismissedVersion = 0L, onDismissVersion = onDismissUpdate)
                }
                "setup" -> {
                    Sg("Первый запуск")
                    GrpOf(
                        { ActionRow("1. Добавить провайдера", sub = "Укажите API-ключ и нажмите «Проверить и модели».", icon = HIcon.Plug) { nav.settings("prov") } },
                        { ActionRow("2. Выбрать модель", sub = "Выбор модели находится рядом с полем ввода.", icon = HIcon.Chat) {
                            nav.toTab(AppTab.CHAT)
                            vms.app.open(SheetKind.Model)
                        } },
                        { ActionRow("3. Настроить рабочую папку", sub = "Для Code можно использовать локальную папку или указать репозиторий GitHub в сессии.", icon = HIcon.Folder) { nav.settings("dirs") } },
                    )
                    Sg("Веб-поиск")
                    SheetNote("Выберите Anthropic или настройте OpenRouter как совместимый с OpenAI сервис. Затем в чате откройте меню «+» рядом с полем ввода и включите «Веб-поиск».", Modifier.padding(horizontal = 20.dp))
                    GrpOf({ ActionRow("Открыть настройки поиска", icon = HIcon.Globe) { nav.settings("search") } })
                }
                "ghtoken" -> {
                    GrpOf({ LiField("Персональный токен GitHub", s.githubToken, { sv.set(K.githubToken, it) }, "ghp_…", password = true) })
                    SheetNote("Нужен только для приватных репозиториев и чтобы не упираться в лимит API при установке скиллов. Публичные репозитории работают без токена.", Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun remember_versionName(): String {
    val ctx = LocalContext.current
    return remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0" }
}
