package app.clodex.harness.data.agent

import app.clodex.harness.data.mcp.McpRepository
import app.clodex.harness.data.mcp.NamespacedMcpTool
import kotlinx.serialization.json.JsonObject

/** A verified MCP tool exposed to the model. The provider-facing name is sanitized; calls use the real one. */
class McpAgentTool(
    private val repo: McpRepository,
    private val tool: NamespacedMcpTool,
    sourceName: String,
) : AgentTool {
    override val spec = AgentToolSpec(
        name = providerToolName(tool.namespacedName),
        description = tool.tool.description,
        inputSchema = tool.tool.inputSchema,
        readOnly = tool.tool.readOnlyHint,
        title = "$sourceName · ${tool.tool.name}",
        permission = tool.permission,
    )

    override suspend fun invoke(arguments: JsonObject): AgentToolResult =
        repo.call(tool.namespacedName, arguments, tool.permission).fold(
            onSuccess = { r -> AgentToolResult.of(r.text.ifBlank { r.structured?.toString() ?: "(пустой ответ)" }, r.isError) },
            onFailure = { e -> AgentToolResult.error(e.message ?: "Ошибка MCP") },
        )
}

/** Providers accept `[A-Za-z0-9_-]{1,64}`; anything else is replaced and long names get a stable hash suffix. */
fun providerToolName(name: String): String {
    val clean = name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "tool" }
    if (clean.length <= 64 && clean == name) return clean
    val hash = Integer.toHexString(name.hashCode()).padStart(8, '0')
    return clean.take(64 - 9) + "_" + hash
}

/** Tools of every connected connector; [displayName] maps a connector id to its visible name. */
suspend fun mcpAgentTools(repo: McpRepository, displayName: suspend (String) -> String): List<AgentTool> =
    repo.enabledTools().map { McpAgentTool(repo, it, displayName(it.connectorId)) }.distinctBy { it.spec.name }
