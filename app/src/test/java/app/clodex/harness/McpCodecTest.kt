package app.clodex.harness

import app.clodex.harness.data.mcp.McpCodec
import app.clodex.harness.data.mcp.McpProtocolException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class McpCodecTest {
    @Test fun requestUsesJsonRpcIdMethodAndParams() {
        val raw = McpCodec.request(7, "tools/list", buildJsonObject { put("cursor", "next") })
        val obj = McpCodec.json.parseToJsonElement(raw).jsonObject

        assertEquals("2.0", obj["jsonrpc"]?.jsonPrimitive?.content)
        assertEquals(7, obj["id"]?.jsonPrimitive?.content?.toInt())
        assertEquals("tools/list", obj["method"]?.jsonPrimitive?.content)
        assertEquals("next", obj["params"]?.jsonObject?.get("cursor")?.jsonPrimitive?.content)
    }

    @Test fun parsesPlainJsonResult() {
        val obj = McpCodec.parsePayload("application/json", """{"jsonrpc":"2.0","id":2,"result":{"ok":true}}""", 2)
        assertEquals(true, obj["ok"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test fun parsesMultilineSseDataAndSelectsExpectedId() {
        val body = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"skip":true}}

            event: message
            data: {"jsonrpc":"2.0","id":3,
            data: "result":{"name":"server"}}

        """.trimIndent()
        val obj = McpCodec.parsePayload("text/event-stream; charset=utf-8", body, 3)
        assertEquals("server", obj["name"]?.jsonPrimitive?.content)
    }

    @Test fun jsonRpcErrorsBecomeProtocolErrorsWithoutEchoingPayloadSecrets() {
        val body = """{"jsonrpc":"2.0","id":4,"error":{"code":-32600,"message":"bad request","data":{"token":"super-secret"}}}"""
        val error = assertThrows(McpProtocolException::class.java) {
            McpCodec.parsePayload("application/json", body, 4)
        }
        assertEquals("MCP -32600: bad request", error.message)
        assertFalse(error.message.orEmpty().contains("super-secret"))
    }

    @Test fun rejectsWrongIdsMalformedPayloadsAndOversizedBodies() {
        assertThrows(McpProtocolException::class.java) {
            McpCodec.parsePayload("application/json", """{"jsonrpc":"2.0","id":9,"result":{}}""", 8)
        }
        assertThrows(McpProtocolException::class.java) {
            McpCodec.parsePayload("text/event-stream", "data: nope\n\n", 1)
        }
        assertThrows(McpProtocolException::class.java) {
            McpCodec.parsePayload("application/json", "x".repeat(1_000_001), 1)
        }
    }
}
