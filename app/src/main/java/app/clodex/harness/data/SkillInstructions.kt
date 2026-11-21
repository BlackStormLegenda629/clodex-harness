package app.clodex.harness.data

/**
 * Skills are prompt instructions, not executable plugins or new platform capabilities.
 *
 * The Caveman style block reserves its place first and is appended last, so an
 * oversized user skill can shorten itself but never silently drop the style.
 */
internal fun skillInstructions(skills: List<SkillEntity>, caveman: String = ""): String {
    val style = if (caveman.isBlank()) "" else "\n\nСтиль ответов Caveman:\n" + caveman.trim().take(4_000)
    val budget = 12_000 - style.length
    val body = buildString {
        for (skill in skills.sortedBy { it.sortKey }) {
            if (!skill.enabled || skill.body.isBlank()) continue
            if (length >= budget) break
            val block = "\n\nСкилл «${skill.name.take(120)}» (инструкции, не дополнительные инструменты):\n${skill.body.trim().take(4000)}"
            append(block.take(budget - length))
        }
    }
    return body + style
}
