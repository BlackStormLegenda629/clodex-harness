package app.clodex.harness.util

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object ArtifactIo {
    fun mime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "md" -> "text/markdown"
        "py" -> "text/x-python"
        else -> "text/plain"
    }

    fun copy(ctx: Context, text: String, label: String = "Clodex") {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** API 29+: MediaStore.Downloads. API 26–28: public Downloads dir (needs WRITE_EXTERNAL_STORAGE, see needsLegacyPermission). */
    suspend fun saveToDownloads(ctx: Context, name: String, text: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime(name))
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: error("MediaStore отказал")
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("Не удалось открыть файл")
                ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                name
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
                File(dir, name).writeText(text)
                name
            }
        }
    }

    fun needsLegacyPermission(ctx: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    fun share(ctx: Context, name: String, text: String) {
        val root = File(ctx.cacheDir, "shared").apply { mkdirs() }
        val dir = kotlin.io.path.createTempDirectory(root.toPath(), "export-").toFile()
        val leaf = name.substringAfterLast('/').substringAfterLast('\\').takeUnless { it.isBlank() || it == "." || it == ".." } ?: "artifact"
        val f = File(dir, leaf).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime(name)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(ctx: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Save with the legacy-permission dance on API < 29. [onDone] gets the saved file name or an error message. */
@Composable
fun rememberDownloader(onDone: (Result<String>) -> Unit): (name: String, text: String) -> Unit {
    val ctx = LocalContext.current
    val done by rememberUpdatedState(onDone)
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val p = pending; pending = null
        if (granted && p != null) scope.launch { done(ArtifactIo.saveToDownloads(ctx, p.first, p.second)) }
        else done(Result.failure(IllegalStateException("Нужно разрешение на запись в Загрузки")))
    }
    return { name, text ->
        if (ArtifactIo.needsLegacyPermission(ctx)) { pending = name to text; launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
        else scope.launch { done(ArtifactIo.saveToDownloads(ctx, name, text)) }
    }
}

// ------------------------------------------------------------------ speech

@Stable
class SpeechController(val listening: Boolean, val toggle: () -> Unit)

/** Mic button backing: SpeechRecognizer, recognized text goes to [onText]. Handles RECORD_AUDIO and "no recognizer". */
@Composable
fun rememberSpeech(onText: (String) -> Unit, onError: (String) -> Unit): SpeechController {
    val ctx = LocalContext.current
    val text by rememberUpdatedState(onText)
    val err by rememberUpdatedState(onError)
    var listening by remember { mutableStateOf(false) }
    val recognizer = remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { err("Распознавание речи недоступно на этом устройстве"); return }
        val r = recognizer.value ?: SpeechRecognizer.createSpeechRecognizer(ctx).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) {
                    listening = false
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) err("Не удалось распознать речь")
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text(it) }
                }
            })
            recognizer.value = sr
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
        listening = true
        r.startListening(intent)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) start() else err("Нужно разрешение на микрофон")
    }
    DisposableEffect(Unit) { onDispose { recognizer.value?.destroy(); recognizer.value = null } }

    return SpeechController(listening) {
        if (listening) { recognizer.value?.stopListening(); listening = false }
        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
}
