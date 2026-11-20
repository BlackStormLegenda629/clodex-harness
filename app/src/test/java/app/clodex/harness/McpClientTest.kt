package app.clodex.harness

import app.clodex.harness.data.mcp.KtorMcpGateway
import app.clodex.harness.data.mcp.McpAuthMode
import app.clodex.harness.data.mcp.McpConnectionConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpClientTest {
    private data class Seen(val request: HttpRequestData, val body: String)

    @Test fun expiredSessionIsReinitializedOnce() = runBlocking {
        var inits = 0
        var calls = 0
        val gateway = KtorMcpGateway(HttpClient(MockEngine { req ->
            val body = kotlinx.serialization.json.Json.parseToJsonElement((req.body as TextContent).text) as kotlinx.serialization.json.JsonObject
            val id = body["id"]
            fun reply(result: String) = """{"jsonrpc":"2.0","id":$id,"result":$result}"""
            when (body["method"].toString()) {
                "\"initialize\"" -> respond(reply("""{"protocolVersion":"2025-03-26"}"""), headers = headersOf("Mcp-Session-Id", "session-${++inits}"))
                "\"notifications/initialized\"" -> respond("", HttpStatusCode.Accepted)
                "\"tools/list\"" -> respond(reply("""{"tools":[]}"""))
                else -> {
                    assertEquals("2025-03-26", req.headers["MCP-Protocol-Version"])
                    assertEquals("session-$inits", req.headers["Mcp-Session-Id"])
                    assertEquals("Bearer oauth-access", req.headers[HttpHeaders.Authorization])
                    if (++calls == 1) respond("", HttpStatusCode.NotFound) else respond(reply("{}"))
                }
            }
        }))
        val cfg = McpConnectionConfig("nt", "https://mcp.example", oauth = app.clodex.harness.data.mcp.McpOAuthTokens("client", "https://mcp.example/token", "https://mcp.example", access = "oauth-access"))
        gateway.call(cfg, "read", buildJsonObject {}).getOrThrow()
        assertEquals(2, inits)
        assertEquals(2, calls)
    }

    @Test fun probeInitializesSessionAndDiscoversToolsThenCallReusesSession() = runBlocking {
        val seen = mutableListOf<Seen>()
        val replies = ArrayDeque(
            listOf(
                Triple(HttpStatusCode.OK, """{"jsonrpc":"2.0","id":1,"result":{"serverInfo":{"name":"Acme","version":"1.2"},"capabilities":{}}}""", "sess-7"),
                Triple(HttpStatusCode.Accepted, "", null),
                Triple(HttpStatusCode.OK, """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"find","description":"Search","inputSchema":{"type":"object"},"annotations":{"readOnlyHint":true}}]}}""", null),
                Triple(HttpStatusCode.OK, """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"found"}],"isError":false}}""", null),
            ),
        )
        val engine = MockEngine { req ->
            seen += Seen(req, (req.body as? TextContent)?.text.orEmpty())
            val (status, body, session) = replies.removeFirst()
            val headers = if (session != null) headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                "Mcp-Session-Id" to listOf(session),
            ) else headersOf(HttpHeaders.ContentType, "application/json")
            respond(body, status, headers)
        }
        val gateway = KtorMcpGateway(HttpClient(engine), clock = { 99L })
        val config = McpConnectionConfig("gm", "https://mcp.example/rpc", McpAuthMode.BEARER, "secret-token")

        val probe = gateway.probe(config).getOrThrow()
        val call = gateway.call(config, "find", buildJsonObject { put("q", "mail") }).getOrThrow()

        assertEquals("Acme", probe.server.name)
        assertEquals(99L, probe.checkedAt)
        assertEquals("find", probe.tools.single().name)
        assertTrue(probe.tools.single().readOnlyHint)
        assertEquals("found", call.text)
        assertEquals(4, seen.size)
        assertTrue(seen.first().body.contains("initialize"))
        assertTrue(seen[1].body.contains("notifications/initialized"))
        assertTrue(seen[2].body.contains("tools/list"))
        assertTrue(seen[3].body.contains("tools/call"))
        assertEquals("sess-7", seen[2].request.headers["Mcp-Session-Id"])
        assertEquals("sess-7", seen[3].request.headers["Mcp-Session-Id"])
        assertEquals("Bearer secret-token", seen.first().request.headers[HttpHeaders.Authorization])
        assertTrue(seen.first().request.headers[HttpHeaders.Accept].orEmpty().contains("text/event-stream"))
    }

    @Test fun nonSuccessResponseDoesNotLeakBearerToken() = runBlocking {
        val engine = MockEngine { respond("token=top-secret", HttpStatusCode.Unauthorized) }
        val gateway = KtorMcpGateway(HttpClient(engine))
        val config = McpConnectionConfig("x", "https://mcp.example", McpAuthMode.BEARER, "top-secret")

        val error = gateway.probe(config).exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("401"))
        assertFalse(error?.message.orEmpty().contains("top-secret"))
    }
}
