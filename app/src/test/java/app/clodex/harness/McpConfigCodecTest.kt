package app.clodex.harness

import app.clodex.harness.data.mcp.McpAuthMode
import app.clodex.harness.data.mcp.McpConfigCodec
import app.clodex.harness.data.mcp.McpConnectionConfig
import app.clodex.harness.data.mcp.McpToolSpec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConfigCodecTest {
    @Test fun roundTripsEndpointAuthHeadersAndDiscoveredTools() {
        val config = McpConnectionConfig(
            connectorId = "sl",
            endpoint = "https://mcp.example/rpc",
            authMode = McpAuthMode.BEARER,
            bearerToken = "tok\"en\nline",
            headers = mapOf("X-Team" to "mobile"),
            lastCheckedAt = 42,
            serverName = "Example",
            serverVersion = "1.0",
            tools = listOf(McpToolSpec("search", "Search", buildJsonObject { put("type", "object") }, true)),
            lastError = "old error",
        )

        val decoded = McpConfigCodec.decode(McpConfigCodec.encode(mapOf(config.connectorId to config)))

        assertEquals(config, decoded["sl"])
    }

    @Test fun ignoresUnknownFieldsAndDropsInvalidEntries() {
        val raw = """{"future":1,"configs":[{"connectorId":"ok","endpoint":"https://x","unknown":true},{"endpoint":"missing-id"}]}"""
        val decoded = McpConfigCodec.decode(raw)

        assertEquals(setOf("ok"), decoded.keys)
        assertEquals(McpAuthMode.NONE, decoded["ok"]?.authMode)
    }

    @Test fun malformedOrBlankStorageBecomesEmptyMap() {
        assertTrue(McpConfigCodec.decode("").isEmpty())
        assertTrue(McpConfigCodec.decode("not-json").isEmpty())
    }

    @Test fun headerLinesParseNameValuePairsAndSkipJunk() {
        val parsed = app.clodex.harness.data.mcp.McpHeaders.parse("X-Team: mobile\n\nbad line\nX-Key:  a:b \n: empty")
        assertEquals(mapOf("X-Team" to "mobile", "X-Key" to "a:b"), parsed)
        assertEquals("X-Key: a:b\nX-Team: mobile", app.clodex.harness.data.mcp.McpHeaders.format(parsed))
    }
}
