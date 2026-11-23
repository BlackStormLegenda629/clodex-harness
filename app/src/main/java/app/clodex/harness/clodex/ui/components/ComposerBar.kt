package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.clodex.harness.R
import app.clodex.harness.clodex.theme.ButtonStyle
import app.clodex.harness.clodex.theme.LocalClodexStyle
import app.clodex.harness.clodex.ui.icons.ClodexIcons

/**
 * The chat composer: hint, attach and mic on the left, model chip and voice/send on the right.
 * [whiteVoice] is used on Home, where the right button is a white voice button.
 */
@Composable
fun ComposerBar(
    hint: String,
    modifier: Modifier = Modifier,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    modelChip: (@Composable () -> Unit)? = null,
    onAttach: () -> Unit = {},
    onMic: () -> Unit = {},
    onSend: () -> Unit = {},
    whiteVoice: Boolean = false,
    leadingChip: (@Composable () -> Unit)? = null,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    val material = style.buttonStyle == ButtonStyle.MATERIAL
    val shape = if (material) CircleShape else RoundedCornerShape(28.dp)
    val fieldShape = if (material) CircleShape else RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (material) colors.secondaryContainer else colors.surface, shape)
            .border(1.dp, if (material) colors.outline else colors.borderStrong, shape)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .padding(bottom = 0.dp),
    ) {
        if (leadingChip != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { leadingChip() }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (material) Modifier.height(56.dp).background(colors.surface, fieldShape) else Modifier)
                .defaultMinSize(minHeight = 24.dp)
                .padding(horizontal = if (material) 16.dp else 4.dp, vertical = if (material) 0.dp else 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    hint,
                    style = style.type.body.copy(fontSize = 16.sp),
                    color = if (material) colors.chipText else colors.textMuted2,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = style.type.body.copy(color = colors.textPrimary),
                cursorBrush = Brush.verticalGradient(listOf(colors.accent, colors.accent)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundActionButton(
                icon = ClodexIcons.Attach,
                description = stringResource(R.string.cd_attach),
                size = 40.dp,
                background = colors.surfaceChip,
                tint = colors.textPrimary,
                onClick = onAttach,
            )
            Spacer(Modifier.weight(1f))
            if (modelChip != null) {
                modelChip()
                Spacer(Modifier.width(8.dp))
            }
            RoundActionButton(
                icon = ClodexIcons.Mic,
                description = stringResource(R.string.cd_mic),
                size = 40.dp,
                background = colors.surfaceChip,
                tint = colors.textPrimary,
                onClick = onMic,
            )
            Spacer(Modifier.width(8.dp))
            if (whiteVoice) {
                RoundActionButton(
                    icon = ClodexIcons.Wave,
                    description = stringResource(R.string.cd_voice),
                    size = 40.dp,
                    background = colors.white,
                    tint = colors.onAccent,
                    onClick = onMic,
                )
            } else {
                RoundActionButton(
                    icon = ClodexIcons.Send,
                    description = stringResource(R.string.cd_send),
                    size = 40.dp,
                    background = colors.accent,
                    tint = colors.onAccent,
                    onClick = onSend,
                )
            }
        }
    }
}

@Composable
fun RoundActionButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}
