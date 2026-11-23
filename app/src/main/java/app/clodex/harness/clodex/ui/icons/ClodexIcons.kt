package app.clodex.harness.clodex.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun f(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else v.toString()

/** A stroked 24×24 icon; the colour is applied by Icon(tint = …). */
private fun icon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** <circle> as two arcs, so the path parses into ArcTo nodes. */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${f(cx - r)} ${f(cy)}a${f(r)} ${f(r)} 0 1 0 ${f(2 * r)} 0a${f(r)} ${f(r)} 0 1 0 ${f(-2 * r)} 0z"

/** Rounded rectangle as a path with arcs on the corners. */
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${f(x + r)} ${f(y)}H${f(x + w - r)}a${f(r)} ${f(r)} 0 0 1 ${f(r)} ${f(r)}V${f(y + h - r)}" +
        "a${f(r)} ${f(r)} 0 0 1 ${f(-r)} ${f(r)}H${f(x + r)}a${f(r)} ${f(r)} 0 0 1 ${f(-r)} ${f(-r)}" +
        "V${f(y + r)}a${f(r)} ${f(r)} 0 0 1 ${f(r)} ${f(-r)}z"

/** The single icon set of the app; nothing is borrowed from third-party packs. */
object ClodexIcons {
    val Menu = icon("menu", "M4 7h16M4 12h16M4 17h10")
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val EditNew = icon("editNew", "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val Search = icon("search", circle(11f, 11f, 7f), "M20 20l-4-4")
    val ChevronLeft = icon("chevronLeft", "M15 18l-6-6 6-6")
    val ChevronRight = icon("chevronRight", "M9 6l6 6-6 6")
    val ChevronDown = icon("chevronDown", "M6 9l6 6 6-6")
    val Chat = icon("chat", "M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z")
    val Folder = icon("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Code = icon("code", "M8 6l-6 6 6 6M16 6l6 6-6 6")
    val Artifacts = icon("artifacts", rect(3f, 7f, 10f, 13f, 2f), "M9 4h9a2 2 0 0 1 2 2v9")
    val Cowork = icon(
        "cowork",
        circle(9f, 8f, 3f), circle(17f, 9f, 2.5f),
        "M3 20a6 6 0 0 1 12 0M15 20a4 4 0 0 1 6 0",
    )
    val Mic = icon("mic", rect(9f, 3f, 6f, 11f, 3f), "M5 11a7 7 0 0 0 14 0M12 18v3")
    val Wave = icon("wave", "M5 12v0M9 8v8M13 5v14M17 8v8M21 12v0")
    val Attach = icon("attach", "M21 11.5l-8.5 8.5a5 5 0 0 1-7-7L14 4.5a3.5 3.5 0 0 1 5 5L10.5 18a2 2 0 0 1-3-3L15 7.5")
    val Send = icon("send", "M12 19V5M5 12l7-7 7 7")
    val Check = icon("check", "M5 12l5 5 9-10")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val File = icon("file", "M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z", "M14 3v6h6")
    val Clock = icon("clock", circle(12f, 12f, 9f), "M12 7v5l3 2")
    val Shield = icon("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z")
    val Branch = icon("branch", "M6 3v12M18 6a3 3 0 1 0 0 .01M6 18a3 3 0 1 0 0 .01M18 9a9 9 0 0 1-9 9")
    val Bolt = icon("bolt", "M13 2L4 14h7l-1 8 9-12h-7z")
    val Bulb = icon("bulb", "M9 18h6M10 22h4M12 2a7 7 0 0 0-4 12.7V17h8v-2.3A7 7 0 0 0 12 2z")
    val Settings = icon(
        "settings", circle(12f, 12f, 3f),
        "M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2",
    )
    val Person = icon("person", circle(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0")
    val Card = icon("card", rect(2f, 5f, 20f, 14f, 2f), "M2 10h20")
    val Sliders = icon("sliders", "M4 7h16M4 17h16", circle(9f, 7f, 2f), circle(15f, 17f, 2f))
    val Connectors = icon(
        "connectors",
        rect(3f, 3f, 7f, 7f, 1.5f), rect(14f, 3f, 7f, 7f, 1.5f), rect(3f, 14f, 7f, 7f, 1.5f),
        "M17.5 14v7M14 17.5h7",
    )
    val Info = icon("info", circle(12f, 12f, 9f), "M12 16v-4M12 8h0")
    val Undo = icon("undo", "M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3")
    val PullRequest = icon(
        "pullRequest", circle(6f, 6f, 2f), circle(6f, 18f, 2f), circle(18f, 18f, 2f),
        "M6 8v8M18 16V9a3 3 0 0 0-3-3h-3",
    )
    val Palette = icon(
        "palette", circle(12f, 12f, 9f),
        circle(8f, 10f, 1.3f), circle(12f, 7.5f, 1.3f), circle(16f, 10f, 1.3f),
        "M12 21c-1.5 0-2-1-2-2s.5-2 2-2h2",
    )
}
