package app.clodex.harness.util

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

internal fun speechText(text: String): String = text.take(4000).let {
    if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it
}

@Composable
internal fun rememberReplySpeech(chatId: Long, onError: () -> Unit): Pair<Long?, (Long, String) -> Unit> {
    val context = LocalContext.current.applicationContext
    val error = rememberUpdatedState(onError)
    val active = remember(chatId) { mutableStateOf<Long?>(null) }
    val speak = remember(chatId) { mutableStateOf<((Long, String) -> Unit)?>(null) }
    DisposableEffect(context, chatId) {
        val main = Handler(Looper.getMainLooper())
        var disposed = false
        var serial = 0L
        var current: String? = null
        val engine = TextToSpeech(context) { status ->
            main.post {
                if (!disposed && status != TextToSpeech.SUCCESS) error.value()
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = finish(utteranceId, false)
            @Deprecated("Android callback")
            override fun onError(utteranceId: String) = finish(utteranceId, true)
            private fun finish(id: String, failed: Boolean) {
                main.post {
                    if (!disposed && current == id) {
                        current = null
                        active.value = null
                        if (failed) error.value()
                    }
                }
            }
        })
        speak.value = { id, text ->
            current = null
            if (active.value == id) {
                active.value = null
                engine.stop()
            } else {
                val utterance = (++serial).toString()
                current = utterance
                active.value = id
                if (engine.speak(speechText(text), TextToSpeech.QUEUE_FLUSH, null, utterance) != TextToSpeech.SUCCESS) {
                    current = null
                    active.value = null
                    error.value()
                }
            }
        }
        onDispose {
            disposed = true
            speak.value = null
            engine.stop()
            engine.shutdown()
        }
    }
    return active.value to { id, text -> speak.value?.invoke(id, text) }
}
