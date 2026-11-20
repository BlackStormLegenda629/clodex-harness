package app.clodex.harness.data.code

class CodeFiles(private val gateway: WorkspaceGateway) {
    suspend fun list(path: WorkspacePath): Result<List<WorkspaceEntry>> = gateway.list(path).map { entries ->
        entries.sortedWith(compareBy<WorkspaceEntry> { !it.dir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.path.name })
    }

    suspend fun preview(path: WorkspacePath): Result<String> {
        if (!isHtmlFile(path)) return Result.failure(WorkspaceException("Предпросмотр доступен только для HTML"))
        return gateway.read(path).mapCatching { it.text ?: throw WorkspaceException(it.reason ?: "Не удалось прочитать HTML") }
    }
}

fun isHtmlFile(path: WorkspacePath) = path.name.endsWith(".html", true) || path.name.endsWith(".htm", true)
