package app.clodex.harness.data.code

import android.content.Context
import android.net.Uri
import app.clodex.harness.data.SettingsRepository
import app.clodex.harness.data.WorkspaceEntity
import app.clodex.harness.data.agent.AgentEvent
import app.clodex.harness.data.agent.AgentMessage
import app.clodex.harness.data.agent.AgentRequest
import app.clodex.harness.data.agent.AgentRunner
import app.clodex.harness.data.agent.PendingToolCall
import app.clodex.harness.data.agent.ProviderAgentClient
import app.clodex.harness.data.agent.ToolApprovalGate
import app.clodex.harness.data.agent.ToolDecision
import app.clodex.harness.data.maxTokens
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** One agent run: its events, and the changes it staged (read after the flow completes). */
class CodeRun(val events: Flow<AgentEvent>, val changes: () -> List<WorkspaceChange>)

interface CodeAgent {
    /** Checks access before any provider call. Returns the base branch for GitHub, "" for a device folder. */
    suspend fun prepare(ws: WorkspaceEntity): Result<String>
    suspend fun start(ws: WorkspaceEntity, ref: String, mode: CodeMode, history: List<AgentMessage>, seed: List<WorkspaceChange>, projectId: Long? = null): CodeRun
    /** Applies changes for real: SAF writes into the folder, GitHub commits to [head] (created from [base]). */
    suspend fun apply(ws: WorkspaceEntity, base: String, head: String, changes: List<WorkspaceChange>, message: String): Result<WorkspaceApplyResult>
    suspend fun openPullRequest(ws: WorkspaceEntity, base: String, head: String, title: String, body: String): Result<GitHubPullRequest>
}

/** Code tools are read-only or stage-only, so nothing ever needs the runner's per-call approval. */
private object NoMutatingTools : ToolApprovalGate {
    override suspend fun await(call: PendingToolCall) = ToolDecision.DENY
}

fun codeRun(provider: ProviderAgentClient, gateway: WorkspaceGateway, mode: CodeMode, request: AgentRequest, seed: List<WorkspaceChange>): CodeRun {
    val overlay = WorkspaceOverlay(gateway)
    seed.forEach(overlay::seed)
    return CodeRun(AgentRunner(provider, NoMutatingTools).run(request, codeTools(overlay, mode)), overlay::changes)
}

fun codeSystemPrompt(ws: WorkspaceEntity, ref: String, mode: CodeMode, instructions: String): String = buildString {
    if (ws.kind == "gh") append("Ты — агент для работы с кодом в GitHub-репозитории ${ws.path} (ветка $ref).\n")
    else append("Ты — агент для работы с файлами в папке «${ws.name}» на устройстве пользователя.\n")
    append("Инструменты: workspace_list (список файлов), workspace_read (чтение текста)")
    if (mode != CodeMode.PLAN) append(", workspace_write (полное новое содержимое файла)")
    append(". Пути — относительно корня рабочей области.\n")
    append("Ты не можешь запускать команды, сборку или тесты: никогда не утверждай, что запускал их.\n")
    append(
        when (mode) {
            CodeMode.PLAN -> "Режим «План»: только изучи файлы и предложи план. Ничего не записывай.\n"
            CodeMode.EDITS -> "Режим «Правки»: изменения применятся только после подтверждения пользователем.\n"
            CodeMode.AUTO -> "Режим «Авто»: подготовленные изменения применятся автоматически в конце задачи.\n"
        },
    )
    append("Сначала прочитай нужные файлы, потом меняй. В конце кратко по-русски опиши, что сделано и почему.")
    if (instructions.isNotBlank()) append("\n\nИнструкции пользователя:\n").append(instructions.trim())
}

/** Head branch for a GitHub session: `claude/<latin slug>-<id>`. */
fun headBranch(title: String, sessionId: Long): String {
    val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30).trim('-').ifEmpty { "task" }
    return "claude/$slug-$sessionId"
}

class DefaultCodeAgent(
    private val context: Context,
    private val http: HttpClient,
    private val settings: SettingsRepository,
    private val provider: ProviderAgentClient,
    private val memory: suspend (Boolean, Long?) -> String = { _, _ -> "" },
    private val instructions: suspend () -> String = { "" },
) : CodeAgent {
    private suspend fun token() = settings.flow.first().githubToken.trim()

    private suspend fun github(ws: WorkspaceEntity, ref: String) = GitHubWorkspaceGateway(http, ws.path, token(), ref)

    private fun device(ws: WorkspaceEntity): Result<SafWorkspaceGateway> = runCatching { SafWorkspaceGateway(context, Uri.parse(ws.path)) }
        .recoverCatching { throw WorkspaceException("Папка недоступна — выберите её заново", recoverable = true) }

    private suspend fun gateway(ws: WorkspaceEntity, ref: String): Result<WorkspaceGateway> =
        if (ws.kind == "gh") Result.success(github(ws, ref)) else device(ws)

    suspend fun files(ws: WorkspaceEntity, ref: String): Result<CodeFiles> {
        val branch = if (ws.kind == "gh" && ref.isBlank()) github(ws, "").defaultBranch()
            .getOrElse { return Result.failure(it) } else ref
        return gateway(ws, branch).map(::CodeFiles)
    }

    override suspend fun prepare(ws: WorkspaceEntity): Result<String> =
        if (ws.kind == "gh") github(ws, "").readableDefaultBranch()
        else if (context.contentResolver.persistedUriPermissions.none { it.isReadPermission && it.uri.toString() == ws.path }) {
            Result.failure(WorkspaceException("Доступ к папке не сохранён — выберите её заново", recoverable = true))
        } else device(ws).mapCatching { it.list(WorkspacePath.ROOT).getOrThrow(); "" }

    override suspend fun start(ws: WorkspaceEntity, ref: String, mode: CodeMode, history: List<AgentMessage>, seed: List<WorkspaceChange>, projectId: Long?): CodeRun {
        val s = settings.flow.first()
        val gw = gateway(ws, ref).getOrThrow()
        val request = AgentRequest(s.model, maxTokens(s.effort), codeSystemPrompt(ws, ref, mode, s.instructions) + instructions() + memory(s.on("p2"), projectId), history, s.on("web"))
        return codeRun(provider, gw, mode, request, seed)
    }

    override suspend fun apply(ws: WorkspaceEntity, base: String, head: String, changes: List<WorkspaceChange>, message: String): Result<WorkspaceApplyResult> {
        if (ws.kind != "gh") return device(ws).mapCatching { it.apply(changes, message).getOrThrow() }
        if (token().isEmpty()) return Result.failure(WorkspaceException("Для коммита нужен токен GitHub: «Настройки → Подключения → Токен GitHub»", recoverable = true))
        val gw = github(ws, base)
        return gw.createBranch(base, head).mapCatching { gw.onBranch(head).apply(changes, message).getOrThrow() }
    }

    override suspend fun openPullRequest(ws: WorkspaceEntity, base: String, head: String, title: String, body: String): Result<GitHubPullRequest> {
        if (ws.kind != "gh") return Result.failure(WorkspaceException("PR доступен только для GitHub-репозиториев"))
        if (token().isEmpty()) return Result.failure(WorkspaceException("Для PR нужен токен GitHub", recoverable = true))
        return github(ws, base).openPullRequest(base, head, title, body)
    }
}
