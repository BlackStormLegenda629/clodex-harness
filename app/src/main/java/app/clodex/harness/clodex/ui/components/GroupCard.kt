package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.clodex.harness.clodex.theme.LocalClodexStyle

/** Rounded group of rows separated by 1 dp SurfaceBubble dividers. */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = RoundedCornerShape(style.radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape)
            .clip(shape)
            .background(if (style.glass) androidx.compose.ui.graphics.Color.Transparent else colors.surface, shape)
            .border(1.dp, if (style.glass) androidx.compose.ui.graphics.Color.Transparent else colors.border, shape),
        content = content,
    )
}

/** Divider between rows of a [GroupCard]. */
@Composable
fun GroupDivider(modifier: Modifier = Modifier) {
    val colors = LocalClodexStyle.current.colors
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(colors.surfaceBubble),
    )
}
