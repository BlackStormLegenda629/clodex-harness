package app.clodex.harness.data.mcp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

val Context.connectorSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "connector_settings")

object McpConfigCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun encode(configs: Map<String, McpConnectionConfig>): String = buildJsonObject {
        putJsonArray("configs") {
            configs.values.sortedBy { it.connectorId }.forEach { config ->
                add(buildJsonObject {
                    put("connectorId", config.connectorId)
                    put("endpoint", config.endpoint)
                    put("authMode", config.authMode.name)
                    put("bearerToken", config.bearerToken)
                    config.oauth?.let { t -> putJsonObject("oauth") {
                        put("clientId", t.clientId); put("tokenEndpoint", t.tokenEndpoint); put("resource", t.resource)
                        put("access", t.access); t.refresh?.let { put("refresh", it) }; t.expiresAt?.let { put("expiresAt", it) }
                    } }
                    put("enabled", config.enabled)
                    put("lastCheckedAt", config.lastCheckedAt)
                    put("serverName", config.serverName)
                    put("serverVersion", config.serverVersion)
                    config.lastError?.let { put("lastError", it) }
                    putJsonArray("grants") { config.grants.forEach { grant -> add(buildJsonObject {
                        put("chatId", grant.chatId); put("tool", grant.permission.tool); put("fingerprint", grant.permission.fingerprint)
                    }) } }
                    putJsonObject("headers") { config.headers.forEach { (name, value) -> put(name, value) } }
                    putJsonArray("tools") {
                        config.tools.forEach { tool ->
                            add(buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("inputSchema", tool.inputSchema)
                                put("readOnlyHint", tool.readOnlyHint)
                            })
                        }
                    }
                })
            }
        }
    }.toString()

    fun decode(raw: String?): Map<String, McpConnectionConfig> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            root["configs"]?.jsonArray.orEmpty().mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj["connectorId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val endpoint = obj["endpoint"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val auth = runCatching {
                    McpAuthMode.valueOf(obj["authMode"]?.jsonPrimitive?.contentOrNull ?: McpAuthMode.NONE.name)
                }.getOrDefault(McpAuthMode.NONE)
                val headers = obj["headers"]?.jsonObject?.mapNotNull { (name, value) ->
                    value.jsonPrimitive.contentOrNull?.let { name to it }
                }?.toMap().orEmpty()
                val tools = obj["tools"]?.jsonArray.orEmpty().mapNotNull { rawTool ->
                    val tool = rawTool.jsonObject
                    val name = tool["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    McpToolSpec(
                        name = name,
                        description = tool["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        inputSchema = tool["inputSchema"]?.let { runCatching { it.jsonObject }.getOrNull() }
                            ?: kotlinx.serialization.json.JsonObject(emptyMap()),
                        readOnlyHint = tool["readOnlyHint"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                    )
                }
                id to McpConnectionConfig(
                    connectorId = id,
                    endpoint = endpoint,
                    authMode = auth,
                    bearerToken = obj["bearerToken"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    headers = headers,
                    enabled = obj["enabled"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
                    lastCheckedAt = obj["lastCheckedAt"]?.jsonPrimitive?.longOrNull ?: 0,
                    serverName = obj["serverName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    serverVersion = obj["serverVersion"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    tools = tools,
                    lastError = obj["lastError"]?.jsonPrimitive?.contentOrNull,
                    grants = (obj["grants"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { raw ->
                        val grant = raw as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                        val chat = grant["chatId"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return@mapNotNull null
                        val name = grant["tool"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val fingerprint = grant["fingerprint"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        McpGrant(chat, McpPermission(id, name, fingerprint))
                    },
                    oauth = (obj["oauth"] as? kotlinx.serialization.json.JsonObject)?.let { t ->
                        fun field(key: String) = t[key]?.jsonPrimitive?.contentOrNull.orEmpty()
                        McpOAuthTokens(field("clientId"), field("tokenEndpoint"), field("resource"), field("access"), field("refresh").ifBlank { null }, t["expiresAt"]?.jsonPrimitive?.longOrNull)
                    },
                )
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}

interface McpConfigRepository {
    val configs: Flow<Map<String, McpConnectionConfig>>
    suspend fun get(id: String): McpConnectionConfig?
    suspend fun save(config: McpConnectionConfig)
    suspend fun remove(id: String)
}

class DataStoreMcpConfigRepository(private val store: DataStore<Preferences>) : McpConfigRepository {
    private val key = stringPreferencesKey("connections")
    override val configs: Flow<Map<String, McpConnectionConfig>> = store.data.map { McpConfigCodec.decode(it[key]) }

    override suspend fun get(id: String): McpConnectionConfig? = configs.first()[id]

    override suspend fun save(config: McpConnectionConfig) {
        store.edit { prefs ->
            val current = McpConfigCodec.decode(prefs[key]).toMutableMap()
            current[config.connectorId] = config
            prefs[key] = McpConfigCodec.encode(current)
        }
    }

    override suspend fun remove(id: String) {
        store.edit { prefs ->
            val current = McpConfigCodec.decode(prefs[key]).toMutableMap()
            current.remove(id)
            prefs[key] = McpConfigCodec.encode(current)
        }
    }
}
