package app.clodex.harness

import app.clodex.harness.data.agent.*
import app.clodex.harness.data.mcp.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class McpRememberedApprovalTest {
    private fun gateway() = FakeMcpGateway(Result.failure(McpProtocolException("unused")))
    private val tool = McpToolSpec("write", "Change a record")
    private val config = McpConnectionConfig("acme", "https://example.com/mcp", tools = listOf(tool))

    @Test fun grantIsScopedToChatToolAndConnectionAndCanBeRevoked() = runBlocking {
        val store = FakeMcpConfigRepository()
        store.save(config)
        val repo = McpRepository(store, gateway())
        val permission = repo.enabledTools().single().permission
        assertFalse(repo.isAllowed(permission, 10))
        assertTrue(repo.remember(permission, 10))
        assertTrue(repo.isAllowed(permission, 10))
        assertFalse(repo.isAllowed(permission, 11))
        val roundTrip = McpConfigCodec.decode(McpConfigCodec.encode(store.configs.first()))
        assertTrue(McpRepository(FakeMcpConfigRepository(roundTrip), gateway()).isAllowed(permission, 10))
        repo.revoke(permission, 10)
        assertFalse(repo.isAllowed(permission, 10))
        repo.remember(permission, 10)
        repo.setEnabled("acme", false)
        repo.setEnabled("acme", true)
        assertFalse(repo.isAllowed(permission, 10))
        repo.remember(permission, 10)
        store.save(store.get("acme")!!.copy(endpoint = "https://other.example/mcp"))
        assertFalse(repo.isAllowed(permission, 10))
        assertFalse(repo.remember(permission, 10))
    }

    @Test fun toolChangeInvalidatesSnapshotAndPreventsCallingNewConnection() = runBlocking {
        val store = FakeMcpConfigRepository()
        store.save(config)
        val gateway = gateway()
        val repo = McpRepository(store, gateway)
        val snapshot = repo.enabledTools().single()
        repo.remember(snapshot.permission, 10)
        store.save(store.get("acme")!!.copy(tools = listOf(tool.copy(description = "Different action"))))
        assertFalse(repo.isAllowed(snapshot.permission, 10))
        assertTrue(repo.call(snapshot.namespacedName, JsonObject(emptyMap()), snapshot.permission).isFailure)
        assertEquals(0, gateway.calls)
    }

    @Test fun rememberedDecisionSkipsPromptOnlyInSameChatAndRevocationRestoresIt() = runBlocking {
        val store = FakeMcpConfigRepository()
        store.save(config)
        val repo = McpRepository(store, gateway())
        val permission = repo.enabledTools().single().permission
        val pending = PendingToolCall(ProviderToolCall("c", "acme__write", JsonObject(emptyMap())), "Acme · write", "Acme", "write", permission)
        val ui = FixedGate(ToolDecision.ALLOW_CHAT)
        assertEquals(ToolDecision.ALLOW, RememberingApprovalGate(10, repo, ui).await(pending))
        assertEquals(1, ui.asked.size)
        assertEquals(ToolDecision.ALLOW, RememberingApprovalGate(10, repo, ui).await(pending))
        assertEquals(1, ui.asked.size)
        val deny = FixedGate(ToolDecision.DENY)
        assertEquals(ToolDecision.DENY, RememberingApprovalGate(11, repo, deny).await(pending))
        repo.revoke(permission, 10)
        assertEquals(ToolDecision.DENY, RememberingApprovalGate(10, repo, deny).await(pending))
        assertEquals(2, deny.asked.size)
    }

    @Test fun rememberFailureStillAllowsTheCurrentCallOnce() = runBlocking {
        val store = FakeMcpConfigRepository(mapOf("acme" to config))
        val repo = McpRepository(store, gateway())
        val permission = repo.enabledTools().single().permission
        val pending = PendingToolCall(ProviderToolCall("c", "acme__write", JsonObject(emptyMap())), "Acme · write", "Acme", "write", permission)
        val ui = FixedGate(ToolDecision.ALLOW_CHAT)
        assertEquals(ToolDecision.ALLOW, RememberingApprovalGate(0, repo, ui).await(pending))
        assertFalse(repo.isAllowed(permission, 0))
    }

    @Test fun credentialChangeDoesNotCarryApprovalToAnotherAccount() = runBlocking {
        val store = FakeMcpConfigRepository(mapOf("acme" to config.copy(authMode = McpAuthMode.BEARER, bearerToken = "account-a")))
        val repo = McpRepository(store, gateway())
        val permission = repo.enabledTools().single().permission
        repo.remember(permission, 10)
        store.save(store.get("acme")!!.copy(bearerToken = "account-b"))
        assertFalse(repo.isAllowed(permission, 10))
        val replacement = repo.enabledTools().single().permission
        assertNotEquals(permission, replacement)
        assertFalse(repo.isAllowed(replacement, 10))
        assertFalse(McpConfigCodec.encode(store.configs.first()).contains("account-a"))
    }
}
