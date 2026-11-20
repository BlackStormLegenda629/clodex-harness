package app.clodex.harness.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.selected
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
    val bg = when { send -> c.ac; circle -> c.s2; else -> Color.Transparent }
    val fg = tint ?: if (send) c.onAc else c.ink
    Box(
        modifier.size(size).hClick(0.92f, label = desc, onClick = onClick).clip(CircleShape).background(bg)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Ic(icon, tint = fg) }
}

@Composable
fun Header(
    title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, leading: (@Composable () -> Unit)? = null,
    big: Boolean = false, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {},
) {
    val c = H.c; val t = H.t
    val hasLead = onBack != null || leading != null
    Row(
        modifier.fillMaxWidth().heightIn(min = HDim.header)
            .padding(start = if (hasLead) 8.dp else 16.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) IconBtn(HIcon.Back, "Назад", onBack) else leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = if (big) t.big else t.hd, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = t.mono12, color = c.mu, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        actions()
    }
}

@Composable
fun Btn(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, danger: Boolean = false,
    icon: HIcon? = null, enabled: Boolean = true, container: Color? = null,
) {
    val c = H.c
    val shape = RoundedCornerShape(HDim.pill)
    val bg = container ?: if (primary) c.ac else Color.Transparent
    val border = container ?: if (primary) c.ac else c.line
    val fg = when { container != null -> c.bg; primary -> c.onAc; danger -> c.dt; else -> c.ink }
    Row(
        modifier.height(46.dp).graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .hClick(enabled = enabled, onClick = onClick).clip(shape).background(bg).border(1.dp, border, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Ic(icon, tint = fg)
        Text(text, style = H.t.btn, color = fg, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun HChip(text: String, on: Boolean, mono: Boolean = false, onClick: () -> Unit) {
    val c = H.c
    val shape = RoundedCornerShape(HDim.chip)
    Box(
        Modifier.height(40.dp).hClick(0.92f, role = Role.Tab, onClick = onClick).clip(shape)
            .semantics { selected = on }.background(if (on) c.s2 else Color.Transparent)
            .border(1.dp, if (on) c.mu else c.line, shape).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = if (mono) H.t.mono13 else H.t.chip13, color = if (on) c.ink else c.mu) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Seg(
    options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier,
    pad: PaddingValues = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 12.dp),
) {
    FlowRow(modifier.fillMaxWidth().padding(pad), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> HChip(o, o == selected) { onSelect(o) } }
    }
}

@Composable
fun Sw(on: Boolean, modifier: Modifier = Modifier, onToggle: (() -> Unit)? = null) {
    val c = H.c; val m = LocalMotion.current
    val x by animateDpAsState(if (on) 21.dp else 3.dp, m.spec(150, Ease), label = "sw")
    val click = if (onToggle != null) Modifier.hClick(role = Role.Switch, onClick = onToggle) else Modifier
    Box(modifier.size(44.dp, 26.dp).then(click).clip(RoundedCornerShape(13.dp)).background(if (on) c.ac else c.s2)) {
        Box(Modifier.offset(x = x, y = 3.dp).size(20.dp).clip(CircleShape).background(if (on) c.onAc else c.mu))
    }
}

@Composable
fun Srch(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = H.c; val t = H.t
    val shape = RoundedCornerShape(HDim.input)
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 12.dp).height(48.dp)
            .clip(shape).background(c.s1).border(1.dp, c.line, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ic(HIcon.Search, tint = c.mu)
        BasicTextField(
            value, onChange, singleLine = true, textStyle = t.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(placeholder, style = t.body, color = c.mu); inner() } },
        )
    }
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
    Text(text, style = H.t.badge, color = fg, modifier = modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
fun Fab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = H.c
    Row(
        modifier.height(52.dp).hClick(onClick = onClick).clip(RoundedCornerShape(HDim.pill)).background(c.ac).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { Ic(HIcon.Plus, tint = c.onAc); Text(text, style = H.t.fab, color = c.onAc) }
}

/** Padding 8/12/12 and gap 10 — the prototype's .dock. */
@Composable
fun Dock(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun TabBar(items: List<Triple<String, HIcon, Boolean>>, onTab: (Int) -> Unit) {
    val c = H.c
    val m = LocalMotion.current
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().height(HDim.tabsBar).clip(shape).background(c.s1).border(1.dp, c.line, shape).padding(4.dp)) {
        val index = items.indexOfFirst { it.third }.coerceAtLeast(0)
        val tabW = maxWidth / items.size
        val x by animateDpAsState(tabW * index, m.spec(320, Rise), label = "tabIndicator")
        // The neutral selection surface slides to the active tab.
        Box(
            Modifier.offset(x = x).width(tabW).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(c.s2),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            items.forEachIndexed { i, (label, icon, on) ->
                val src = remember { MutableInteractionSource() }
                val pressed by src.collectIsPressedAsState()
                val s by animateFloatAsState(
                    if (pressed) 0.92f else 1f, m.spec(160, Rise), label = "tabIcon",
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                        .selectable(selected = on, interactionSource = src, indication = null, role = Role.Tab) { onTab(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Ic(icon, Modifier.graphicsLayer { scaleX = s; scaleY = s }, tint = if (on) c.ac else c.mu)
                    Text(label, style = H.t.tab, color = if (on) c.ink else c.mu)
                }
            }
        }
    }
}

/** Screen shell: status bar + (nav bars ∪ IME) insets, content capped at 980dp on tablets, optional dock. */
@Composable
fun ScreenFrame(dock: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = H.c
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 980.dp).fillMaxSize().statusBarsPadding().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            if (dock != null) Dock(dock)
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
