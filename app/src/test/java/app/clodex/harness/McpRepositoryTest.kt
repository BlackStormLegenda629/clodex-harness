package app.clodex.harness

import app.clodex.harness.data.mcp.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMcpConfigRepository(initial: Map<String, McpConnectionConfig> = emptyMap()) : McpConfigRepository {
    val state = MutableStateFlow(initial)
    override val configs = state
    override suspend fun get(id: String) = state.value[id]
    override suspend fun save(config: McpConnectionConfig) { state.value = state.value + (config.connectorId to config) }
    override suspend fun remove(id: String) { state.value = state.value - id }
}

class FakeMcpGateway(var probeResult: Result<McpProbe>) : McpGateway {
    var probes = 0
    var calls = 0
    var lastTool: String? = null
    override suspend fun probe(config: McpConnectionConfig): Result<McpProbe> { probes++; return probeResult }
    override suspend fun call(config: McpConnectionConfig, tool: String, arguments: JsonObject): Result<McpCallResult> {
        calls++; lastTool = tool; return Result.success(McpCallResult("ok"))
    }
}

class McpRepositoryTest {
    @Test fun gmailMustSucceedAtAnAuthenticatedReadBeforeEnablingTools() = runBlocking {
        val store = FakeMcpConfigRepository()
        var reply = McpCallResult("", isError = true)
        val gateway = object : McpGateway {
            override suspend fun probe(config: McpConnectionConfig) = Result.success(probe.copy(tools = listOf(
                McpToolSpec("list_labels", readOnlyHint = true), McpToolSpec("send_email"),
            )))
            override suspend fun call(config: McpConnectionConfig, tool: String, arguments: JsonObject): Result<McpCallResult> {
                assertEquals("list_labels", tool)
                assertEquals("google-access", config.bearerToken)
                assertEquals("https://gmailmcp.googleapis.com/mcp/v1", config.endpoint)
                return Result.success(reply)
            }
        }
        val runtime = McpRepository(store, gateway)
        assertTrue(runtime.connectGmail("google-access").isFailure)
        assertTrue(runtime.enabledTools().isEmpty())
        assertEquals(null, store.get("gm"))
        for (bad in listOf("", "unrelated", "{\"labels\":[{}]}")) {
            reply = McpCallResult(bad)
            assertTrue("Should reject invalid output: $bad", runtime.connectGmail("google-access").isFailure)
            assertEquals(null, store.get("gm"))
        }
        reply = McpCallResult("{}") // Google omits the labels field when there are no results.
        runtime.connectGmail("google-access").getOrThrow()
        assertEquals(listOf("gm__list_labels"), runtime.enabledTools().map { it.namespacedName })
        reply = McpCallResult("", structured = kotlinx.serialization.json.Json.parseToJsonElement("""{"labels":[{"labelId":"INBOX","name":"INBOX"}]}"""))
        assertTrue(runtime.connectGmail("google-access").isSuccess)
    }

    private val probe = McpProbe(
        McpServerInfo("Server", "1"),
        listOf(McpToolSpec("search", "Find", buildJsonObject { put("type", "object") }, readOnlyHint = true)),
        "session", 77,
    )

    @Test fun oldManualGmailConfigCannotExposeOrCallTools() = runBlocking {
        val config = McpConnectionConfig("gm", "https://mcp.example", McpAuthMode.BEARER, tools = probe.tools)
        val gateway = FakeMcpGateway(Result.success(probe))
        val runtime = McpRepository(FakeMcpConfigRepository(mapOf("gm" to config)), gateway)
        assertTrue(runtime.enabledTools().isEmpty())
        assertTrue(runtime.call("gm__search", JsonObject(emptyMap())).isFailure)
        assertTrue(runtime.probeAndSave("gm", config).isFailure)
        assertEquals(0, gateway.calls + gateway.probes)
    }

    @Test fun successfulProbePersistsVerifiedMetadataAndEnablesTools() = runBlocking {
        val store = FakeMcpConfigRepository()
        val runtime = McpRepository(store, FakeMcpGateway(Result.success(probe)))
        val input = McpConnectionConfig("nt", "https://mcp.example")

        runtime.probeAndSave("nt", input).getOrThrow()

        val saved = store.get("nt")!!
        assertTrue(saved.enabled)
        assertEquals("Server", saved.serverName)
        assertEquals(77, saved.lastCheckedAt)
        assertEquals("search", saved.tools.single().name)
        assertEquals("nt__search", runtime.enabledTools().single().namespacedName)
    }

    @Test fun failedProbeDisablesConfigAndKeepsSafeError() = runBlocking {
        val old = McpConnectionConfig("nt", "https://mcp.example", enabled = true, serverName = "Old")
        val store = FakeMcpConfigRepository(mapOf("nt" to old))
        val runtime = McpRepository(store, FakeMcpGateway(Result.failure(McpProtocolException("offline"))))

        assertTrue(runtime.probeAndSave("nt", old).isFailure)

        val saved = store.get("nt")!!
        assertFalse(saved.enabled)
        assertEquals("offline", saved.lastError)
        assertTrue(runtime.enabledTools().isEmpty())
    }

    @Test fun namespacedCallsRouteToOriginalToolAndDisabledToolsAreRejected() = runBlocking {
        val verified = McpConnectionConfig("sl", "https://mcp.example", enabled = true, tools = probe.tools)
        val store = FakeMcpConfigRepository(mapOf("sl" to verified))
        val gateway = FakeMcpGateway(Result.success(probe))
        val runtime = McpRepository(store, gateway)

        assertEquals("ok", runtime.call("sl__search", JsonObject(emptyMap())).getOrThrow().text)
        assertEquals("search", gateway.lastTool)
        runtime.setEnabled("sl", false)
        assertTrue(runtime.call("sl__search", JsonObject(emptyMap())).isFailure)
    }
}
