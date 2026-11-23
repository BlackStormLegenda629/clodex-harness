package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.clodex.harness.clodex.theme.LocalClodexStyle

/** The six accent presets; the last circle opens the HSV picker. */
val SwatchColors = listOf(
    Color(0xFFFF7A1A),
    Color(0xFFA78BFA),
    Color(0xFFD0BCFF),
    Color(0xFF3DDC97),
    Color(0xFFFF4D8D),
    Color(0xFF4D7CFF),
)

@Composable
fun ColorSwatchRow(
    selected: Color,
    onSelect: (Color) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalClodexStyle.current.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SwatchColors.forEach { color ->
            val picked = color.value == selected.value
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .then(if (picked) Modifier.border(2.dp, colors.backgroundSoft, CircleShape) else Modifier)
                    .then(if (picked) Modifier.border(2.dp, colors.textPrimary, CircleShape) else Modifier)
                    .background(color, CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onSelect(color)
                    },
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .border(2.dp, colors.borderStrong, CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCustom),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(selected, CircleShape),
            )
        }
    }
}
