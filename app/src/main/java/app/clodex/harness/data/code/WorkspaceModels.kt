package app.clodex.harness.data.code

enum class CodeMode(val label: String) {
    PLAN("План"), EDITS("Правки"), AUTO("Авто");

    companion object {
        fun fromLabel(label: String): CodeMode = entries.firstOrNull { it.label == label } ?: EDITS
    }
}

/** Where a Code session works. Device folders are SAF trees; GitHub repos are read from [base]. */
sealed interface WorkspaceRef {
    val name: String

    data class Device(override val name: String, val treeUri: String) : WorkspaceRef
    data class GitHub(override val name: String, val repo: String, val base: String, val head: String) : WorkspaceRef
}

data class WorkspaceEntry(val path: WorkspacePath, val dir: Boolean, val size: Long, val text: Boolean)

/** [text] is null for binary or oversized files: they are listed but never decoded. */
data class WorkspaceRead(val path: WorkspacePath, val text: String?, val size: Long, val reason: String? = null)

/** A staged edit; [before] is null for a new file. */
data class WorkspaceChange(val path: WorkspacePath, val before: String?, val after: String)

data class WorkspaceFailure(val path: WorkspacePath, val message: String)

/** What really happened on apply. [commitSha]/[branch] are set only by a successful GitHub commit. */
data class WorkspaceApplyResult(
    val applied: List<WorkspacePath>,
    val failed: List<WorkspaceFailure> = emptyList(),
    val commitSha: String? = null,
    val branch: String? = null,
)

/** [recoverable] marks errors the user can fix (re-pick a folder, update a token) without losing the session. */
class WorkspaceException(message: String, val recoverable: Boolean = false, val notFound: Boolean = false) : Exception(message)

interface WorkspaceGateway {
    suspend fun list(path: WorkspacePath): Result<List<WorkspaceEntry>>
    suspend fun read(path: WorkspacePath): Result<WorkspaceRead>
    suspend fun apply(changes: List<WorkspaceChange>, message: String): Result<WorkspaceApplyResult>
}

object WorkspaceLimits {
    const val MAX_READ_BYTES = 256 * 1024
    const val MAX_WRITE_BYTES = 512 * 1024
    const val MAX_LIST = 500
    const val MAX_CHANGES = 50
}
