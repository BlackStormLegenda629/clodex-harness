package app.clodex.harness.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun f(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else v.toString()

/** <circle> as an SVG path (two arcs). */
private fun circ(cx: Float, cy: Float, r: Float) =
    "M${f(cx - r)} ${f(cy)}a${f(r)} ${f(r)} 0 1 0 ${f(2 * r)} 0a${f(r)} ${f(r)} 0 1 0 ${f(-2 * r)} 0z"

/** <rect rx> as an SVG path. */
private fun rr(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${f(x + r)} ${f(y)}H${f(x + w - r)}a${f(r)} ${f(r)} 0 0 1 ${f(r)} ${f(r)}V${f(y + h - r)}" +
        "a${f(r)} ${f(r)} 0 0 1 ${f(-r)} ${f(r)}H${f(x + r)}a${f(r)} ${f(r)} 0 0 1 ${f(-r)} ${f(-r)}" +
        "V${f(y + r)}a${f(r)} ${f(r)} 0 0 1 ${f(r)} ${f(-r)}z"

/** Every <symbol> of the prototype's sprite, same path data, same 24x24 viewBox. */
enum class HIcon(vararg val paths: String) {
    Chat("M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"),
    Stack("M5 8h14M7 4h10M4 12h16v8H4z"),
    Code("M8 7l-5 5 5 5M16 7l5 5-5 5M14 5l-4 14"),
    Cards(rr(3f, 7f, 12f, 14f, 3f), "M9 4h9a3 3 0 0 1 3 3v9"),
    Gear("M4 7h10M18 7h2M4 17h2M10 17h10", circ(16f, 7f, 2f), circ(8f, 17f, 2f)),
    Plus("M12 5v14M5 12h14"),
    Mic(rr(9f, 3f, 6f, 11f, 3f), "M5 11a7 7 0 0 0 14 0M12 18v3"),
    Up("M12 19V5M5 12l7-7 7 7"),
    Back("M15 18l-6-6 6-6"),
    X("M6 6l12 12M18 6L6 18"),
    Ck("M5 12l5 5 9-10"),
    Ch("M9 6l6 6-6 6"),
    Cam("M4 8h3l2-3h6l2 3h3v11H4z", circ(12f, 13f, 3.5f)),
    Img(rr(4f, 4f, 16f, 16f, 3f), circ(9f, 9f, 1.5f), "M4 17l5-5 4 4 3-3 4 4"),
    File("M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M12 17v-6M9 13l3-3 3 3"),
    Vid(rr(3f, 6f, 13f, 12f, 3f), "M16 11l5-3v8l-5-3"),
    Globe(circ(12f, 12f, 9f), "M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"),
    Plug(rr(4f, 13f, 7f, 7f, 1f), rr(13f, 13f, 7f, 7f, 1f), rr(4f, 4f, 7f, 7f, 1f), "M16 4v6M13 7h6"),
    Copy(rr(8f, 8f, 12f, 12f, 2f), "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"),
    Share("M12 15V4M8 8l4-4 4 4M5 13v6h14v-6"),
    Search(circ(11f, 11f, 7f), "M20 20l-4-4"),
    Filter("M4 5h16l-6 8v6l-4-2v-4z"),
    Dots(circ(12f, 5f, 1.2f), circ(12f, 12f, 1.2f), circ(12f, 19f, 1.2f)),
    Expand("M14 4h6v6M20 4l-6 6M10 20H4v-6M4 20l6-6"),
    Collapse("M20 4l-6 6M14 4v6h6M4 20l6-6M10 20v-6H4"),
    Eye("M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6-10-6-10-6z", circ(12f, 12f, 2.5f)),
    Pin("M9 4h6l-1 6 3 3H7l3-3zM12 13v7"),
    Pen("M4 20l4-1 11-11-3-3L5 16zM14 7l3 3"),
    Trash("M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13"),
    Arch(rr(3f, 4f, 18f, 5f, 1f), "M5 9v10h14V9M10 13h4"),
    Lock(rr(5f, 11f, 14f, 9f, 2f), "M8 11V8a4 4 0 0 1 8 0v3"),
    User(circ(12f, 8f, 4f), "M4 21c1-4 4-6 8-6s7 2 8 6"),
    Docp("M13 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h6M13 3l5 5v3M13 3v5h5M17 15v6M14 18h6"),
    Wait(circ(12f, 12f, 9f), "M12 7v6M12 16.5v.5"),
    Spin("M12 3a9 9 0 1 0 9 9"),
    List("M4 6h16M4 12h10M4 18h16"),
    Dl("M12 4v11M7 11l5 5 5-5M5 20h14"),
    Branch(circ(6f, 6f, 2.5f), circ(6f, 18f, 2.5f), circ(18f, 8f, 2.5f), "M6 8.5v7M18 10.5c0 4-6 3-11 6"),
    Folder("M3 6h7l2 2h9v11H3z"),
}

object HIcons {
    private val cache = HashMap<Pair<HIcon, Float>, ImageVector>()

    fun get(icon: HIcon, stroke: Float = 2f): ImageVector = cache.getOrPut(icon to stroke) {
        ImageVector.Builder(
            name = icon.name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            icon.paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }

    fun byName(name: String): HIcon = HIcon.entries.firstOrNull { it.name == name } ?: HIcon.Pen
}
