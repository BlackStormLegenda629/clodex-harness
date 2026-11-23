package app.clodex.harness

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.clodex.harness.clodex.settings.AppearanceRepository
import app.clodex.harness.clodex.settings.AppearanceSettings
import app.clodex.harness.clodex.settings.AppearanceViewModel
import app.clodex.harness.clodex.theme.styleFor
import app.clodex.harness.clodex.ui.AppRoot
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

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
        val appearance = AppearanceViewModel(AppearanceRepository.of(this))
        setContent {
            val settings by appearance.state.collectAsStateWithLifecycle()
            val dark = styleFor(settings).dark
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            AppRoot(
                settings = settings,
                onSettingsChange = { next -> appearance.apply(next) },
            )
        }
    }
}

/** Applies every changed field of the appearance settings through the view model. */
fun AppearanceViewModel.apply(previous: AppearanceSettings, next: AppearanceSettings) {
    if (previous.accent != next.accent) setAccent(next.accent)
    if (previous.liquidGlass != next.liquidGlass) setLiquidGlass(next.liquidGlass)
    if (previous.design != next.design) setDesign(next.design)
    if (previous.font != next.font) setFont(next.font)
    if (previous.radius != next.radius) setRadius(next.radius)
    if (previous.themeMode != next.themeMode) setThemeMode(next.themeMode)
}

fun AppearanceViewModel.apply(next: AppearanceSettings) = apply(state.value, next)
