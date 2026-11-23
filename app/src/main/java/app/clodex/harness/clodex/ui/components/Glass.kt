package app.clodex.harness.clodex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import app.clodex.harness.clodex.theme.ClodexColors
import app.clodex.harness.clodex.theme.LocalClodexStyle

/** Base colour plus the three radial spots; only drawn when Liquid Glass is on. */
private fun DrawScope.drawGlassSpots(colors: ClodexColors) {
    drawRect(colors.glassBase)
    val far = size.maxDimension
    drawRect(
        Brush.radialGradient(
            colors = listOf(colors.glassSpot1, colors.glassSpot1.copy(alpha = 0f)),
            center = Offset(size.width * 0.16f, size.height * 0.10f),
            radius = far * 0.42f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(colors.glassSpot2, colors.glassSpot2.copy(alpha = 0f)),
            center = Offset(size.width * 0.90f, size.height * 0.60f),
            radius = far * 0.45f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(colors.glassSpot3, colors.glassSpot3.copy(alpha = 0f)),
            center = Offset(size.width * 0.42f, size.height * 0.98f),
            radius = far * 0.46f,
        ),
    )
}

/** Screen background: flat palette colour, or the spot gradient in glass mode. */
@Composable
fun Modifier.clodexBackground(): Modifier {
    val style = LocalClodexStyle.current
    val colors = style.colors
    return if (style.glass) drawBehind { drawGlassSpots(colors) } else background(colors.background)
}

/** Translucent glass surface: 7% white fill, 1 dp white frame, dark shadow and a highlight. */
@Composable
fun Modifier.glassSurface(shape: Shape): Modifier {
    val style = LocalClodexStyle.current
    if (!style.glass) return this
    val colors = style.colors
    return this
        .shadow(
            elevation = 22.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.35f),
            spotColor = Color.Black.copy(alpha = 0.35f),
        )
        .clip(shape)
        .background(colors.glassFill, shape)
        .border(1.dp, colors.glassBorder, shape)
        .drawBehind {
            drawRect(
                Brush.linearGradient(
                    colors = listOf(colors.glassHighlight, Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
        }
}

/** The lavender gradient every active control uses in glass mode. */
@Composable
fun glassActiveBrush(): Brush {
    val colors = LocalClodexStyle.current.colors
    return Brush.verticalGradient(listOf(colors.glassActiveFrom, colors.glassActiveTo))
}

/** Full-screen glass backdrop shared by screens and previews. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().clodexBackground(), content = content)
}
