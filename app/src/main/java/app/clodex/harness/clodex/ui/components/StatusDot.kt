package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.clodex.harness.clodex.theme.LocalClodexStyle

enum class Status { Running, Waiting, Done }

/** 8 dp dot: accent running, warning waiting, success done. */
@Composable
fun StatusDot(status: Status, modifier: Modifier = Modifier) {
    val colors = LocalClodexStyle.current.colors
    val color = when (status) {
        Status.Running -> colors.accent
        Status.Waiting -> colors.warning
        Status.Done -> colors.successStrong
    }
    Box(modifier.size(8.dp).clip(CircleShape).background(color, CircleShape))
}

/** Optional explicit colour, used by rows that show a success check instead of a dot. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color, CircleShape))
}
