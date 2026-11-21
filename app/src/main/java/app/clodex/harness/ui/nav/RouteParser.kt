package app.clodex.harness.ui.nav

object RouteParser {
    fun parse(route: String): Pair<String, String?>? = when {
        Regex("(chat|session|diff|artifact)/[1-9][0-9]*").matches(route) -> route.substringBefore('/') to route.substringAfter('/')
        route.startsWith("browse?uri=") && route.removePrefix("browse?uri=").isNotBlank() -> "browse" to route.removePrefix("browse?uri=")
        else -> null
    }
}
