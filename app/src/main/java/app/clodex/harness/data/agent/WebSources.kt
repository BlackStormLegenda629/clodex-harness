package app.clodex.harness.data.agent

import java.net.URI

/** Actual provider citations, not URLs inferred from model prose. */
internal class WebSources {
    private val links = linkedSetOf<String>()

    fun add(raw: String?) {
        if (raw == null || raw.length > 4096 || links.size >= 20) return
        val uri = runCatching { URI(raw) }.getOrNull() ?: return
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) return
        val url = uri.toASCIIString().replace("(", "%28").replace(")", "%29")
        val label = uri.host.removeSurrounding("[", "]")
        links += "[$label]($url)"
    }

    fun markdown(): String? = links.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "\n\nИсточники:\n") { "- $it" }
}
