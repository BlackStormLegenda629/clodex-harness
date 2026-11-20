package app.clodex.harness.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.clodex.harness.data.ProgressStyle
import app.clodex.harness.ui.theme.H
import kotlin.math.sin

@Composable
internal fun TransferProgress(style: ProgressStyle, fraction: Float?, modifier: Modifier = Modifier) {
    val c = H.c
    val width = modifier.fillMaxWidth()
    if (fraction == null) {
        LinearProgressIndicator(modifier = width, color = c.ac, trackColor = c.line)
    } else if (style == ProgressStyle.Straight) {
        LinearProgressIndicator(progress = { fraction }, modifier = width, color = c.ac, trackColor = c.line)
    } else {
        Canvas(width.height(12.dp).progressSemantics(fraction)) {
            val inset = 1.5.dp.toPx()
            val usable = (size.width - 2 * inset).coerceAtLeast(0f)
            val mid = size.height / 2
            val length = usable * fraction
            val stroke = inset * 2
            drawLine(c.line, Offset(inset, mid), Offset(inset + usable, mid), stroke, StrokeCap.Round)
            if (length > 0f) {
                val period = 20.dp.toPx()
                val path = Path().apply {
                    moveTo(inset, mid)
                    val segments = (length / 2.dp.toPx()).toInt().coerceAtLeast(1)
                    for (i in 1..segments) {
                        val x = length * i / segments
                        lineTo(inset + x, mid + inset * sin(x / period * 6.283185f))
                    }
                }
                drawPath(path, c.ac, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}
