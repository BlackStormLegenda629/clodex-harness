package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import app.clodex.harness.clodex.theme.LocalClodexStyle
import app.clodex.harness.clodex.ui.icons.ClodexIcons

/** Pill showing the current model and effort level; opens the model picker. */
@Composable
fun ModelChip(
    model: String,
    level: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = CircleShape
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(colors.surfaceBubble, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(model, style = style.type.caption, color = colors.textPrimary)
        Spacer(Modifier.width(6.dp))
        Text(level, style = style.type.caption, color = colors.textMuted)
        Spacer(Modifier.width(4.dp))
        Icon(ClodexIcons.ChevronDown, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
    }
}
