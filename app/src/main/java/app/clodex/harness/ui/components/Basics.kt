package app.clodex.harness.ui.components

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.icons.HIcons
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HDim
import app.clodex.harness.ui.theme.LocalMotion
import app.clodex.harness.ui.theme.scrollBlur
import app.clodex.harness.ui.theme.Rise
import app.clodex.harness.ui.theme.Ease
import app.clodex.harness.util.ToastBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.min

@Composable
fun Ic(icon: HIcon, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 20.dp, stroke: Float = 2f) {
    Icon(HIcons.get(icon, stroke), contentDescription = null, modifier = modifier.size(size), tint = tint)
}

/** Press feedback of the prototype: scale .97 (rows/buttons) or .92 (icon buttons, chips), 140 ms. No ripple. */
fun Modifier.hClick(
    scale: Float = 0.97f, enabled: Boolean = true, role: Role? = Role.Button, label: String? = null, onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val m = LocalMotion.current
    val s by animateFloatAsState(if (pressed) scale else 1f, m.spec(140, Ease), label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, role = role, onClickLabel = label, onClick = onClick)
}

/** List rows rise 10dp + fade, 340 ms, delay 0/40/80/120/160 ms by index (first 5). */
fun Modifier.rise(index: Int = 0, ms: Int = 340): Modifier = composed {
    val m = LocalMotion.current
    val a = remember { Animatable(if (m.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (a.value < 1f) a.animateTo(1f, tween(ms, delayMillis = min(index, 4) * 40, easing = Rise)) }
    val d = LocalDensity.current.density
    this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 10f * d }
}

@Composable
fun IconBtn(
    icon: HIcon, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    circle: Boolean = false, send: Boolean = false, tint: Color? = null, size: Dp = HDim.iconBtn,
) {
    val c = H.c
    val haptic = LocalHapticFeedback.current
    val button = modifier.size(size).semantics { contentDescription = desc }
    val glyph: @Composable () -> Unit = {
        Ic(icon, tint = tint ?: LocalContentColor.current, size = if (size >= 48.dp) 24.dp else 20.dp)
    }
    // M3 enforces a 48 dp touch target; compact icon buttons keep their call-site size, as before.
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides if (size >= 48.dp) size else Dp.Unspecified,
    ) {
        when {
            send -> FilledIconButton(
                onClick = { haptic.performHapticFeedback(HapticPolicy.typeFor("send")); onClick() }, modifier = button,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.ac, contentColor = c.onAc),
                content = glyph,
            )
            circle -> FilledTonalIconButton(
                onClick = onClick, modifier = button,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.bb, contentColor = c.bt),
                content = glyph,
            )
            else -> IconButton(onClick = onClick, modifier = button, content = glyph)
        }
    }
}

internal object HapticPolicy {
    fun typeFor(action: String) = if (action == "toggle") HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun Header(
    title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, leading: (@Composable () -> Unit)? = null,
    big: Boolean = false, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {},
) {
    val c = H.c; val t = H.t
    androidx.compose.material3.TopAppBar(
        title = {
            Column {
                Text(title, style = if (big) t.big else t.hd, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = t.mono12, color = c.mu, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        modifier = modifier,
        navigationIcon = { if (onBack != null) IconBtn(HIcon.Back, "Назад", onBack) else leading?.invoke() },
        actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = c.s1, titleContentColor = c.ink),
    )
}

@Composable
fun Btn(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, danger: Boolean = false,
    icon: HIcon? = null, enabled: Boolean = true, container: Color? = null, tonal: Boolean = false,
) {
    val c = H.c
    val haptic = LocalHapticFeedback.current
    val confirm = { haptic.performHapticFeedback(HapticPolicy.typeFor("confirm")); onClick() }
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) Ic(icon)
        Text(text, style = H.t.btn, textAlign = TextAlign.Center, maxLines = 1)
    }
    when {
        container != null -> Button(
            onClick = onClick, modifier = modifier, enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = c.bg),
            content = content,
        )
        primary -> Button(onClick = confirm, modifier = modifier, enabled = enabled, content = content)
        // Tonal is the default emphasis in Material You; `tonal` stays as a call-site-compatible alias.
        tonal || !danger -> FilledTonalButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.bb, contentColor = c.bt),
            content = content,
        )
        danger -> OutlinedButton(
            onClick = confirm, modifier = modifier, enabled = enabled,
            border = BorderStroke(1.dp, c.error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.error),
            content = content,
        )
        else -> OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
fun HChip(text: String, on: Boolean, mono: Boolean = false, onClick: () -> Unit) {
    val c = H.c
    FilterChip(
        selected = on,
        onClick = onClick,
        label = { Text(text, style = if (mono) H.t.mono13 else H.t.chip13, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = c.mu,
            selectedContainerColor = c.bb,
            selectedLabelColor = c.bt,
        ),
        border = if (on) null else BorderStroke(1.dp, c.line),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Seg(
    options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier,
    pad: PaddingValues = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 12.dp),
) {
    // M3 chips carry a 48 dp touch target around the 32 dp container, so rows need no extra gap.
    FlowRow(modifier.fillMaxWidth().padding(pad), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEach { o -> HChip(o, o == selected) { onSelect(o) } }
    }
}

@Composable
fun Sw(on: Boolean, modifier: Modifier = Modifier, onToggle: (() -> Unit)? = null) {
    val change: ((Boolean) -> Unit)? = onToggle?.let { toggle -> { _: Boolean -> toggle() } }
    Switch(checked = on, onCheckedChange = change, modifier = modifier)
}

@Composable
fun Srch(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = H.c; val t = H.t
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 12.dp),
        textStyle = t.body.copy(color = c.ink),
        singleLine = true,
        placeholder = { Text(placeholder, style = t.body, color = c.mu) },
        leadingIcon = { Ic(HIcon.Search, tint = c.mu) },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.s2, unfocusedContainerColor = c.s2,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = c.ac, focusedTextColor = c.ink, unfocusedTextColor = c.ink,
        ),
    )
}

@Composable
fun Emp(text: String? = null, icon: HIcon? = null, title: String? = null, modifier: Modifier = Modifier) {
    val c = H.c; val t = H.t
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) Ic(icon, size = 56.dp, tint = c.mu, stroke = 1.5f)
        if (title != null) Text(title, style = t.empTitle, color = c.ink)
        if (text != null) Text(text, style = t.note.copy(fontSize = t.body.fontSize, lineHeight = t.body.fontSize * 1.5f), color = c.mu, textAlign = TextAlign.Center)
    }
}

enum class BdgKind { Blue, Grey, Warn }

@Composable
fun Bdg(text: String, kind: BdgKind = BdgKind.Blue, modifier: Modifier = Modifier) {
    val c = H.c
    val (bg, fg) = when (kind) { BdgKind.Blue -> c.bb to c.bt; BdgKind.Grey -> c.s2 to c.mu; BdgKind.Warn -> c.wb to c.warn }
    Text(text, style = H.t.badge, color = fg, modifier = modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
fun Fab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = H.c
    ExtendedFloatingActionButton(
        text = { Text(text, style = H.t.fab) },
        icon = { Ic(HIcon.Plus) },
        onClick = onClick,
        modifier = modifier,
        containerColor = c.ac,
        contentColor = c.onAc,
    )
}

/** Padding 8/12/12 and gap 10 — the prototype's .dock. */
@Composable
fun Dock(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun TabBar(items: List<Triple<String, HIcon, Boolean>>, onTab: (Int) -> Unit) {
    val c = H.c
    NavigationBar(
        containerColor = c.s1,
        tonalElevation = 3.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        items.forEachIndexed { i, (label, icon, on) ->
            NavigationBarItem(
                selected = on,
                onClick = { onTab(i) },
                icon = { Ic(icon, size = 24.dp) },
                label = { Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.bt,
                    selectedTextColor = c.bt,
                    indicatorColor = c.bb,
                    unselectedIconColor = c.mu,
                    unselectedTextColor = c.mu,
                ),
            )
        }
    }
}

/** Screen shell: status bar + (nav bars ∪ IME) insets, content capped at 1000dp on tablets, optional dock. */
@Composable
fun ScreenFrame(dock: (@Composable ColumnScope.() -> Unit)? = null, navigation: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = H.c
    androidx.compose.material3.Scaffold(
        containerColor = c.bg,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime)),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { navigation?.invoke() },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 1000.dp).fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth(), content = content)
                if (dock != null) Dock(dock)
            }
        }
    }
}

@Composable
fun ColumnScope.Body(state: ScrollState = rememberScrollState(), content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth().scrollBlur().verticalScroll(state), content = content)
}

/** Toast: rises 12dp + fades in over 250 ms, hides after 1800 ms. */
@Composable
fun ToastHost(bus: ToastBus, modifier: Modifier = Modifier) {
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bus) { bus.flow.collectLatest { msg = it; delay(1800); msg = null } }
    val c = H.c
    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 110.dp), contentAlignment = Alignment.BottomCenter) {
        msg?.let { text ->
            androidx.compose.runtime.key(text) {
                val m = LocalMotion.current
                val a = remember { Animatable(if (m.enabled) 0f else 1f) }
                LaunchedEffect(Unit) { if (a.value < 1f) a.animateTo(1f, tween(250, easing = Ease)) }
                val d = LocalDensity.current.density
                Box(Modifier.fillMaxWidth(0.8f), contentAlignment = Alignment.Center) {
                    Text(
                        text, style = H.t.row14, color = c.bg, textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 12f * d }
                            .clip(RoundedCornerShape(22.dp)).background(c.ink).padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
