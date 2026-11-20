// Scroll smoothing adapted from AndroidHarness, Copyright (c) 2026 Sanuu7 (MIT).
// See licenses/androidharness-MIT.txt.
package app.clodex.harness.ui.theme

import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

internal class ScrollMotion {
    private var velocity = 0f

    fun advance(distanceDp: Float, seconds: Float): Float {
        val dt = seconds.coerceIn(1f / 240f, 1f / 24f)
        val sample = abs(distanceDp) / dt
        val response = if (sample > velocity) .022f else .085f
        velocity += (sample - velocity) * (1f - exp(-dt / response))
        return ((velocity - 260f) * .0068f).coerceIn(0f, 8f)
    }
}

@Composable
internal fun Modifier.scrollBlur(): Modifier {
    if (Build.VERSION.SDK_INT < 31 || !LocalScrollBlur.current || !LocalMotion.current.enabled) return this
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val blur = remember(density, scope) {
        object : NestedScrollConnection {
            val radius = mutableFloatStateOf(0f)
            private var cachedRadius = -1f
            private var cachedEffect: androidx.compose.ui.graphics.RenderEffect? = null
            var distance = 0f
            var job: Job? = null

            fun effect(): androidx.compose.ui.graphics.RenderEffect? {
                val r = radius.floatValue
                if (r <= 0f) return null
                if (r != cachedRadius) {
                    cachedEffect = RenderEffect.createBlurEffect(0f, r, Shader.TileMode.CLAMP).asComposeRenderEffect()
                    cachedRadius = r
                }
                return cachedEffect
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y == 0f || !consumed.y.isFinite()) return Offset.Zero
                distance += consumed.y / density
                if (job?.isActive != true) job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val motion = ScrollMotion()
                    var last = withFrameNanos { it }
                    var idle = 0
                    while (idle < 3) {
                        val now = withFrameNanos { it }
                        val moved = distance
                        distance = 0f
                        val next = motion.advance(moved, (now - last) / 1_000_000_000f)
                        last = now
                        radius.floatValue = floor(next * density * 2) / 2
                        idle = if (radius.floatValue == 0f && moved == 0f) idle + 1 else 0
                    }
                }
                return Offset.Zero
            }
        }
    }
    DisposableEffect(blur) { onDispose { blur.job?.cancel(); blur.distance = 0f; blur.radius.floatValue = 0f } }
    return nestedScroll(blur).graphicsLayer { renderEffect = blur.effect() }
}
