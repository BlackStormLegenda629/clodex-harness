package app.clodex.harness.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.clodex.harness.data.WorkspaceEntity
import app.clodex.harness.data.code.*
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.theme.H
import kotlinx.coroutines.CancellationException

@Composable
private fun CodeDialog(title: String, close: () -> Unit, back: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(back ?: close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth().fillMaxHeight(0.65f)) {
            Surface(color = H.c.bg, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    SheetHeader(title, close, back)
                    content()
                }
            }
        }
    }
}

@Composable
fun CodeFilesDialog(ws: WorkspaceEntity, ref: String, open: suspend (WorkspaceEntity, String) -> Result<CodeFiles>, close: () -> Unit) {
    var folderValue by rememberSaveable(ws.id, ref) { mutableStateOf("") }
    var selected by remember(ws.id, ref) { mutableStateOf<WorkspacePath?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val folder = WorkspacePath.parse(folderValue).getOrDefault(WorkspacePath.ROOT)
    val file = selected
    val back: (() -> Unit)? = when {
        file != null -> { { selected = null } }
        !folder.isRoot -> { { folderValue = folder.parent.value } }
        else -> null
    }
    val preview = file?.let {
        key(ws, ref, it, attempt) { loadCodeFiles { open(ws, ref).getOrThrow().preview(it).getOrThrow() } }
    }
    CodeDialog("Файлы", close, back) {
        Text(folder.value.ifEmpty { ws.name }, style = H.t.small, color = H.c.mu, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 8.dp))
        if (file != null) {
            when {
                preview == null -> Text("Загружаю HTML…", color = H.c.mu)
                preview.isFailure -> FileReadError(preview.exceptionOrNull()) { attempt++ }
            }
        }
        key(ws, ref, folder) {
            var listAttempt by remember { mutableIntStateOf(0) }
            key(listAttempt) {
                val result = loadCodeFiles { open(ws, ref).getOrThrow().list(folder).getOrThrow() }
                when {
                    result == null -> Text("Загружаю папку…", color = H.c.mu)
                    result.isFailure -> FileReadError(result.exceptionOrNull()) { listAttempt++ }
                    result.getOrThrow().isEmpty() -> Emp("Папка пуста", HIcon.File)
                    else -> LazyColumn(Modifier.weight(1f)) {
                        items(result.getOrThrow(), key = { it.path.value }) { entry ->
                            val html = !entry.dir && isHtmlFile(entry.path)
                            val click: (() -> Unit)? = when {
                                entry.dir -> { { folderValue = entry.path.value } }
                                html -> { { selected = entry.path } }
                                else -> null
                            }
                            Li(onClick = click) {
                                Ic(if (entry.dir) HIcon.Folder else HIcon.File, tint = H.c.mu)
                                Text(entry.path.name, color = H.c.ink, style = H.t.body, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (entry.dir || html) Text(if (html) "Просмотр" else "›", color = H.c.ac, style = H.t.small)
                            }
                        }
                        if (result.getOrThrow().size >= WorkspaceLimits.MAX_LIST) item {
                            SheetNote("Показано до ${WorkspaceLimits.MAX_LIST} элементов. В папке могут быть другие файлы и папки.")
                        }
                    }
                }
            }
        }
    }
    if (file != null && preview?.isSuccess == true) {
        HtmlViewer(file.name, preview.getOrThrow()) { selected = null }
    }
}

@Composable
private fun <T> loadCodeFiles(load: suspend () -> T): Result<T>? = produceState<Result<T>?>(null) {
    value = try { Result.success(load()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
}.value

@Composable
private fun FileReadError(error: Throwable?, retry: () -> Unit) {
    Text((error as? WorkspaceException)?.message ?: "Не удалось прочитать рабочую область", color = H.c.warn)
    Btn("Повторить", retry)
}
