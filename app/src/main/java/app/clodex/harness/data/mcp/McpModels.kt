package app.clodex.harness.data.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class McpAuthMode { NONE, BEARER, HEADERS, GOOGLE }

data class McpConnectionConfig(
    val connectorId: String,
    val endpoint: String,
    val authMode: McpAuthMode = McpAuthMode.NONE,
    val bearerToken: String = "",
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val lastCheckedAt: Long = 0,
    val serverName: String = "",
    val serverVersion: String = "",
    val tools: List<McpToolSpec> = emptyList(),
    val lastError: String? = null,
    val oauth: McpOAuthTokens? = null,
    val grants: List<McpGrant> = emptyList(),
) {
    override fun toString() = "McpConnectionConfig($connectorId)"
}

data class McpPermission(val connectorId: String, val tool: String, val fingerprint: String)
data class McpGrant(val chatId: Long, val permission: McpPermission)

internal fun McpConnectionConfig.permission(tool: McpToolSpec): McpPermission {
    val identity = kotlinx.serialization.json.buildJsonArray {
        listOf(connectorId, endpoint, authMode.name, bearerToken, oauth?.access.orEmpty(),
            serverName, serverVersion, tool.name, tool.description, tool.inputSchema.toString(), tool.readOnlyHint.toString())
            .forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
        headers.toSortedMap().forEach { (k, v) -> add(kotlinx.serialization.json.JsonPrimitive(k)); add(kotlinx.serialization.json.JsonPrimitive(v)) }
    }.toString()
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    return McpPermission(connectorId, tool.name, digest)
}

data class McpServerInfo(val name: String, val version: String)

data class McpToolSpec(
    val name: String,
    val description: String = "",
    val inputSchema: JsonObject = JsonObject(emptyMap()),
    val readOnlyHint: Boolean = false,
)

data class McpProbe(
    val server: McpServerInfo,
    val tools: List<McpToolSpec>,
    val sessionId: String? = null,
    val checkedAt: Long,
)

data class McpCallResult(val text: String, val structured: JsonElement? = null, val isError: Boolean = false)

open class McpProtocolException(message: String) : Exception(message)
class McpHttpException(val status: Int) : McpProtocolException("MCP HTTP $status")

object McpHeaders {
    fun parse(text: String): Map<String, String> = text.lines().mapNotNull { line ->
        val i = line.indexOf(':')
        if (i <= 0) return@mapNotNull null
        val name = line.substring(0, i).trim(); val value = line.substring(i + 1).trim()
        if (name.isEmpty() || value.isEmpty() || name.any { it.isWhitespace() }) null else name to value
    }.toMap()

    fun format(headers: Map<String, String>): String =
        headers.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}: ${it.value}" }
}
