package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.clodex.harness.clodex.theme.LocalClodexStyle

data class ChipSpec(
    val label: String,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit = {},
)

/** Small outlined pills; a selected chip is filled like a segmented control. */
@Composable
fun ChipRow(
    chips: List<ChipSpec>,
    modifier: Modifier = Modifier,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        chips.forEachIndexed { index, chip ->
            if (index > 0) Spacer(Modifier.width(8.dp))
            Chip(chip)
        }
    }
}

@Composable
private fun Chip(chip: ChipSpec) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = CircleShape
    val selected = chip.selected
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .then(
                when {
                    selected && style.buttonStyle == app.clodex.harness.clodex.theme.ButtonStyle.GLASS ->
                        Modifier.background(glassActiveBrush(), shape)
                    selected -> Modifier.background(colors.accent, shape)
                    else -> Modifier.background(colors.surface, shape).border(1.dp, colors.borderGhost, shape)
                },
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = chip.onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chip.icon != null) {
            Icon(
                chip.icon,
                contentDescription = null,
                tint = if (selected) colors.onAccent else colors.chipText,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            chip.label,
            style = style.type.captionSmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) colors.onAccent else colors.chipText,
        )
    }
}
