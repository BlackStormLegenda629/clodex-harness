package app.clodex.harness.data.search

object SearchQuery {
    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").lowercase()

    fun isSearchable(raw: String): Boolean = normalize(raw).length >= 2

    /** A bounded excerpt around the first case-insensitive match, with ellipses where text is cut. */
    fun snippet(text: String, q: String, radius: Int = 60): String {
        if (text.isEmpty()) return ""
        if (q.isEmpty()) return excerpt(text, 0, radius * 2)
        val index = text.indexOf(q, ignoreCase = true)
        if (index < 0) return excerpt(text, 0, radius * 2)
        val window = radius * 2
        var start = (index - radius + q.length / 2).coerceAtLeast(0)
        if (start + window > text.length) start = (text.length - window).coerceAtLeast(0)
        val end = (start + window).coerceAtMost(text.length)
        return excerpt(text, start, end - start)
    }

    private fun excerpt(text: String, start: Int, length: Int): String {
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = (safeStart + length).coerceAtMost(text.length)
        val body = text.substring(safeStart, safeEnd)
        val prefix = if (safeStart > 0) "…" else ""
        val suffix = if (safeEnd < text.length) "…" else ""
        return prefix + body + suffix
    }
}
