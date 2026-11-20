package app.clodex.harness.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.clodex.harness.ui.theme.HColors

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

private val TOKEN = Regex(
    "(#.*$|//.*$)|(\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*')|\\b(import|from|def|return|with|as|for|in|if|elif|else|open|list|class|fun|val|var|const|let|function|new|while|try|except|true|false|null|None|True|False|print)\\b|\\b(\\d+)\\b",
    RegexOption.MULTILINE,
)

/** Same four classes as the prototype's hl(): comment / string / keyword / number. */
fun highlight(code: String, c: HColors): AnnotatedString = buildAnnotatedString {
    var i = 0
    for (m in TOKEN.findAll(code)) {
        append(code.substring(i, m.range.first))
        val g = m.groups
        val style = when {
            g[1] != null -> SpanStyle(color = c.mu, fontStyle = FontStyle.Italic)
            g[2] != null -> SpanStyle(color = c.ok)
            g[3] != null -> SpanStyle(color = c.ac)
            else -> SpanStyle(color = c.warn)
        }
        withStyle(style) { append(m.value) }
        i = m.range.last + 1
    }
    append(code.substring(i))
}

private fun hex(c: Color): String = "#%06X".format(0xFFFFFF and c.toArgb())

/** Gives a bare HTML artifact the look of the prototype's .pvw card (centered, pill buttons). */
fun wrapHtml(content: String, c: HColors): String = "<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
    "<style>body{margin:0;padding:40px 20px;background:${hex(c.s1)};color:${hex(c.ink)};font-family:sans-serif;text-align:center}" +
    "h1,h2,h3,h4{font-size:22px;margin:0 0 8px}p{color:${hex(c.mu)};margin:0 0 18px}" +
    "a,button{display:inline-block;padding:10px 20px;border:0;border-radius:22px;background:${hex(c.ink)};color:${hex(c.bg)};font-size:14px;text-decoration:none}</style>" +
    content
