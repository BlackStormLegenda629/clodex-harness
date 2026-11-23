package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.clodex.harness.clodex.theme.ButtonStyle
import app.clodex.harness.clodex.theme.LocalClodexStyle

enum class ClodexButtonVariant { Primary, Ghost, Danger, Text, White, Tonal, Outlined }

/**
 * All button variants of the app. Primary becomes a lavender gradient in glass mode and
 * Primary-coloured in Material You; every other variant keeps the same geometry.
 */
@Composable
fun ClodexButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ClodexButtonVariant = ClodexButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = CircleShape

    val background: Color = when {
        variant == ClodexButtonVariant.Primary && style.buttonStyle == ButtonStyle.MATERIAL -> colors.primary
        variant == ClodexButtonVariant.Primary && style.buttonStyle == ButtonStyle.GLASS -> Color.Transparent
        variant == ClodexButtonVariant.Primary -> colors.accent
        variant == ClodexButtonVariant.Ghost -> colors.surfaceAlt
        variant == ClodexButtonVariant.White -> colors.white
        variant == ClodexButtonVariant.Tonal -> colors.secondaryContainer
        else -> Color.Transparent
    }
    val border: Color? = when (variant) {
        ClodexButtonVariant.Ghost -> colors.borderGhost
        ClodexButtonVariant.Danger -> colors.dangerBorder
        ClodexButtonVariant.Outlined -> colors.outline
        else -> null
    }
    val contentColor = when (variant) {
        ClodexButtonVariant.Primary -> when (style.buttonStyle) {
            ButtonStyle.GLASS -> Color.White
            ButtonStyle.MATERIAL -> colors.onPrimary
            ButtonStyle.MODERN -> colors.onAccent
        }
        ClodexButtonVariant.Ghost -> colors.textPrimary
        ClodexButtonVariant.Danger -> colors.diffDelText
        ClodexButtonVariant.Text -> colors.accent
        ClodexButtonVariant.White -> colors.onAccent
        ClodexButtonVariant.Tonal -> colors.onSecondaryContainer
        ClodexButtonVariant.Outlined -> colors.textPrimary
    }
    val gradient = variant == ClodexButtonVariant.Primary && style.buttonStyle == ButtonStyle.GLASS
    val horizontal = if (variant == ClodexButtonVariant.Text) 14.dp else 22.dp

    Box(
        modifier = modifier
            .then(if (variant == ClodexButtonVariant.Primary) Modifier.height(48.dp) else Modifier.defaultMinSize(minHeight = 48.dp))
            .clip(shape)
            .then(if (gradient) Modifier.background(glassActiveBrush(), shape) else Modifier.background(background, shape))
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .then(if (pressed) Modifier.background(Color.Black.copy(alpha = 0.08f), shape) else Modifier)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = horizontal, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = style.type.button.copy(
                    fontWeight = if (variant == ClodexButtonVariant.Ghost || variant == ClodexButtonVariant.Text) FontWeight.SemiBold else FontWeight.Bold,
                ),
                color = contentColor,
            )
        }
    }
}
