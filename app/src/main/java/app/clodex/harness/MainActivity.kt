package app.clodex.harness

import android.graphics.Color
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clodex.harness.ui.nav.HarnessApp
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HarnessTheme
import app.clodex.harness.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ClodexApp).container
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                container.setForeground(true)
                try { awaitCancellation() } finally { container.setForeground(false) }
            }
        }
        setContent {
            val ready by container.ready.collectAsStateWithLifecycle()
            val settings by container.settings.flow.collectAsStateWithLifecycle(container.initialSettings)
            val dark = isDarkTheme(settings)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            HarnessTheme(settings) {
                if (ready) HarnessApp(container) else Box(Modifier.fillMaxSize().background(H.c.bg))
            }
        }
    }
}
