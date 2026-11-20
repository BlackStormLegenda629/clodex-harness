package app.clodex.harness.data

/** Skills are prompt instructions, not executable plugins or new platform capabilities. */
internal fun skillInstructions(skills: List<SkillEntity>): String = buildString {
    for (skill in skills.sortedBy { it.sortKey }) {
        if (!skill.enabled || skill.body.isBlank()) continue
        val block = "\n\nСкилл «${skill.name.take(120)}» (инструкции, не дополнительные инструменты):\n${skill.body.trim().take(4000)}"
        val remaining = 12000 - length
        if (remaining <= 0) break
        append(block.take(remaining))
    }
}
