package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.clodex.harness.clodex.theme.LocalClodexStyle

/** Round icon button: 44 dp by default, 40 and 36 dp for dense headers. */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    transparent: Boolean = false,
    tint: Color? = null,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val shape = CircleShape
    val background = when {
        transparent -> Color.Transparent
        style.glass -> colors.glassFill
        else -> colors.surfaceChip
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background, shape)
            .then(if (style.glass && !transparent) Modifier.border(1.dp, colors.glassBorder, shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: colors.textPrimary,
            modifier = Modifier.size(if (size >= 44.dp) 20.dp else 20.dp),
        )
    }
}
