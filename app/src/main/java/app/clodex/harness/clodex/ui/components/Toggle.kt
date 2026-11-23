package app.clodex.harness.clodex.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.clodex.harness.clodex.theme.ButtonStyle
import app.clodex.harness.clodex.theme.LocalClodexStyle

/** 51×31 dp switch with a 27 dp knob moving in 150 ms. */
@Composable
fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(durationMillis = 150),
        label = "toggle",
    )
    val gradient = style.buttonStyle == ButtonStyle.GLASS
    val trackShape = CircleShape
    Box(
        modifier = modifier
            .width(51.dp)
            .height(31.dp)
            .clip(trackShape)
            .then(
                when {
                    !checked -> Modifier
                        .background(colors.surfaceChip, trackShape)
                        .border(1.dp, colors.borderGhost, trackShape)
                    gradient -> Modifier.background(glassActiveBrush(), trackShape)
                    else -> Modifier.background(colors.accent, trackShape)
                },
            )
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(0.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}
