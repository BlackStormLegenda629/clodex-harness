package app.clodex.harness.data.mcp

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class McpOAuth(private val http: HttpClient) {
    private fun https(value: String): String = URI(value).let {
        require(it.scheme == "https" && it.host != null && it.userInfo == null && it.fragment == null) { "OAuth требует HTTPS" }; value
    }
    private fun origin(value: String) = URI(value).let { "https://${it.rawAuthority}" }
    private fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.strings(key: String) = (get(key) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    private fun parse(body: String) = McpCodec.json.parseToJsonElement(body).jsonObject
    private suspend fun metadata(url: String): JsonObject? {
        val response = http.get(https(url))
        return if (response.status.isSuccess()) parse(response.bodyAsText()) else null
    }

    suspend fun connect(endpoint: String, openBrowser: (String) -> Unit): McpOAuthTokens {
        https(endpoint)
        val resourcePath = URI(endpoint).rawPath.trimEnd('/')
        val base = origin(endpoint)
        val response = http.post(endpoint) {
            contentType(ContentType.Application.Json); accept(ContentType.Application.Json)
            header(HttpHeaders.Accept, "text/event-stream")
            setBody("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"Clodex Harness","version":"1"}}}""")
        }
        val challenge = response.headers[HttpHeaders.WWWAuthenticate].orEmpty()
        val hint = Regex("resource_metadata=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(challenge)?.groupValues?.get(1)
        val prm = (listOfNotNull(hint) + listOf("$base/.well-known/oauth-protected-resource$resourcePath", "$base/.well-known/oauth-protected-resource")).distinct()
            .firstNotNullOfOrNull { metadata(it) }
        val resource = https(prm?.str("resource") ?: endpoint)
        require(origin(resource) == base) { "Метаданные OAuth относятся к другому серверу" }
        val issuer = https(prm?.strings("authorization_servers")?.firstOrNull() ?: base)
        val path = URI(issuer).rawPath.trimEnd('/')
        val authority = origin(issuer)
        val server = listOf("$authority/.well-known/oauth-authorization-server$path", "$authority/.well-known/openid-configuration$path", "${issuer.trimEnd('/')}/.well-known/openid-configuration")
            .distinct().firstNotNullOfOrNull { metadata(it) } ?: error("Сервер не опубликовал настройки OAuth")
        require(server.str("issuer") == issuer) { "Не совпадает издатель OAuth" }
        require("S256" in server.strings("code_challenge_methods_supported")) { "Сервер не подтвердил поддержку PKCE S256" }
        val registration = server.str("registration_endpoint") ?: error("Сервис требует свой зарегистрированный OAuth-клиент")
        val authorization = https(server.str("authorization_endpoint") ?: error("Нет адреса входа"))
        val tokenEndpoint = https(server.str("token_endpoint") ?: error("Нет адреса обмена токена"))
        val scope = Regex("(?:^|[ ,])scope=\"([^\"]+)\"").find(challenge)?.groupValues?.get(1)
            ?: prm?.strings("scopes_supported")?.joinToString(" ").orEmpty()
        val state = random(); val verifier = random()
        val pkce = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        return withContext(Dispatchers.IO) {
            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { listener ->
                val redirect = "http://127.0.0.1:${listener.localPort}/callback"
                val reg = http.post(https(registration)) {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("client_name", "Clodex Harness"); put("token_endpoint_auth_method", "none")
                        putJsonArray("redirect_uris") { add(redirect) }
                        putJsonArray("grant_types") { add("authorization_code"); add("refresh_token") }
                        putJsonArray("response_types") { add("code") }
                    }.toString())
                }
                check(reg.status.isSuccess()) { "Сервис отклонил регистрацию OAuth (HTTP ${reg.status.value})" }
                val client = parse(reg.bodyAsText())
                val id = client.str("client_id") ?: error("Нет идентификатора OAuth-клиента")
                require(client.str("token_endpoint_auth_method") in listOf(null, "none") && client.str("client_secret") == null) { "Сервис требует конфиденциальный OAuth-клиент" }
                val params = linkedMapOf("response_type" to "code", "client_id" to id, "redirect_uri" to redirect, "code_challenge" to pkce,
                    "code_challenge_method" to "S256", "state" to state, "resource" to resource)
                if (scope.isNotBlank()) params["scope"] = scope
                withContext(Dispatchers.Main) { openBrowser(authorization + (if ('?' in authorization) "&" else "?") + form(params)) }
                val code = awaitCode(listener, state)
                exchange(McpOAuthTokens(id, tokenEndpoint, resource), mapOf("grant_type" to "authorization_code", "code" to code, "code_verifier" to verifier, "redirect_uri" to redirect))
            }
        }
    }

    suspend fun refresh(tokens: McpOAuthTokens): McpOAuthTokens = exchange(tokens,
        mapOf("grant_type" to "refresh_token", "refresh_token" to (tokens.refresh ?: error("Нужно войти снова"))))

    private suspend fun exchange(tokens: McpOAuthTokens, fields: Map<String, String>): McpOAuthTokens {
        val response = http.post(https(tokens.tokenEndpoint)) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(form(fields + mapOf("client_id" to tokens.clientId, "resource" to tokens.resource)))
        }
        check(response.status.isSuccess()) { "Не удалось получить доступ (HTTP ${response.status.value})" }
        val json = parse(response.bodyAsText())
        check(json.str("token_type")?.equals("Bearer", true) == true) { "Сервер не выдал Bearer-токен" }
        val access = json.str("access_token")?.takeIf(String::isNotBlank) ?: error("Сервер не выдал токен")
        val expiry = json.str("expires_in")?.toLongOrNull()?.coerceIn(0, 31_536_000)?.let { System.currentTimeMillis() + it * 1000 }
        return tokens.copy(access = access, refresh = json.str("refresh_token") ?: tokens.refresh, expiresAt = expiry)
    }

    private suspend fun awaitCode(listener: ServerSocket, state: String): String = withTimeout(300_000) {
        listener.soTimeout = 750
        while (true) {
            ensureActive()
            val socket = try { listener.accept() } catch (_: java.net.SocketTimeoutException) { continue }
            socket.use {
                it.soTimeout = 1000
                val line = try {
                    val input = it.getInputStream().buffered(); val bytes = StringBuilder()
                    while (bytes.length < 8192) { val byte = input.read(); if (byte == -1 || byte == 10) break; bytes.append(byte.toChar()) }
                    bytes.toString()
                } catch (_: java.io.IOException) { return@use }
                val target = line.takeIf { it.startsWith("GET /callback?") }?.substringAfter(' ')?.substringBefore(' ')
                val query = runCatching { target?.substringAfter('?')?.split('&')?.associate { p -> p.substringBefore('=') to URLDecoder.decode(p.substringAfter('=', ""), "UTF-8") } }.getOrNull()
                val valid = query?.get("state") == state
                runCatching {
                    it.getOutputStream().write((if (valid) "HTTP/1.0 200 OK\r\n" else "HTTP/1.0 400 Bad Request\r\n").plus(
                        "Content-Type: text/plain; charset=UTF-8\r\nCache-Control: no-store\r\n\r\nВернитесь в Clodex.").toByteArray())
                }
                if (valid) return@withTimeout query?.get("code")?.takeIf(String::isNotBlank) ?: error("Вход отменён или доступ отклонён")
            }
        }
        error("Время входа истекло")
    }

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun random() = b64(ByteArray(32).also { SecureRandom().nextBytes(it) })
    private fun form(fields: Map<String, String>) = fields.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
}

data class McpOAuthTokens(val clientId: String, val tokenEndpoint: String, val resource: String,
    val access: String = "", val refresh: String? = null, val expiresAt: Long? = null) {
    override fun toString() = "McpOAuthTokens([redacted])"
}
