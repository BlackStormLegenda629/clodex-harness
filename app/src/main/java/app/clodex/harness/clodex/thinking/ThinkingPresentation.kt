package app.clodex.harness.clodex.thinking

/** Thinking indicator model, kept outside the UI package so view models can own it. */
data class ThinkingPresentation(
    val chatId: Long? = null,
    val thinking: Boolean,
    val startedAt: Long? = null,
    val seconds: Int? = null,
    val steps: List<String> = emptyList(),
) {
    val showDots: Boolean get() = thinking
}

fun thinkingLabel(state: ThinkingPresentation, now: Long? = null): String =
    (if (state.thinking) state.startedAt?.let { start -> now?.let { ((it - start) / 1000).coerceAtLeast(0) } }
    else state.seconds?.takeIf { it > 0 }?.toLong())?.let { "Ход рассуждений · $it с" }
        ?: "Ход рассуждений"
