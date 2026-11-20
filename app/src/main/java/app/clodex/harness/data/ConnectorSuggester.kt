package app.clodex.harness.data

object ConnectorSuggester {
    private val aliases = mapOf("fg" to "фигм[еуыа]?", "nt" to "ноушн[ае]?", "gh" to "гитхаб[ае]?", "ln" to "линеар[ае]?", "jr" to "джир[аеуы]?", "sl" to "слак[ае]?", "gm" to "джимейл[ае]?", "cf" to "конфлюенс[ае]?", "cv" to "канв[аеуы]?")
    private val patterns by lazy { ConnectorCatalog.all.filter { it.id != "x" }.associateWith { entry ->
        val terms = listOf(entry.name, entry.site).filter { it.isNotBlank() }.map(Regex::escape) + listOfNotNull(aliases[entry.id])
        Regex("(?<![\\p{L}\\p{N}])(?:${terms.joinToString("|")})(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    } }

    fun find(text: String): List<ConnCatalogEntry> = patterns.mapNotNull { (entry, pattern) ->
        pattern.find(text)?.let { entry to it.range.first }
    }.sortedBy { it.second }.take(2).map { it.first }
}
