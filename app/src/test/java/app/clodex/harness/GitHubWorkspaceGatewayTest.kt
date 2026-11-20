package app.clodex.harness

import app.clodex.harness.data.code.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class GitHubWorkspaceGatewayTest {
    private data class Seen(val method: HttpMethod, val path: String, val body: String, val auth: String?, val query: String)

    private val token = "ghp_SECRET123"
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun gateway(seen: MutableList<Seen>, branch: String = "main", route: MockRequestHandleScope.(Seen) -> HttpResponseData): GitHubWorkspaceGateway {
        val engine = MockEngine { req: HttpRequestData ->
            val s = Seen(req.method, req.url.encodedPath, (req.body as? TextContent)?.text.orEmpty(), req.headers["Authorization"], req.url.encodedQuery)
            seen += s
            route(s)
        }
        return GitHubWorkspaceGateway(HttpClient(engine), "acme/app", token, branch)
    }

    private fun MockRequestHandleScope.ok(body: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(body, status, jsonHeaders)
    private fun p(s: String) = WorkspacePath.parse(s).getOrThrow()

    @Test fun `workspace probe rejects metadata only access`() = runBlocking {
        val seen = mutableListOf<Seen>()
        val gw = gateway(seen) { s ->
            if (s.path == "/repos/acme/app") ok("""{"default_branch":"trunk"}""")
            else ok("""{"message":"Resource not accessible"}""", HttpStatusCode.Forbidden)
        }
        assertTrue(gw.readableDefaultBranch().isFailure)
        assertEquals("/repos/acme/app/contents/", seen.last().path)
        assertEquals("ref=trunk", seen.last().query)
    }

    @Test fun `lists a folder and reads base64 files from the branch`() = runBlocking {
        val seen = mutableListOf<Seen>()
        val content = Base64.getMimeEncoder().encodeToString("fun main() = println(\"привет\")\n".toByteArray())
        val gw = gateway(seen) { s ->
            when (s.path) {
                "/repos/acme/app/contents/src" -> ok("""[{"name":"Main.kt","path":"src/Main.kt","type":"file","size":30},{"name":"res","type":"dir","size":0},{"name":"logo.png","type":"file","size":10}]""")
                "/repos/acme/app/contents/src/Main.kt" -> ok("""{"type":"file","size":30,"encoding":"base64","content":${JsonPrimitive(content)}}""")
                else -> ok("{}", HttpStatusCode.NotFound)
            }
        }
        val list = gw.list(p("src")).getOrThrow()
        assertEquals(listOf("src/res", "src/logo.png", "src/Main.kt"), list.map { it.path.value })
        assertTrue(list.first().dir)
        assertFalse(list.first { it.path.name == "logo.png" }.text)
        val read = gw.read(p("src/Main.kt")).getOrThrow()
        assertEquals("fun main() = println(\"привет\")\n", read.text)
        assertTrue(seen.all { it.query == "ref=main" })
        assertEquals("Bearer $token", seen.first().auth)
    }

    @Test fun `oversized and binary files are not decoded`() = runBlocking {
        val gw = gateway(mutableListOf()) { s ->
            when (s.path) {
                "/repos/acme/app/contents/big.txt" -> ok("""{"type":"file","size":900000,"encoding":"none","content":""}""")
                else -> ok("""{"type":"file","size":3,"encoding":"base64","content":"AAEC"}""")
            }
        }
        val big = gw.read(p("big.txt")).getOrThrow()
        assertNull(big.text); assertEquals("больше 256 КБ", big.reason)
        val bin = gw.read(p("data.txt")).getOrThrow()
        assertNull(bin.text); assertEquals("двоичный файл", bin.reason)
    }

    @Test fun `create branch reuses an existing head on 422`() = runBlocking {
        val seen = mutableListOf<Seen>()
        val gw = gateway(seen) { s ->
            when {
                s.path.endsWith("/git/ref/heads/main") -> ok("""{"object":{"sha":"base111"}}""")
                s.path.endsWith("/git/refs") -> ok("""{"message":"Reference already exists"}""", HttpStatusCode.UnprocessableEntity)
                s.path.endsWith("/git/ref/heads/claude/fix") -> ok("""{"object":{"sha":"head222"}}""")
                else -> ok("{}", HttpStatusCode.NotFound)
            }
        }
        assertEquals("head222", gw.createBranch("main", "claude/fix").getOrThrow())
        assertTrue(seen.first { it.path.endsWith("/git/refs") }.body.contains("\"ref\":\"refs/heads/claude/fix\""))
    }

    @Test fun `apply makes one commit through tree, commit and ref update`() = runBlocking {
        val seen = mutableListOf<Seen>()
        val gw = gateway(seen, branch = "claude/fix") { s ->
            when {
                s.method == HttpMethod.Get && s.path.endsWith("/git/ref/heads/claude/fix") -> ok("""{"object":{"sha":"parent1"}}""")
                s.method == HttpMethod.Get && s.path.endsWith("/git/commits/parent1") -> ok("""{"tree":{"sha":"tree0"}}""")
                s.method == HttpMethod.Get && s.path.endsWith("/git/trees/tree0") -> ok("""{"tree":[{"path":"gradlew","mode":"100755","type":"blob","sha":"g"},{"path":"src","mode":"040000","type":"tree","sha":"srcT"}]}""")
                s.method == HttpMethod.Get && s.path.endsWith("/git/trees/srcT") -> ok("""{"tree":[{"path":"App.kt","mode":"100644","type":"blob","sha":"a"}]}""")
                s.method == HttpMethod.Post && s.path.endsWith("/git/trees") -> ok("""{"sha":"tree1"}""", HttpStatusCode.Created)
                s.method == HttpMethod.Post && s.path.endsWith("/git/commits") -> ok("""{"sha":"commit9"}""", HttpStatusCode.Created)
                s.method == HttpMethod.Patch && s.path.endsWith("/git/refs/heads/claude/fix") -> ok("""{"object":{"sha":"commit9"}}""")
                else -> ok("{}", HttpStatusCode.NotFound)
            }
        }
        val r = gw.apply(
            listOf(WorkspaceChange(p("src/App.kt"), "old", "new"), WorkspaceChange(p("gradlew"), "#!", "#!/bin/sh"), WorkspaceChange(p("docs/NEW.md"), null, "# hi")),
            "Fix login",
        ).getOrThrow()
        assertEquals("commit9", r.commitSha)
        assertEquals("claude/fix", r.branch)
        assertEquals(3, r.applied.size)
        val tree = Json.parseToJsonElement(seen.first { it.method == HttpMethod.Post && it.path.endsWith("/git/trees") }.body).jsonObject
        assertEquals("tree0", tree["base_tree"]!!.jsonPrimitive.content)
        val modes = tree["tree"]!!.jsonArray.associate { it.jsonObject["path"]!!.jsonPrimitive.content to it.jsonObject["mode"]!!.jsonPrimitive.content }
        assertEquals(mapOf("src/App.kt" to "100644", "gradlew" to "100755", "docs/NEW.md" to "100644"), modes)
        val commit = Json.parseToJsonElement(seen.first { it.path.endsWith("/git/commits") && it.method == HttpMethod.Post }.body).jsonObject
        assertEquals("parent1", commit["parents"]!!.jsonArray.single().jsonPrimitive.content)
        assertTrue(seen.last().body.contains("commit9"))
    }

    @Test fun `pull request propagates the real number and url`() = runBlocking {
        val seen = mutableListOf<Seen>()
        val gw = gateway(seen) { ok("""{"number":42,"html_url":"https://github.com/acme/app/pull/42"}""", HttpStatusCode.Created) }
        val pr = gw.openPullRequest("main", "claude/fix", "Fix", "body").getOrThrow()
        assertEquals(GitHubPullRequest(42, "https://github.com/acme/app/pull/42"), pr)
        assertTrue(seen.single().body.contains("\"head\":\"claude/fix\""))
    }

    @Test fun `pr conflict is surfaced without a number`() = runBlocking {
        val gw = gateway(mutableListOf()) {
            ok("""{"message":"Validation Failed","errors":[{"message":"A pull request already exists for acme:claude/fix."}]}""", HttpStatusCode.UnprocessableEntity)
        }
        val e = gw.openPullRequest("main", "claude/fix", "Fix", "").exceptionOrNull()
        assertTrue(e is WorkspaceException)
        assertTrue(e!!.message!!.contains("already exists"))
    }

    @Test fun `http errors are mapped and never contain the token`() = runBlocking {
        val cases = listOf(
            Triple(HttpStatusCode.Unauthorized, null, "недействителен"),
            Triple(HttpStatusCode.Forbidden, "0", "Лимит"),
            Triple(HttpStatusCode.Forbidden, "100", "Нет прав"),
            Triple(HttpStatusCode.NotFound, null, "Не найдено"),
            Triple(HttpStatusCode.Conflict, null, "Конфликт"),
            Triple(HttpStatusCode.TooManyRequests, null, "Лимит"),
        )
        for ((status, remaining, expected) in cases) {
            val engine = MockEngine {
                val h = if (remaining != null) headersOf(HttpHeaders.ContentType to listOf("application/json"), "x-ratelimit-remaining" to listOf(remaining)) else jsonHeaders
                respond("""{"message":"bad token $token"}""", status, h)
            }
            val e = GitHubWorkspaceGateway(HttpClient(engine), "acme/app", token, "main").read(p("a.txt")).exceptionOrNull()
            assertTrue("$status", e is WorkspaceException)
            assertTrue("$status: ${e!!.message}", e.message!!.contains(expected))
            assertFalse(e.message!!.contains(token))
        }
    }
}
