package app.clodex.harness.data.code

/**
 * A normalized path relative to a workspace root. It can never point outside the root:
 * `..`, absolute paths, backslashes, encoded separators and control characters are rejected.
 */
class WorkspacePath private constructor(val segments: List<String>) {
    val value: String get() = segments.joinToString("/")
    val isRoot: Boolean get() = segments.isEmpty()
    val name: String get() = segments.lastOrNull().orEmpty()
    val parent: WorkspacePath get() = if (isRoot) this else WorkspacePath(segments.dropLast(1))

    fun child(name: String): Result<WorkspacePath> = parse(if (isRoot) name else "$value/$name")

    override fun equals(other: Any?) = other is WorkspacePath && other.segments == segments
    override fun hashCode() = segments.hashCode()
    override fun toString() = value.ifEmpty { "." }

    companion object {
        const val MAX_LENGTH = 512
        const val MAX_SEGMENT = 255
        val ROOT = WorkspacePath(emptyList())
        private val ENCODED_SEPARATOR = Regex("%(2f|5c)", RegexOption.IGNORE_CASE)

        fun parse(input: String): Result<WorkspacePath> {
            fun fail(why: String) = Result.failure<WorkspacePath>(WorkspaceException("Недопустимый путь «${input.take(80)}»: $why"))
            if (input.length > MAX_LENGTH) return fail("длиннее $MAX_LENGTH символов")
            if (input.any { it.code < 0x20 || it.code == 0x7F }) return fail("управляющие символы")
            if ('\\' in input) return fail("обратная косая черта")
            if (ENCODED_SEPARATOR.containsMatchIn(input)) return fail("закодированный разделитель")
            if (input.startsWith("/")) return fail("абсолютный путь")
            val parts = input.split('/').filter { it.isNotEmpty() && it != "." }
            if (parts.any { it == ".." }) return fail("выход за пределы папки")
            if (parts.any { it.length > MAX_SEGMENT }) return fail("имя длиннее $MAX_SEGMENT символов")
            return Result.success(WorkspacePath(parts))
        }
    }
}
