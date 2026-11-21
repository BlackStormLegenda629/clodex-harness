package app.clodex.harness.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.theme.Ease
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HDim
import app.clodex.harness.ui.theme.LocalMotion
import app.clodex.harness.ui.theme.SheetEase
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Header area of a sheet gets this so it can be dragged down like the rest of the surface. */
val LocalSheetDrag = compositionLocalOf<Modifier> { Modifier }

/**
 * ModalBottomSheet behaviour with the prototype's timings: slide up 340 ms (.2,.8,.2,1), scrim fade 200 ms,
 * close 250 ms, swipe down to dismiss, system back closes. Switching between sheets replays the slide.
 */
@Composable
fun <T : Any> SheetHost(sheet: T?, onDismiss: () -> Unit, content: @Composable ColumnScope.(T) -> Unit) {
    val m = LocalMotion.current
    BackHandler(enabled = sheet != null) { onDismiss() }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(sheet != null, enter = fadeIn(m.spec(200, Ease)), exit = fadeOut(m.spec(250, Ease))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            )
        }
        AnimatedContent(
            targetState = sheet,
            modifier = Modifier.align(Alignment.BottomCenter),
            transitionSpec = {
                val t: ContentTransform = if (targetState != null)
                    slideInVertically(m.spec(340, SheetEase)) { it }.togetherWith(fadeOut(m.spec(1, Ease)))
                else EnterTransition.None.togetherWith(slideOutVertically(m.spec(250, SheetEase)) { it })
                t.apply { targetContentZIndex = if (targetState != null) 1f else 0f }.using(SizeTransform(clip = false) { _, _ -> snap() })
            },
            contentAlignment = Alignment.BottomCenter,
            label = "sheet",
        ) { s -> if (s != null) SheetSurface(onDismiss) { content(s) } }
    }
}

@Composable
private fun SheetSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = H.c
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    val threshold = with(density) { 120.dp.toPx() }
    var off by remember { mutableFloatStateOf(0f) }

    fun settle(velocityY: Float) {
        scope.launch {
            if (off > threshold || velocityY > 1800f) onDismiss()
            else animate(off, 0f, animationSpec = androidx.compose.animation.core.tween(200)) { v, _ -> off = v }
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && off > 0f) { val n = (off + available.y).coerceAtLeast(0f); val used = n - off; off = n; return Offset(0f, used) }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) { off += available.y; return Offset(0f, available.y) }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (off > 0f) { settle(available.y); return available }
                return Velocity.Zero
            }
        }
    }
    val dragState = rememberDraggableState { delta -> off = (off + delta).coerceAtLeast(0f) }
    val dragModifier = Modifier.draggable(dragState, Orientation.Vertical, onDragStopped = { v -> settle(v) })

    Column(
        Modifier.widthIn(max = 760.dp).fillMaxWidth().heightIn(max = maxH)
            .offset { IntOffset(0, off.roundToInt()) }
            .clip(RoundedCornerShape(topStart = HDim.sheetTop, topEnd = HDim.sheetTop))
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .nestedScroll(connection).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(32.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.mu.copy(alpha = 0.4f)).then(dragModifier))
        }
        CompositionLocalProvider(LocalSheetDrag provides dragModifier) { content() }
    }
}

/** .sht: 76dp, close (or back) button on the left, centered title. */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(76.dp).then(LocalSheetDrag.current), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(if (onBack != null) HIcon.Back else HIcon.X, if (onBack != null) "Назад" else "Закрыть", onBack ?: onClose)
        Text(title, style = H.t.sheetTitle, color = H.c.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(end = 44.dp), maxLines = 1)
    }
}

@Composable
fun SheetNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = H.t.note, color = H.c.mu, modifier = modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 14.dp))
}
