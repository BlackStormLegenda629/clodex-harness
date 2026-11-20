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

/** Section title (.sg): 12sp uppercase muted. */
@Composable
fun Sg(text: String) {
    Text(text.uppercase(), style = H.t.sg, color = H.c.mu, modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 8.dp))
}

/** Bordered card with hairline dividers between rows; every row rises in with the prototype's stagger. */
@Composable
fun <T> Grp(items: List<T>, modifier: Modifier = Modifier.padding(horizontal = 20.dp), animate: Boolean = true, row: @Composable (item: T, index: Int) -> Unit) {
    val c = H.c
    val shape = RoundedCornerShape(HDim.card)
    Column(modifier.fillMaxWidth().clip(shape).background(c.s1).border(1.dp, c.line, shape)) {
        items.forEachIndexed { i, it ->
            Box(
                Modifier.fillMaxWidth().then(if (animate) Modifier.rise(i) else Modifier)
                    .drawBehind { if (i > 0) drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) },
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
        modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (onClick != null) Modifier.hClick(role = role ?: Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
    Column(modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
        Text(label, style = t.small, color = c.mu)
        BasicTextField(
            value, onChange, singleLine = singleLine, minLines = minLines, textStyle = t.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner -> Box { if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = t.body, color = c.mu); inner() } },
        )
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

/** .ai (40dp, radius 12) / .ci (40dp circle) icon plate. */
@Composable
fun IconPlate(icon: HIcon, circle: Boolean = false, size: Dp = 40.dp, iconSize: Dp = 20.dp, tint: Color = H.c.ink, bg: Color = H.c.s2) {
    Box(Modifier.size(size).clip(if (circle) CircleShape else RoundedCornerShape(if (size > 40.dp) 20.dp else 12.dp)).background(bg), contentAlignment = Alignment.Center) {
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

/** .r2 — sheet row on a surface card. */
@Composable
fun R2(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, role: Role? = null, content: @Composable RowScope.() -> Unit) {
    val c = H.c
    val shape = RoundedCornerShape(HDim.card)
    Row(
        modifier.fillMaxWidth().padding(bottom = 6.dp).then(if (onClick != null) Modifier.hClick(role = role ?: Role.Button, onClick = onClick) else Modifier)
            .clip(shape).background(c.s1).border(1.dp, c.line, shape).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 14.dp),
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
