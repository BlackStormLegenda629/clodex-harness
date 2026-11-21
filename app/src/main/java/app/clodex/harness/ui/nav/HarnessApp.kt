package app.clodex.harness.ui.nav

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.clodex.harness.AppContainer
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.screens.*
import app.clodex.harness.ui.theme.Ease
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.LocalMotion
import app.clodex.harness.ui.theme.Rise
import app.clodex.harness.ui.vm.*
import app.clodex.harness.util.RelativeTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val vms = LocalVms.current
    val tab by vms.app.tab.collectAsStateWithLifecycle()
    val m = LocalMotion.current
    var text by rememberSaveable { mutableStateOf("") }
    val prefill: ((String) -> Unit)? = if (text.isBlank()) {
        { prompt -> if (text.isBlank()) text = prompt }
    } else null
    val wide = LocalConfiguration.current.screenWidthDp >= 800

    if (wide) {
        // Tablet / foldable: permanent sidebar + a single wide work area. No bottom tab bar.
        Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Sidebar(tab)
            Box(Modifier.width(1.dp).fillMaxHeight().background(H.c.line))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    targetState = tab, modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = { fadeIn(m.spec(240, Ease)) togetherWith fadeOut(m.spec(240, Ease)) }, label = "tab",
                ) { TabContent(it, prefill) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.widthIn(max = 1000.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { TabDock(tab, text, { text = it }) }
                }
            }
        }
    } else {
        ScreenFrame(dock = {
            TabDock(tab, text, { text = it })
            TabBar(AppTab.entries.map { Triple(it.label, it.icon, it == tab) }) { vms.app.setTab(AppTab.entries[it]) }
        }) {
            AnimatedContent(
                targetState = tab, modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { fadeIn(m.spec(240, Ease)) togetherWith fadeOut(m.spec(240, Ease)) }, label = "tab",
            ) { TabContent(it, prefill) }
        }
    }
}

@Composable
private fun TabContent(t: AppTab, onPrefill: ((String) -> Unit)?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1000.dp).fillMaxSize()) {
            when (t) {
                AppTab.CHAT -> { ChatHeader(); ChatBody(onPrefill) }
                AppTab.PROJ -> { ProjectsHeader(); ProjectsBody() }
                AppTab.CODE -> { CodeHeader(); CodeBody() }
                AppTab.ART -> { ArtifactsHeader(); ArtifactsBody() }
            }
        }
    }
}

@Composable
private fun TabDock(t: AppTab, text: String, onText: (String) -> Unit) {
    val vms = LocalVms.current
    val nav = LocalNav.current
    val attachments by vms.chat.attachments.collectAsStateWithLifecycle()
    if (t == AppTab.CHAT) {
        val s by vms.settings.settings.collectAsStateWithLifecycle()
        val offer by vms.chat.offer.collectAsStateWithLifecycle()
        offer?.let { o -> ProviderOfferBar(o, onSwitch = { vms.chat.acceptOffer() }, onDismiss = { vms.chat.dismissOffer() }, onOpen = { vms.chat.dismissOffer(); nav.settings("prov") }) }
        val approval by vms.chat.pendingToolApproval.collectAsStateWithLifecycle()
        val generating by vms.chat.generatingChatIds.collectAsStateWithLifecycle()
        val current by vms.chat.current.collectAsStateWithLifecycle()
        approval?.let { p -> ToolApprovalBar(p, onAllow = { vms.chat.resolveTool(true) }, onDeny = { vms.chat.resolveTool(false) }, onRemember = { vms.chat.resolveTool(true, remember = true) }) }
        Composer(
            text, onText, "Напишите ${s.activeProvider().name}…", s.providerId, s.activeProvider().name, s.model, s.effort,
            onPlus = { vms.app.open(SheetKind.Add) }, onModel = { vms.app.open(SheetKind.Model) },
            onSend = { if (vms.chat.send(text)) onText("") },
            attachments = attachments, onRemoveAttachment = { vms.chat.removeAttachment(it) },
            generating = current?.id in generating, onStop = { vms.chat.stop() },
        )
    } else when (t) {
        AppTab.PROJ -> ProjectsDock()
        AppTab.CODE -> CodeDock()
        AppTab.ART -> {}
        AppTab.CHAT -> {}
    }
}

/** Material You navigation item: 56dp pill with a tonal selected state. */
@Composable
private fun RailItem(label: String, icon: HIcon, selected: Boolean, onClick: () -> Unit) {
    val c = H.c
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (selected) c.ac.copy(alpha = 0.16f) else Color.Transparent)
            .hClick(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Ic(icon, tint = if (selected) c.ac else c.mu, size = 22.dp)
        Text(label, style = H.t.tab.copy(fontSize = 12.sp), color = if (selected) c.ac else c.mu, modifier = Modifier.weight(1f))
    }
}

/** Desktop-style navigation rail: brand, tabs, recent chats, settings. */
@Composable
private fun Sidebar(tab: AppTab) {
    val vms = LocalVms.current
    val nav = LocalNav.current
    val chats by vms.chat.chats.collectAsStateWithLifecycle()
    val current by vms.chat.current.collectAsStateWithLifecycle()
    val generating by vms.chat.generatingChatIds.collectAsStateWithLifecycle()
    val c = H.c
    Column(
        Modifier.width(300.dp).fillMaxHeight().background(c.s1)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(40.dp).height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.ac.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text("C", style = H.t.ctl, color = c.ac)
            }
            Column(Modifier.weight(1f)) {
                Text("Clodex", style = H.t.hd, color = c.ink)
                Text("Harness", style = H.t.small, color = c.mu)
            }
            IconBtn(HIcon.Plus, "Новый чат", { vms.chat.newChat { vms.app.setTab(AppTab.CHAT) } }, circle = true, tint = c.ac)
        }
        Spacer(10.dp)
        AppTab.entries.forEach { t ->
            RailItem(t.label, t.icon, t == tab) { vms.app.setTab(t) }
        }
        Spacer(12.dp)
        HorizontalDivider(color = c.line)
        Spacer(8.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("НЕДАВНИЕ ЧАТЫ", style = H.t.sg, color = c.mu, modifier = Modifier.weight(1f))
            IconBtn(HIcon.List, "Все чаты", { nav.push("chats") }, size = 34.dp)
        }
        LazyColumn(Modifier.weight(1f).padding(top = 4.dp)) {
            items(chats, key = { it.id }) { ch ->
                val on = ch.id == current?.id
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (on) c.ac.copy(alpha = 0.14f) else Color.Transparent)
                        .hClick { vms.chat.open(ch.id); vms.app.setTab(AppTab.CHAT) }.semantics { selected = on }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(ch.title, style = H.t.row14Med, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (ch.id in generating) LiveDot()
                        Text(chatStatus(ch.id in generating, RelativeTime.chat(ch.updatedAt)), style = H.t.small, color = c.mu, maxLines = 1)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = c.line)
            Spacer(8.dp)
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(c.s2)
                    .hClick { nav.settings("root") }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) { Ic(HIcon.Gear, tint = c.ac); Text("Настройки", style = H.t.tab.copy(fontSize = 12.sp), color = c.ac, modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Spacer(h: androidx.compose.ui.unit.Dp) { Box(Modifier.height(h)) }

@Composable
fun HarnessApp(container: AppContainer) {
    val factory = remember { VmFactory(container) }
    val appVm: AppViewModel = viewModel(factory = factory)
    val chatVm = container.chatVm
    val projectsVm: ProjectsViewModel = viewModel(factory = factory)
    val artifactsVm: ArtifactsViewModel = viewModel(factory = factory)
    val codeVm = container.codeVm
    val skillsVm: SkillsViewModel = viewModel(factory = factory)
    val connectorsVm: ConnectorsViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)
    val vms = remember(appVm, chatVm, projectsVm, artifactsVm, codeVm, skillsVm, connectorsVm, settingsVm) {
        Vms(appVm, chatVm, projectsVm, artifactsVm, codeVm, skillsVm, connectorsVm, settingsVm)
    }
    val navController = rememberNavController()
    val route by navController.currentBackStackEntryAsState()
    var dismissedUpdate by rememberSaveable { mutableStateOf(0L) }
    val nav = remember(navController) { Nav(navController, vms.app) }
    val m = LocalMotion.current
    val shift = with(LocalDensity.current) { 28.dp.roundToPx() }
    val sheet by vms.app.sheet.collectAsStateWithLifecycle()
    val toasts = container.toasts

    // Attachment pickers live here (not in the sheet) so their results survive the sheet closing.
    fun addUris(uris: List<Uri>) = uris.forEach { vms.chat.addAttachment(it.toString(), container.files.queryName(it) ?: "файл") }
    var pending by rememberSaveable { mutableStateOf<Uri?>(null) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { addUris(it) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addUris(it) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pending?.let { addUris(listOf(it)) }; pending = null }
    val vid = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok -> if (ok) pending?.let { addUris(listOf(it)) }; pending = null }
    val attach = remember {
        AttachActions(
            camera = { runCatching { val u = container.files.newCaptureUri(".jpg"); pending = u; cam.launch(u) }.onFailure { toasts.show("Камера недоступна") } },
            photo = { runCatching { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { toasts.show("Выбор фото недоступен") } },
            files = { runCatching { files.launch(arrayOf("*/*")) }.onFailure { toasts.show("Выбор файлов недоступен") } },
            video = { runCatching { val u = container.files.newCaptureUri(".mp4"); pending = u; vid.launch(u) }.onFailure { toasts.show("Камера недоступна") } },
        )
    }

    CompositionLocalProvider(LocalNav provides nav, LocalVms provides vms, LocalToasts provides toasts, LocalAttach provides attach) {
        Box(Modifier.fillMaxSize().background(H.c.bg)) {
            NavHost(
                navController, startDestination = Routes.MAIN, modifier = Modifier.fillMaxSize(),
                enterTransition = { slideInHorizontally(m.spec(320, Rise)) { shift } + fadeIn(m.spec(320, Rise)) },
                exitTransition = { fadeOut(m.spec(1)) },
                popEnterTransition = { slideInHorizontally(m.spec(320, Rise)) { -shift } + fadeIn(m.spec(320, Rise)) },
                popExitTransition = { fadeOut(m.spec(1)) },
            ) {
                composable(Routes.MAIN) { MainScreen() }
                composable(Routes.CHATS) { ChatsListScreen() }
                composable(Routes.SETTINGS, listOf(navArgument("id") { type = NavType.StringType })) {
                    SettingsScreen(it.arguments?.getString("id") ?: "root", onDismissUpdate = { dismissedUpdate = it })
                }
                composable(Routes.PROJECT, longArg()) { ProjectScreen(it.arguments?.getLong("id") ?: -1L) }
                composable(Routes.CONTEXT, longArg()) { ContextScreen(it.arguments?.getLong("id") ?: -1L) }
                composable(Routes.MEMORY, longArg()) { MemoryScreen(it.arguments?.getLong("id") ?: -1L) }
                composable(Routes.SESSION, longArg()) { SessionScreen(it.arguments?.getLong("id") ?: -1L) }
                composable(Routes.DIFF, longArg()) { DiffScreen(it.arguments?.getLong("id") ?: -1L) }
                composable(Routes.ARTIFACT, longArg()) { ArtifactScreen(it.arguments?.getLong("id") ?: -1L) }
                composable(Routes.BROWSE, listOf(navArgument("uri") { type = NavType.StringType; defaultValue = "" })) {
                    FilesScreen(it.arguments?.getString("uri").orEmpty())
                }
                composable(Routes.TERM) { TerminalScreen() }
            }
            SheetHost(sheet, onDismiss = { vms.app.close() }) { SheetContent(it) }
            ToastHost(toasts)
            UpdateControls(
                automatic = true,
                canShow = route?.destination?.route == Routes.MAIN && sheet == null,
                dismissedVersion = dismissedUpdate,
                onDismissVersion = { dismissedUpdate = it },
            )
        }
    }
}

private fun longArg() = listOf(navArgument("id") { type = NavType.LongType })
