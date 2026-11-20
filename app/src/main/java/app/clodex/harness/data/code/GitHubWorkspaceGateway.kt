package app.clodex.harness.data.code

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.util.Base64

data class GitHubPullRequest(val number: Int, val url: String)

/**
 * A GitHub repository as a workspace. Reads come from [branch]; [apply] makes one real Git data commit
 * (tree → commit → ref update) on [branch]. Nothing is reported unless GitHub answered with success.
 */
class GitHubWorkspaceGateway(
    private val http: HttpClient,
    val repo: String,
    private val token: String,
    val branch: String,
) : WorkspaceGateway {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val api = "https://api.github.com/repos/$repo"

    fun onBranch(b: String) = GitHubWorkspaceGateway(http, repo, token, b)

    private class GitHubHttpException(val status: Int, message: String) : Exception(message)

    private suspend fun call(method: HttpMethod, path: String, body: JsonObject? = null, query: Map<String, String> = emptyMap()): JsonElement {
        val r: HttpResponse = http.request(api + path) {
            this.method = method
            query.forEach { (k, v) -> parameter(k, v) }
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            header("User-Agent", "ClodexHarness")
            if (token.isNotBlank()) header("Authorization", "Bearer $token")
            if (body != null) { contentType(ContentType.Application.Json); setBody(body.toString()) }
        }
        val packet = r.bodyAsChannel().readRemaining(MAX_RESPONSE + 1L)
        val bytes = packet.readBytes()
        if (bytes.size > MAX_RESPONSE) throw WorkspaceException("Ответ GitHub больше ${MAX_RESPONSE / 1024 / 1024} МБ")
        val text = String(bytes, Charsets.UTF_8)
        if (r.status.value !in 200..299) {
            throw GitHubHttpException(r.status.value, gitHubErrorText(r.status.value, text, r.headers["x-ratelimit-remaining"], token))
        }
        return if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
    }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: GitHubHttpException) {
        Result.failure(WorkspaceException(e.message!!, recoverable = e.status == 401 || e.status == 403, notFound = e.status == 404))
    } catch (e: WorkspaceException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(WorkspaceException(redact("Нет соединения с GitHub: ${e.message ?: e::class.simpleName}", token)))
    }

    private fun enc(path: WorkspacePath) = path.segments.joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    private fun refPath(b: String) = b.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    override suspend fun list(path: WorkspacePath): Result<List<WorkspaceEntry>> = guarded {
        val res = call(HttpMethod.Get, "/contents/" + enc(path), query = mapOf("ref" to branch))
        val arr = res as? JsonArray ?: throw WorkspaceException("$path — это файл, а не папка")
        arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o.s("name") ?: return@mapNotNull null
            val p = path.child(name).getOrNull() ?: return@mapNotNull null
            val dir = o.s("type") == "dir"
            val size = o.l("size")
            WorkspaceEntry(p, dir, size, !dir && WorkspacePolicy.canReadText(name, "", size))
        }.sortedWith(compareBy({ !it.dir }, { it.path.name.lowercase() })).take(WorkspaceLimits.MAX_LIST)
    }

    override suspend fun read(path: WorkspacePath): Result<WorkspaceRead> = guarded {
        if (path.isRoot) throw WorkspaceException("Корень репозитория — это папка")
        val o = call(HttpMethod.Get, "/contents/" + enc(path), query = mapOf("ref" to branch)) as? JsonObject
            ?: throw WorkspaceException("$path — это папка, а не файл")
        if (o.s("type") != "file") throw WorkspaceException("$path — не обычный файл (${o.s("type")})")
        val size = o.l("size")
        if (!WorkspacePolicy.canReadText(path.name, "", size)) return@guarded WorkspaceRead(path, null, size, WorkspacePolicy.unreadableReason(path.name, size))
        val content = o.s("content")
        if (o.s("encoding") != "base64" || content == null) return@guarded WorkspaceRead(path, null, size, "GitHub не вернул содержимое")
        val bytes = Base64.getMimeDecoder().decode(content)
        if (WorkspacePolicy.looksBinary(bytes)) WorkspaceRead(path, null, size, "двоичный файл")
        else WorkspaceRead(path, String(bytes, Charsets.UTF_8), size)
    }

    suspend fun defaultBranch(): Result<String> = guarded {
        (call(HttpMethod.Get, "") as JsonObject).s("default_branch") ?: throw WorkspaceException("GitHub не вернул ветку по умолчанию")
    }

    suspend fun readableDefaultBranch(): Result<String> {
        val base = defaultBranch().getOrElse { return Result.failure(it) }
        return onBranch(base).list(WorkspacePath.ROOT).map { base }
    }

    private suspend fun headSha(b: String): String =
        (call(HttpMethod.Get, "/git/ref/heads/" + refPath(b)) as JsonObject).obj("object")?.s("sha")
            ?: throw WorkspaceException("GitHub не вернул SHA ветки $b")

    /** Creates [head] from [base]; an existing [head] is reused. Returns the head commit SHA. */
    suspend fun createBranch(base: String, head: String): Result<String> = guarded {
        val sha = headSha(base)
        try {
            (call(HttpMethod.Post, "/git/refs", buildJsonObject { put("ref", "refs/heads/$head"); put("sha", sha) }) as JsonObject)
                .obj("object")?.s("sha") ?: sha
        } catch (e: GitHubHttpException) {
            if (e.status != 422) throw e
            headSha(head)
        }
    }

    /** File modes of existing paths, so executables and symlinks are not silently changed. */
    private suspend fun modes(rootTree: String, paths: List<WorkspacePath>): Map<WorkspacePath, String> {
        val trees = mutableMapOf<WorkspacePath, Map<String, Pair<String, String>>?>() // dir -> name -> (mode, sha)
        suspend fun entries(dir: WorkspacePath): Map<String, Pair<String, String>>? {
            if (dir in trees) return trees[dir]
            val sha = if (dir.isRoot) rootTree else entries(dir.parent)?.get(dir.name)?.takeIf { it.first == "040000" }?.second
            val value = sha?.let { s ->
                (call(HttpMethod.Get, "/git/trees/$s") as JsonObject).arr("tree")?.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val n = o.s("path") ?: return@mapNotNull null
                    n to ((o.s("mode") ?: "100644") to o.s("sha").orEmpty())
                }?.toMap()
            }
            trees[dir] = value
            return value
        }
        return paths.mapNotNull { p -> entries(p.parent)?.get(p.name)?.let { p to it.first } }.toMap()
    }

    override suspend fun apply(changes: List<WorkspaceChange>, message: String): Result<WorkspaceApplyResult> = guarded {
        if (changes.isEmpty()) return@guarded WorkspaceApplyResult(emptyList(), branch = branch)
        if (changes.size > WorkspaceLimits.MAX_CHANGES) throw WorkspaceException("Больше ${WorkspaceLimits.MAX_CHANGES} файлов в одном коммите")
        changes.forEach { ch -> WorkspacePolicy.writeError(ch.path.value, ch.after.toByteArray().size)?.let { throw WorkspaceException("${ch.path}: $it") } }
        val parent = headSha(branch)
        val baseTree = (call(HttpMethod.Get, "/git/commits/$parent") as JsonObject).obj("tree")?.s("sha")
            ?: throw WorkspaceException("GitHub не вернул дерево коммита")
        val modes = modes(baseTree, changes.map { it.path })
        val tree = call(HttpMethod.Post, "/git/trees", buildJsonObject {
            put("base_tree", baseTree)
            putJsonArray("tree") {
                changes.forEach { ch ->
                    val mode = modes[ch.path] ?: "100644"
                    if (mode != "100644" && mode != "100755") throw WorkspaceException("${ch.path}: ссылка или подмодуль, запись запрещена")
                    addJsonObject { put("path", ch.path.value); put("mode", mode); put("type", "blob"); put("content", ch.after) }
                }
            }
        }) as JsonObject
        val treeSha = tree.s("sha") ?: throw WorkspaceException("GitHub не вернул SHA дерева")
        val commit = call(HttpMethod.Post, "/git/commits", buildJsonObject {
            put("message", message); put("tree", treeSha); putJsonArray("parents") { add(parent) }
        }) as JsonObject
        val commitSha = commit.s("sha") ?: throw WorkspaceException("GitHub не вернул SHA коммита")
        call(HttpMethod.Patch, "/git/refs/heads/" + refPath(branch), buildJsonObject { put("sha", commitSha); put("force", false) })
        WorkspaceApplyResult(changes.map { it.path }, commitSha = commitSha, branch = branch)
    }

    suspend fun openPullRequest(base: String, head: String, title: String, body: String): Result<GitHubPullRequest> = guarded {
        val o = call(HttpMethod.Post, "/pulls", buildJsonObject { put("title", title); put("head", head); put("base", base); put("body", body) }) as JsonObject
        val number = (o["number"] as? JsonPrimitive)?.intOrNull ?: throw WorkspaceException("GitHub не вернул номер PR")
        GitHubPullRequest(number, o.s("html_url").orEmpty())
    }

    companion object {
        const val MAX_RESPONSE = 4 * 1024 * 1024
    }
}

private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.l(k: String) = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L
private fun JsonObject.obj(k: String) = this[k] as? JsonObject
private fun JsonObject.arr(k: String) = this[k] as? JsonArray

internal fun redact(text: String, token: String): String = if (token.length < 4) text else text.replace(token, "***")

/** Human error for a GitHub status; the token never appears in the text. */
internal fun gitHubErrorText(status: Int, body: String, rateRemaining: String?, token: String): String {
    val obj = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    val detail = listOfNotNull(
        (obj?.get("message") as? JsonPrimitive)?.contentOrNull,
        ((obj?.get("errors") as? JsonArray)?.firstOrNull() as? JsonObject)?.let { (it["message"] as? JsonPrimitive)?.contentOrNull },
    ).joinToString(": ").take(300)
    val text = when {
        status == 401 -> "Токен GitHub недействителен или отозван. Обновите его в «Настройки → Подключения»"
        status == 429 || (status == 403 && rateRemaining == "0") -> "Лимит GitHub API исчерпан, попробуйте позже"
        status == 403 -> "Нет прав: токену нужен доступ на запись (Contents и Pull requests)"
        status == 404 -> "Не найдено в GitHub: репозиторий, ветка или файл (или токен не видит приватный репозиторий)"
        status == 409 -> "Конфликт в GitHub: ветка изменилась или репозиторий пуст"
        status == 422 -> "GitHub отклонил запрос"
        else -> "GitHub вернул $status"
    }
    return redact(if (detail.isNotBlank() && status != 401) "$text ($detail)" else text, token)
}
