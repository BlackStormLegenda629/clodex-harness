package app.clodex.harness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HDim

/** Material You section title: 14sp semibold, accent colored, sentence case. */
@Composable
fun Sg(text: String) {
    Text(text, style = H.t.row14Semi, color = H.c.ac, modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp))
}

/** Filled M3 card; Classic and Studio keep the thin border, Material You does not. */
@Composable
fun Modifier.hCard(shape: Shape, color: Color? = null): Modifier {
    val c = H.c
    return clip(shape).background(color ?: c.s1)
        .then(if (H.material) Modifier else Modifier.border(1.dp, c.line, shape))
}

/** Bordered card with hairline dividers between rows; every row rises in with the prototype's stagger. */
@Composable
fun <T> Grp(items: List<T>, modifier: Modifier = Modifier.padding(horizontal = 20.dp), animate: Boolean = true, row: @Composable (item: T, index: Int) -> Unit) {
    val c = H.c
    val shape = RoundedCornerShape(HDim.card)
    Column(modifier.fillMaxWidth().hCard(shape)) {
        items.forEachIndexed { i, it ->
            Box(
                Modifier.fillMaxWidth().then(if (animate) Modifier.rise(i) else Modifier)
                    .drawBehind {
                        if (i > 0) {
                            val inset = 16.dp.toPx()
                            drawLine(c.line, Offset(inset, 0f), Offset(size.width - inset, 0f), 1.dp.toPx())
                        }
                    },
            ) { row(it, i) }
        }
    }
}

/** Heterogeneous rows of one card. */
@Composable
fun GrpOf(vararg rows: @Composable () -> Unit) {
    Grp(rows.toList()) { r, _ -> r() }
}

@Composable
fun Li(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, role: Role? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.hClick(role = role ?: Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

/** .li.f — small label above an input. */
@Composable
fun LiField(
    label: String, value: String, onChange: (String) -> Unit, placeholder: String = "", password: Boolean = false,
    singleLine: Boolean = true, minLines: Int = 1, modifier: Modifier = Modifier,
) {
    val c = H.c; val t = H.t
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Text(label, style = t.small, color = c.mu)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(HDim.input)).background(c.s2).padding(12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value, onChange, singleLine = singleLine, minLines = minLines, textStyle = t.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                decorationBox = { inner -> Box { if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = t.body, color = c.mu); inner() } },
            )
        }
    }
}

/** .it — list row with a top hairline. */
@Composable
fun It(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val c = H.c
    Row(
        modifier.fillMaxWidth().drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .then(if (onClick != null) Modifier.hClick(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

/** Material You tonal icon plate: circular by default, tinted with the accent. */
@Composable
fun IconPlate(icon: HIcon, circle: Boolean = true, size: Dp = 40.dp, iconSize: Dp = 20.dp, tint: Color = H.c.ac, bg: Color = H.c.ac.copy(alpha = 0.14f)) {
    Box(Modifier.size(size).clip(if (circle) CircleShape else RoundedCornerShape(HDim.chip)).background(bg), contentAlignment = Alignment.Center) {
        Ic(icon, tint = tint, size = iconSize)
    }
}

/** Title + muted second line, the common .g block. */
@Composable
fun RowScope.Titles(title: String, sub: String? = null, mono: Boolean = false, weight: Float = 1f, ellipsis: Boolean = true) {
    Column(Modifier.weight(weight)) {
        Text(title, style = H.t.bodyMed, color = H.c.ink, maxLines = if (ellipsis) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = if (mono) H.t.mono12 else H.t.small, color = H.c.mu, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Settings row: optional tonal icon, title/subtitle, value and a chevron. */
@Composable
fun ActionRow(title: String, value: String? = null, sub: String? = null, icon: HIcon? = null, onClick: () -> Unit) {
    Li(onClick = onClick) {
        if (icon != null) IconPlate(icon)
        Titles(title, sub)
        if (value != null) Text(value, style = H.t.row14, color = H.c.mu, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
    }
}

/** .r2 — sheet row on a surface card. */
@Composable
fun R2(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, role: Role? = null, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(HDim.card)
    Row(
        modifier.fillMaxWidth().padding(bottom = 8.dp).then(if (onClick != null) Modifier.hClick(role = role ?: Role.Button, onClick = onClick) else Modifier)
            .hCard(shape).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = content,
    )
}

/** Model/effort style row: title (+badges) and optional description, check on the right. */
@Composable
fun R2Choice(title: String, selected: Boolean, onClick: () -> Unit, desc: String? = null, badges: List<Pair<String, BdgKind>> = emptyList()) {
    R2(onClick = onClick) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = H.t.row16, color = H.c.ink, modifier = Modifier.weight(1f, fill = false))
                badges.forEach { (b, k) -> Bdg(b, k) }
            }
            if (desc != null) Text(desc, style = H.t.note.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified), color = H.c.mu, modifier = Modifier.padding(top = 4.dp))
        }
        if (selected) Ic(HIcon.Ck, tint = H.c.ac)
    }
}
