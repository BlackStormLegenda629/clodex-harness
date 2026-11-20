package app.clodex.harness.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.theme.Ease
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HDim
import app.clodex.harness.ui.theme.LocalMotion
import kotlinx.coroutines.delay

data class ThinkingPresentation(
    val chatId: Long? = null,
    val thinking: Boolean,
    val startedAt: Long? = null,
    val seconds: Int? = null,
    val steps: List<String> = emptyList(),
) {
    val showDots: Boolean get() = thinking
}

fun thinkingLabel(state: ThinkingPresentation, now: Long? = null): String =
    (if (state.thinking) state.startedAt?.let { start -> now?.let { ((it - start) / 1000).coerceAtLeast(0) } }
    else state.seconds?.takeIf { it > 0 }?.toLong())?.let { "Ход рассуждений · $it с" }
        ?: "Ход рассуждений"

@Composable
private fun ThinkingDots() {
    val motion = LocalMotion.current
    val phase = if (motion.enabled) rememberInfiniteTransition(label = "thinkingDots").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.LinearEasing)), label = "wave",
    ).value else 0f
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val lift = if (motion.enabled) ((1 + kotlin.math.sin((phase - index * 0.18) * 2 * Math.PI)) / 2).toFloat() else 0f
            Box(
                Modifier.size(6.dp).clip(CircleShape).background(H.c.ac)
                    .graphicsLayer { alpha = if (motion.enabled) 0.35f + 0.65f * lift else 1f; translationY = -3.dp.toPx() * lift },
            )
        }
    }
}

@Composable
fun ThinkingRow(state: ThinkingPresentation, onOpen: () -> Unit, modifier: Modifier = Modifier, expanded: Boolean = false) {
    val motion = LocalMotion.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, motion.spec(200, Ease), label = "thinkingChevron")
    var now by remember(state.startedAt) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.startedAt, state.thinking) {
        while (state.thinking && state.startedAt != null) { now = System.currentTimeMillis(); delay(250) }
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).hClick(onClick = onOpen)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.showDots) ThinkingDots()
        Column(Modifier.weight(1f)) {
            Text(thinkingLabel(state, now), style = H.t.small, color = H.c.mu)
            if (state.thinking) {
                val step = state.steps.lastOrNull().orEmpty()
                if (motion.enabled) AnimatedContent(step, label = "thinkingStep") { text ->
                    Text(text, style = H.t.small, color = H.c.mu, maxLines = 1)
                } else Text(step, style = H.t.small, color = H.c.mu, maxLines = 1)
            }
        }
        Ic(HIcon.Ch, Modifier.graphicsLayer { rotationZ = rotation }, tint = H.c.mu, size = 14.dp)
    }
}

@Composable
private fun SummaryStep(text: String, current: Boolean) {
    val motion = LocalMotion.current
    var visible by remember(text) { mutableStateOf(false) }
    LaunchedEffect(text) { visible = true }
    val transition = rememberInfiniteTransition(label = "summaryMarker")
    val markerAlpha by transition.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(800, easing = Ease), RepeatMode.Reverse), label = "markerAlpha",
    )
    AnimatedVisibility(
        visible,
        enter = fadeIn(motion.spec(220, Ease)) + slideInVertically(motion.spec(220, Ease)) { 6 },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier.padding(top = 7.dp).size(7.dp).clip(CircleShape).background(if (current) H.c.ac else H.c.mu)
                    .graphicsLayer { alpha = if (current && motion.enabled) markerAlpha else 1f },
            )
            Text(
                text,
                color = H.c.ink,
                style = H.t.msgAi,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ThinkingSummaryDialog(state: ThinkingPresentation, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val c = H.c
        val shape = RoundedCornerShape(HDim.composer)
        Column(
            Modifier.fillMaxWidth(0.9f).widthIn(max = 620.dp).heightIn(max = 680.dp)
                .clip(shape).background(c.s1)
                .border(1.dp, c.line, shape)
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                IconBtn(
                    HIcon.X, "Закрыть сводку", onDismiss,
                    modifier = Modifier.align(Alignment.CenterStart), size = 40.dp, tint = c.mu,
                )
                Text(
                    "Сводка", color = c.ink, style = H.t.sheetTitle,
                )
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.steps.isEmpty()) {
                    Text(
                        "Сводка пока пуста", color = c.mu, style = H.t.row14,
                        modifier = Modifier.padding(vertical = 22.dp),
                    )
                } else {
                    state.steps.forEachIndexed { index, step ->
                        SummaryStep(step, current = state.thinking && index == state.steps.lastIndex)
                    }
                }
            }
        }
    }
}
