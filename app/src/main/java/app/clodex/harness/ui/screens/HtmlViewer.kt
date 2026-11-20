package app.clodex.harness.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.clodex.harness.ui.components.Ic
import app.clodex.harness.ui.components.IconBtn
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.theme.H
import app.clodex.harness.util.ArtifactIo
import app.clodex.harness.util.highlightHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal fun isHtmlReply(language: String, source: String): Boolean =
    source.isNotBlank() && language.lowercase() in setOf("html", "htm", "xhtml")

internal fun htmlExportName(name: String): String {
    val leaf = name.substringAfterLast('/').substringAfterLast('\\').trim().trimStart('.').take(120)
    return when {
        leaf.isBlank() -> "index.html"
        leaf.endsWith(".html", true) || leaf.endsWith(".htm", true) -> leaf
        else -> "$leaf.html"
    }
}

@Composable
fun HtmlViewer(name: String, html: String, close: () -> Unit) {
    val c = H.c
    val context = LocalContext.current
    val exportName = htmlExportName(name)
    val feedback = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun notify(message: String) { scope.launch { feedback.showSnackbar(message) } }
    val download = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) scope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) }
                        ?: throw IOException("Unable to open document")
                    "HTML сохранён"
                } catch (_: IOException) { "Не удалось сохранить HTML" }
                catch (_: SecurityException) { "Нет доступа для сохранения HTML" }
            }
            feedback.showSnackbar(message)
        }
    }
    var showCode by rememberSaveable(name) { mutableStateOf(false) }
    var expanded by rememberSaveable(name) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val docked = wide && !expanded
        if (docked) Spacer(Modifier.matchParentSize().clickable(onClickLabel = "Закрыть HTML", onClick = close))
        Column(Modifier.align(Alignment.CenterEnd).fillMaxWidth(if (docked) 0.52f else 1f).fillMaxHeight()
            .background(c.s1).systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(if (wide) 88.dp else 44.dp)) {
                    IconBtn(HIcon.X, "Закрыть HTML", close)
                }
                Text(name, Modifier.weight(1f).padding(horizontal = 4.dp), style = H.t.row14Semi, color = c.ink,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (wide) IconBtn(
                    if (docked) HIcon.Expand else HIcon.Collapse,
                    if (docked) "На весь экран" else "Вернуть боковую панель",
                    { expanded = !expanded; menu = false },
                )
                Box {
                    IconBtn(HIcon.Dots, "Действия с HTML", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.s2) {
                        DropdownMenuItem(text = { Text("Предпросмотр", color = c.ink) }, onClick = { showCode = false; menu = false },
                            leadingIcon = { Ic(HIcon.Eye, tint = c.mu) },
                            trailingIcon = { if (!showCode) Ic(HIcon.Ck, tint = c.ac) })
                        DropdownMenuItem(text = { Text("Код", color = c.ink) }, onClick = { showCode = true; menu = false },
                            leadingIcon = { Ic(HIcon.Code, tint = c.mu) },
                            trailingIcon = { if (showCode) Ic(HIcon.Ck, tint = c.ac) })
                        HorizontalDivider(color = c.line)
                        DropdownMenuItem(leadingIcon = { Ic(HIcon.Copy, tint = c.mu) }, text = { Text("Копировать", color = c.ink) }, onClick = {
                            menu = false
                            runCatching { ArtifactIo.copy(context, html, name) }
                                .fold({ notify("Код скопирован") }, { notify("Не удалось скопировать код") })
                        })
                        DropdownMenuItem(leadingIcon = { Ic(HIcon.Dl, tint = c.mu) }, text = { Text("Скачать", color = c.ink) }, onClick = {
                            menu = false
                            runCatching { download.launch(exportName) }
                                .onFailure { notify("Не удалось открыть окно сохранения") }
                        })
                        DropdownMenuItem(leadingIcon = { Ic(HIcon.Share, tint = c.mu) }, text = { Text("Поделиться", color = c.ink) }, onClick = {
                            menu = false
                            runCatching { ArtifactIo.share(context, exportName, html) }
                                .onFailure { notify("Не удалось поделиться HTML") }
                        })
                    }
                }
            }
            HorizontalDivider(color = c.line)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (showCode) {
                    val source = remember(html) { highlightHtml(html) }
                    Box(Modifier.fillMaxSize().background(Color(0xFF171717)).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                        SelectionContainer { Text(source, style = H.t.monoCode, color = Color(0xFFE4E0D9), softWrap = false) }
                    }
                } else {
                    // Match the browser default for HTML that doesn't specify its own background.
                    HtmlPreview(html, Modifier.fillMaxSize().background(Color.White))
                }
                SnackbarHost(feedback, Modifier.align(Alignment.BottomCenter))
            }
        }
        }
    }
}
