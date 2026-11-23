package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.clodex.harness.R
import app.clodex.harness.clodex.theme.LocalClodexStyle
import kotlin.math.max
import kotlin.math.min

/** HSV picker built from scratch: saturation/value square, hue strip, HEX field, preview. */
@Composable
fun ColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val style = LocalClodexStyle.current
    val colors = style.colors
    var hue by remember { mutableFloatStateOf(hsvHue(initial)) }
    var sat by remember { mutableFloatStateOf(hsvSat(initial)) }
    var value by remember { mutableFloatStateOf(hsvValue(initial)) }
    val picked = Color.hsv(hue, sat, value)
    var hex by remember { mutableStateOf(hexOf(picked)) }

    fun sync(color: Color) {
        hex = hexOf(color)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(style.radius))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.borderStrong, RoundedCornerShape(style.radius))
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.color_picker_title), style = style.type.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.hsv(hue, 1f, 1f))
                    .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                sat = (offset.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                                sync(Color.hsv(hue, sat, value))
                            },
                        ) { change, _ ->
                            sat = (change.position.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            sync(Color.hsv(hue, sat, value))
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            sat = (offset.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                            sync(Color.hsv(hue, sat, value))
                        }
                    },
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(hueBrush())
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                hue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                                sync(Color.hsv(hue, sat, value))
                            },
                        ) { change, _ ->
                            hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                            sync(Color.hsv(hue, sat, value))
                        }
                    },
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(picked),
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceChip)
                        .border(1.dp, colors.borderGhost, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = hex,
                        onValueChange = { raw ->
                            val cleaned = raw.uppercase().filter { it in "0123456789ABCDEF#" }.take(7)
                            hex = cleaned
                            parseHex(cleaned)?.let { parsed ->
                                hue = hsvHue(parsed)
                                sat = hsvSat(parsed)
                                value = hsvValue(parsed)
                            }
                        },
                        singleLine = true,
                        textStyle = style.type.mono.copy(fontSize = 14.sp, color = colors.textPrimary),
                        cursorBrush = Brush.verticalGradient(listOf(colors.accent, colors.accent)),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClodexButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    variant = ClodexButtonVariant.Ghost,
                    modifier = Modifier.weight(1f),
                )
                ClodexButton(
                    text = stringResource(R.string.action_done),
                    onClick = { onConfirm(Color.hsv(hue, sat, value)) },
                    variant = ClodexButtonVariant.Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun hueBrush(): Brush = Brush.horizontalGradient(
    listOf(
        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
    ),
)

private fun hexOf(color: Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return "#%02X%02X%02X".format(r, g, b)
}

private fun parseHex(raw: String): Color? {
    val digits = raw.removePrefix("#")
    if (digits.length != 6) return null
    val value = digits.toLongOrNull(16) ?: return null
    return Color(value or 0xFF000000L)
}

private fun hsvHue(color: Color): Float {
    val max = max(color.red, max(color.green, color.blue))
    val min = min(color.red, min(color.green, color.blue))
    val delta = max - min
    if (delta == 0f) return 0f
    val hue = when (max) {
        color.red -> 60f * (((color.green - color.blue) / delta) % 6f)
        color.green -> 60f * (((color.blue - color.red) / delta) + 2f)
        else -> 60f * (((color.red - color.green) / delta) + 4f)
    }
    return if (hue < 0f) hue + 360f else hue
}

private fun hsvSat(color: Color): Float {
    val max = max(color.red, max(color.green, color.blue))
    val min = min(color.red, min(color.green, color.blue))
    return if (max == 0f) 0f else (max - min) / max
}

private fun hsvValue(color: Color): Float = max(color.red, max(color.green, color.blue))
