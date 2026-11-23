package app.clodex.harness.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

sealed interface MdBlock {
    data class Text(val text: String) : MdBlock
    data class Code(val lang: String, val body: String) : MdBlock
}

private val htmlTokens = Regex("""<!--[\s\S]*?(?:-->|\z)|</?[A-Za-z!](?:"[^"]*+"|'[^']*+'|[^'"<>]++)*+>|&(?:#[xX]?[0-9A-Fa-f]++|[A-Za-z][A-Za-z0-9]++);""")
private val htmlTagParts = Regex("""^</?!?[\w:-]+|"[^"]*+"|'[^']*+'|(?<![\w:.-])[\w:.-]++(?=\s*=)|/?>$""")

internal fun highlightHtml(source: String): AnnotatedString = buildAnnotatedString {
    append(source)
    fun paint(range: IntRange, color: Long, offset: Int = 0) {
        addStyle(SpanStyle(color = Color(color)), offset + range.first, offset + range.last + 1)
    }
    for (token in htmlTokens.findAll(source)) {
        when {
            token.value.startsWith("<!--") -> paint(token.range, 0xFF9A9A91)
            token.value.startsWith('&') -> paint(token.range, 0xFFE7AC79)
            else -> for (part in htmlTagParts.findAll(token.value)) {
                val color = when (part.value.first()) {
                    '<', '>', '/' -> 0xFFF2A0A5
                    '\'', '"' -> 0xFFB7D28E
                    else -> 0xFF9DBDEE
                }
                paint(part.range, color, token.range.first)
            }
        }
    }
}

/** Splits an answer into prose and ``` fenced code. An unterminated fence (mid-stream) counts as code. */
fun parseBlocks(src: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val cur = ArrayList<String>()
    var inCode = false
    var lang = ""
    fun flushText() { val t = cur.joinToString("\n").trim(); if (t.isNotEmpty()) out += MdBlock.Text(t); cur.clear() }
    for (line in src.split('\n')) {
        if (line.trimStart().startsWith("```")) {
            if (!inCode) { flushText(); inCode = true; lang = line.trim().removePrefix("```").trim() }
            else { out += MdBlock.Code(lang, cur.joinToString("\n")); cur.clear(); inCode = false }
        } else cur += line
    }
    if (inCode) out += MdBlock.Code(lang, cur.joinToString("\n")) else flushText()
    return out
}

private val INLINE = Regex("\\[([^\\]]+)]\\(([^)]+)\\)|`([^`]+)`")

/** `code` and [text](https://link): the two things the HTML prototype's mdl() understands. */
fun inlineMd(text: String, link: SpanStyle, code: SpanStyle): AnnotatedString = buildAnnotatedString {
    var i = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(i, m.range.first))
        val url = m.groupValues[2]
        when {
            m.groups[3] != null -> withStyle(code) { append(m.groupValues[3]) }
            url.startsWith("http://") || url.startsWith("https://") ->
                withLink(LinkAnnotation.Url(url, TextLinkStyles(link))) { append(m.groupValues[1]) }
            else -> append(m.groupValues[1])
        }
        i = m.range.last + 1
    }
    append(text.substring(i))
}
