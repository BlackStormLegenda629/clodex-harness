package app.clodex.harness.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.text.format.Formatter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.clodex.harness.ui.components.GrpOf
import app.clodex.harness.ui.components.Li
import app.clodex.harness.ui.components.SheetNote
import app.clodex.harness.ui.components.Titles
import app.clodex.harness.ui.components.TransferProgress
import app.clodex.harness.ui.theme.LocalProgressStyle
import app.clodex.harness.updates.GitHubUpdates
import app.clodex.harness.updates.ReleaseOffer
import app.clodex.harness.updates.UnverifiedApkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun UpdateControls(
    automatic: Boolean = false,
    canShow: Boolean = true,
    dismissedVersion: Long,
    onDismissVersion: (Long) -> Unit,
) {
    if (!GitHubUpdates.enabled) {
        if (!automatic) SheetNote("В этой сборке не настроен канал GitHub Releases.", Modifier.padding(horizontal = 16.dp))
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var offer by remember { mutableStateOf<ReleaseOffer?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var permissionHelp by remember { mutableStateOf(false) }
    var downloaded by remember { mutableLongStateOf(0L) }
    val progressStyle = LocalProgressStyle.current

    LaunchedEffect(automatic) {
        if (!automatic) return@LaunchedEffect
        try {
            offer = GitHubUpdates.check(context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed background check must not interrupt normal app use.
        }
    }

    if (!automatic) GrpOf({
        Li(onClick = if (busy || offer != null) null else ({
            busy = true
            status = null
            scope.launch {
                try {
                    offer = GitHubUpdates.check(context)
                    if (offer == null) status = "Нет доступного обновления."
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    status = "Не удалось проверить обновления. Попробуйте снова."
                } finally {
                    busy = false
                }
            }
        })) { Titles(if (busy && offer == null) "Проверяем обновления…" else "Проверить обновления") }
    })
    if (!automatic && offer == null) {
        if (busy) TransferProgress(progressStyle, null, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        status?.let { SheetNote(it, Modifier.padding(horizontal = 16.dp)) }
    }

    if (!canShow) return
    offer?.takeUnless { automatic && it.versionCode == dismissedVersion }?.let { update ->
        fun later() {
            if (!busy) {
                onDismissVersion(update.versionCode)
                offer = null
                status = null
                permissionHelp = false
            }
        }
        AlertDialog(
            onDismissRequest = ::later,
            title = { Text("Что нового · Clodex (${update.versionCode})") },
            text = {
                Column {
                    Text(
                        update.notes.ifBlank { "Описание изменений не добавлено." },
                        Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                    )
                    if (busy) {
                        val finished = downloaded == update.size
                        val bytes = Formatter.formatShortFileSize(context, downloaded)
                        val total = Formatter.formatShortFileSize(context, update.size)
                        Text(if (finished) "Проверяем подпись APK…" else "Скачиваем APK: $bytes / $total", Modifier.padding(top = 12.dp))
                        TransferProgress(progressStyle, downloaded.toFloat() / update.size, Modifier.padding(top = 8.dp))
                    }
                    else status?.let { Text(it, Modifier.padding(top = 12.dp)) }
                }
            },
            dismissButton = { TextButton(onClick = ::later, enabled = !busy) { Text("Позже") } },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    when {
                        permissionHelp -> {
                            permissionHelp = false
                            try {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null)))
                            } catch (_: Exception) {
                                status = "Не удалось открыть системные настройки установки."
                            }
                        }
                        context.packageManager.canRequestPackageInstalls() -> {
                            permissionHelp = false
                            status = null
                            downloaded = 0L
                            busy = true
                            scope.launch {
                                try {
                                    var shownPercent = -1L
                                    GitHubUpdates.install(context, update, onProgress = { received ->
                                        val percent = received * 100 / update.size
                                        if (percent != shownPercent) {
                                            shownPercent = percent
                                            downloaded = received
                                        }
                                    }) { lifecycle.currentState == Lifecycle.State.RESUMED }
                                    onDismissVersion(update.versionCode)
                                    offer = null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: UnverifiedApkException) {
                                    status = "APK не прошёл проверку пакета, подписи или версии."
                                } catch (_: Exception) {
                                    status = "Не удалось открыть установку. Попробуйте снова."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                        else -> {
                            permissionHelp = true
                            status = "Разрешите Clodex установку неизвестных приложений в настройках Android. Затем вернитесь и снова нажмите «Установить»."
                        }
                    }
                }) { Text(if (permissionHelp) "Настройки Android" else "Установить") }
            },
        )
    }
}
