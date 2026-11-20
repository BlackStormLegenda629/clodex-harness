package app.clodex.harness.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/** Stores the factual thinking steps of a reply as a bounded JSON array of strings. */
object ThinkingCodec {
    const val MAX_STEPS = 30
    const val MAX_CHARS = 240

    fun encode(steps: List<String>): String {
        val clean = bound(steps)
        return if (clean.isEmpty()) "" else JsonArray(clean.map(::JsonPrimitive)).toString()
    }

    /** Blank or malformed data yields no steps. */
    fun decode(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val arr = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return emptyList()
        return bound(arr.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content })
    }

    private fun bound(steps: List<String>) =
        steps.map { it.trim().take(MAX_CHARS) }.filter { it.isNotEmpty() }.take(MAX_STEPS)
}
