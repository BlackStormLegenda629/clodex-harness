package app.clodex.harness.data.code

import app.clodex.harness.data.agent.AgentTool
import app.clodex.harness.data.agent.AgentToolResult
import app.clodex.harness.data.agent.AgentToolSpec
import app.clodex.harness.data.agent.ProviderToolCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Staged edits on top of a workspace. Reads see staged text; nothing touches the workspace until the
 * session applies [changes] through the gateway.
 */
class WorkspaceOverlay(val gateway: WorkspaceGateway) {
    private class Staged(val before: String?, var after: String)
    private val staged = LinkedHashMap<WorkspacePath, Staged>()

    suspend fun list(path: WorkspacePath): Result<List<WorkspaceEntry>> {
        val base = gateway.list(path).recoverCatching { e ->
            if (e is WorkspaceException && e.notFound && staged.keys.any { it.segments.take(path.segments.size) == path.segments }) emptyList() else throw e
        }.getOrElse { return Result.failure(it) }
        val known = base.map { it.path }.toMutableSet()
        val extra = mutableListOf<WorkspaceEntry>()
        for ((p, s) in staged) {
            if (p.segments.size <= path.segments.size || p.segments.take(path.segments.size) != path.segments) continue
            val direct = WorkspacePath.parse(p.segments.take(path.segments.size + 1).joinToString("/")).getOrNull() ?: continue
            if (direct in known) continue
            known += direct
            val isDir = direct != p
            extra += WorkspaceEntry(direct, isDir, if (isDir) 0 else s.after.toByteArray().size.toLong(), !isDir)
        }
        return Result.success(base + extra)
    }

    suspend fun read(path: WorkspacePath): Result<WorkspaceRead> {
        staged[path]?.let { return Result.success(WorkspaceRead(path, it.after, it.after.toByteArray().size.toLong())) }
        return gateway.read(path)
    }

    /** Stages [text] for [path]. The original content is captured once, on first write. */
    suspend fun stage(path: WorkspacePath, text: String): Result<DiffSummary> {
        if (path.isRoot) return Result.failure(WorkspaceException("Нужен путь к файлу"))
        WorkspacePolicy.writeError(path.value, text.toByteArray().size)?.let { return Result.failure(WorkspaceException("${path}: $it")) }
        val existing = staged[path]
        if (existing != null) {
            existing.after = text
            return Result.success(TextDiff.summarize(existing.before, text))
        }
        if (staged.size >= WorkspaceLimits.MAX_CHANGES) return Result.failure(WorkspaceException("Больше ${WorkspaceLimits.MAX_CHANGES} файлов за одну задачу"))
        val before: String? = gateway.read(path).fold(
            onSuccess = { r -> r.text ?: return Result.failure(WorkspaceException("$path нельзя перезаписать: ${r.reason ?: "не текст"}")) },
            onFailure = { e -> if (e is WorkspaceException && e.notFound) null else return Result.failure(e) },
        )
        staged[path] = Staged(before, text)
        return Result.success(TextDiff.summarize(before, text))
    }

    /** Restores a change staged by an earlier run that the user has not applied yet. */
    fun seed(change: WorkspaceChange) { staged[change.path] = Staged(change.before, change.after) }

    /** Effective changes: files whose staged text differs from the original. */
    fun changes(): List<WorkspaceChange> = staged.filter { it.value.before != it.value.after }.map { WorkspaceChange(it.key, it.value.before, it.value.after) }

    fun clear() = staged.clear()
}

object CodeToolNames {
    const val LIST = "workspace_list"
    const val READ = "workspace_read"
    const val WRITE = "workspace_write"
}

private fun pathSchema(withContent: Boolean) = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("path") { put("type", "string"); put("description", "Путь относительно корня рабочей области, например src/App.kt. Пусто — корень.") }
        if (withContent) putJsonObject("content") { put("type", "string"); put("description", "Полное новое содержимое файла (UTF-8).") }
    }
    putJsonArray("required") { if (withContent) { add(JsonPrimitive("path")); add(JsonPrimitive("content")) } }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun pathArg(args: JsonObject): Result<WorkspacePath> = WorkspacePath.parse(args.text("path").orEmpty())

private class ListTool(private val overlay: WorkspaceOverlay) : AgentTool {
    override val spec = AgentToolSpec(CodeToolNames.LIST, "Список файлов и папок в рабочей области.", pathSchema(false), readOnly = true, title = "Рабочая область · список")
    override suspend fun invoke(arguments: JsonObject): AgentToolResult {
        val path = pathArg(arguments).getOrElse { return AgentToolResult.error(it.message!!) }
        val entries = overlay.list(path).getOrElse { return AgentToolResult.error(it.message ?: "Ошибка") }
        if (entries.isEmpty()) return AgentToolResult.of("(папка пуста)")
        return AgentToolResult.of(entries.joinToString("\n") { e ->
            when {
                e.dir -> e.path.value + "/"
                e.text -> "${e.path.value} · ${e.size} Б"
                else -> "${e.path.value} · ${e.size} Б · не текст"
            }
        })
    }
}

private class ReadTool(private val overlay: WorkspaceOverlay) : AgentTool {
    override val spec = AgentToolSpec(CodeToolNames.READ, "Прочитать текстовый файл (до 256 КБ).", pathSchema(false), readOnly = true, title = "Рабочая область · чтение")
    override suspend fun invoke(arguments: JsonObject): AgentToolResult {
        val path = pathArg(arguments).getOrElse { return AgentToolResult.error(it.message!!) }
        if (path.isRoot) return AgentToolResult.error("Нужен путь к файлу")
        val r = overlay.read(path).getOrElse { return AgentToolResult.error(it.message ?: "Ошибка") }
        return r.text?.let { AgentToolResult.of(it) } ?: AgentToolResult.error("Файл $path не прочитан: ${r.reason ?: "не текст"}")
    }
}

/** Only stages: the workspace changes after the final approval (Правки) or at completion (Авто). */
private class WriteTool(private val overlay: WorkspaceOverlay, private val mode: CodeMode) : AgentTool {
    override val spec = AgentToolSpec(
        CodeToolNames.WRITE,
        "Записать полное содержимое файла. Изменение копится и применяется " +
            (if (mode == CodeMode.AUTO) "в конце задачи." else "только после подтверждения пользователем."),
        pathSchema(true), readOnly = true, title = "Рабочая область · запись",
    )
    override suspend fun invoke(arguments: JsonObject): AgentToolResult {
        val path = pathArg(arguments).getOrElse { return AgentToolResult.error(it.message!!) }
        val content = arguments.text("content") ?: return AgentToolResult.error("Нет аргумента content")
        val diff = overlay.stage(path, content).getOrElse { return AgentToolResult.error(it.message ?: "Ошибка") }
        val tail = if (mode == CodeMode.AUTO) "будет применено в конце задачи" else "будет применено после подтверждения пользователя"
        return AgentToolResult.of("Изменение подготовлено: $path ($diff), $tail.")
    }
}

/** `План` gets no write tool at all. */
fun codeTools(overlay: WorkspaceOverlay, mode: CodeMode): List<AgentTool> =
    listOfNotNull(ListTool(overlay), ReadTool(overlay), if (mode == CodeMode.PLAN) null else WriteTool(overlay, mode))

/** Timeline title for a finished code tool call, built from its real arguments. */
fun codeEventTitle(call: ProviderToolCall, isError: Boolean): String? {
    val raw = call.arguments.text("path").orEmpty()
    val path = WorkspacePath.parse(raw).getOrNull()?.toString() ?: raw.take(80)
    val shown = if (path == ".") "корень" else path
    return when (call.name) {
        CodeToolNames.LIST -> if (isError) "Не удалось просмотреть $shown" else "Просмотрел $shown"
        CodeToolNames.READ -> if (isError) "Не удалось прочитать $shown" else "Прочитал $shown"
        CodeToolNames.WRITE -> if (isError) "Не удалось подготовить изменение $shown" else "Подготовил изменение $shown"
        else -> null
    }
}
