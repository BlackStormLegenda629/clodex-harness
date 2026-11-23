package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.clodex.harness.clodex.theme.ButtonStyle
import app.clodex.harness.clodex.theme.LocalClodexStyle

/** Pill choice of two or three options; the selected one is filled. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = CircleShape
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(shape)
                    .then(
                        when {
                            !selected -> Modifier
                            style.buttonStyle == ButtonStyle.GLASS -> Modifier.background(glassActiveBrush(), shape)
                            style.buttonStyle == ButtonStyle.MATERIAL -> Modifier.background(colors.primary, shape)
                            else -> Modifier.background(colors.accent, shape)
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = style.type.caption.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = when {
                        !selected -> colors.chipText
                        style.buttonStyle == ButtonStyle.MATERIAL -> colors.onPrimary
                        style.buttonStyle == ButtonStyle.MODERN -> colors.onAccent
                        else -> Color.White
                    },
                )
            }
        }
    }
}
