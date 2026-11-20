package app.clodex.harness.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import app.clodex.harness.ui.nav.LocalToasts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

private const val GmailScope = "https://www.googleapis.com/auth/gmail.readonly"

@Composable
internal fun rememberGoogleGmailLogin(onAccess: (String) -> Unit): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val client = remember(context) { Identity.getAuthorizationClient(context) }
    val toasts = LocalToasts.current
    val deliver by rememberUpdatedState(onAccess)
    var starting by remember { mutableStateOf(false) }
    var waiting by rememberSaveable { mutableStateOf(false) }

    fun report(e: Exception) {
        val code = (e as? ApiException)?.statusCode
        toasts.show(if (code == 10) "Google не принял настройки OAuth для этой сборки Clodex"
        else "Не удалось войти через Google${code?.let { " (код $it)" }.orEmpty()}")
    }

    fun accept(result: AuthorizationResult) {
        val access = result.accessToken
        if (!access.isNullOrBlank() && GmailScope in result.grantedScopes) deliver(access)
        else toasts.show("Доступ к Gmail не предоставлен")
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        waiting = false
        if (result.resultCode == Activity.RESULT_OK) {
            try { accept(client.getAuthorizationResultFromIntent(result.data)) } catch (e: ApiException) { report(e) }
        }
    }
    LaunchedEffect(starting) {
        if (!starting) return@LaunchedEffect
        try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(GmailScope)))
                .setOptOutIncludingGrantedScopes(true)
                .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                .build()
            val pendingResult = client.authorize(request)
            val result = pendingResult.await()
            val pending = result.pendingIntent
            if (pending == null) accept(result)
            else {
                waiting = true
                launcher.launch(IntentSenderRequest.Builder(pending).build())
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            waiting = false
            report(e)
        } finally { starting = false }
    }
    return (starting || waiting) to { if (!starting && !waiting) starting = true }
}
