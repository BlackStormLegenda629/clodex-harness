package app.clodex.harness.data

/** Cards shown on «Программирование», in reading order. */
enum class ProgrammingSection { CAVEMAN, PERMISSIONS, WORKSPACE, MODE, MODEL, LINKS }

fun programmingSections(): List<ProgrammingSection> = listOf(
    ProgrammingSection.CAVEMAN,
    ProgrammingSection.PERMISSIONS,
    ProgrammingSection.WORKSPACE,
    ProgrammingSection.MODE,
    ProgrammingSection.MODEL,
    ProgrammingSection.LINKS,
)
