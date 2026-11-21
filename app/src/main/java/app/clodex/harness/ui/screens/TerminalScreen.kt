package app.clodex.harness.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalToasts
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private data class TLine(val text: String, val command: Boolean = false, val error: Boolean = false)

private suspend fun runShell(ctx: Context, cmd: String): String = withContext(Dispatchers.IO) {
    runCatching {
        val p = ProcessBuilder("/system/bin/sh", "-c", cmd)
            .redirectErrorStream(true)
            .directory(ctx.filesDir)
            .apply { environment()["HOME"] = ctx.filesDir.absolutePath }
            .start()
        runCatching { p.outputStream.close() }
        val out = withTimeoutOrNull(20_000) { p.inputStream.bufferedReader().use { it.readText() } }
        if (out == null) { p.destroyForcibly(); return@runCatching "Превышено время ожидания (20 с)" }
        val code = p.waitFor()
        out.take(60_000).ifBlank { "(команда выполнена, вывода нет)" } + if (code != 0) "\n[код $code]" else ""
    }.getOrElse { "Ошибка: ${it.message ?: "не удалось запустить sh"}" }
}

/** A real shell in the app sandbox: /system/bin/sh + toybox. No root, no SAF chdir, no git/node. */
@Composable
fun TerminalScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val lines = remember {
        mutableStateListOf(
            TLine("Harness Terminal · /system/bin/sh"),
            TLine("Рабочая папка: приватное хранилище приложения. Доступны ls, cat, echo, date, uname и другие toybox-команды."),
            TLine("Git, Node и Python в песочницу не входят. Папки по SAF открываются в разделе «Рабочее пространство»."),
            TLine(""),
        )
    }
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val state = rememberLazyListState()

    fun exec(cmd: String) {
        val c = cmd.trim()
        if (c.isEmpty() || busy) return
        lines += TLine("$ " + c, command = true)
        input = ""
        busy = true
        scope.launch {
            val out = runShell(ctx, c)
            lines += TLine(out)
            busy = false
        }
    }

    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.animateScrollToItem(lines.lastIndex) }

    val shape = RoundedCornerShape(HDim.card)
    ScreenFrame(dock = {
        Column(Modifier.fillMaxWidth().hCard(shape).padding(start = 16.dp, top = 10.dp, end = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$", style = H.t.mono13.copy(fontWeight = FontWeight.SemiBold), color = H.c.ac)
                BasicTextField(
                    input, { input = it }, singleLine = true, textStyle = H.t.mono13.copy(color = H.c.ink), cursorBrush = SolidColor(H.c.ink),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box { if (input.isEmpty()) Text(if (busy) "Выполняется…" else "Введите команду", style = H.t.mono13, color = H.c.mu); inner() } },
                )
                IconBtn(HIcon.Up, "Выполнить", { exec(input) }, send = true)
            }
        }
    }) {
        Header("Терминал", onBack = { nav.pop() }, subtitle = "/system/bin/sh · песочница приложения", actions = {
            IconBtn(HIcon.Copy, "Скопировать вывод", { toasts.show("Вывод скопирован") }, circle = true)
        })
        Body {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("pwd", "ls -la", "df -h", "date", "uname -a", "echo \$HOME", "ps -A | head").forEach { c ->
                    HChip(c, false, mono = true) { exec(c) }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).hCard(shape)) {
                LazyColumn(state = state, modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 520.dp).padding(16.dp)) {
                    itemsIndexed(lines) { _, line ->
                        Text(
                            line.text, style = H.t.monoCode,
                            color = when { line.command -> H.c.ac; line.error -> H.c.dt; else -> H.c.ink },
                            modifier = Modifier.padding(bottom = 6.dp).widthIn(min = 300.dp),
                        )
                    }
                }
            }
        }
    }
}
