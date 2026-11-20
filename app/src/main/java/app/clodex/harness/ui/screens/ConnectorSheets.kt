package app.clodex.harness.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import app.clodex.harness.data.ConnectorCatalog
import app.clodex.harness.data.ConStatus
import app.clodex.harness.data.ConnectorEntity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clodex.harness.data.mcp.McpAuthMode
import app.clodex.harness.data.mcp.McpConnectionConfig
import app.clodex.harness.data.mcp.McpHeaders
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.vm.SheetKind
import java.text.DateFormat
import java.util.Date

private val AuthLabels = listOf("Без авторизации", "Bearer-токен", "Заголовки")
private fun authOf(label: String) = when (label) { AuthLabels[1] -> McpAuthMode.BEARER; AuthLabels[2] -> McpAuthMode.HEADERS; else -> McpAuthMode.NONE }
private fun labelOf(mode: McpAuthMode) = when (mode) { McpAuthMode.BEARER -> AuthLabels[1]; McpAuthMode.HEADERS -> AuthLabels[2]; else -> AuthLabels[0] }

/**
 * Endpoint + auth form. [connectorId] null means "own server by URL".
 * Nothing is marked connected until the server answers initialize + tools/list.
 */
@Composable
fun ColumnScope.ConnectorConfigSheet(connectorId: String?, close: () -> Unit) {
    val vms = LocalVms.current
    val cons by vms.connectors.connectors.collectAsStateWithLifecycle()
    val configs by vms.connectors.configs.collectAsStateWithLifecycle()
    val checking by vms.connectors.checking.collectAsStateWithLifecycle()
    val con = connectorId?.let { id -> cons.firstOrNull { it.id == id } }
    val saved = connectorId?.let { configs[it] }
    if (connectorId == "gm") {
        if (con != null) ConnectorDetails(con, close) else SheetHeader("Gmail", close)
        return
    }

    var url by rememberSaveable(connectorId) { mutableStateOf(saved?.endpoint ?: connectorId?.let(ConnectorCatalog::endpoint).orEmpty()) }
    var auth by rememberSaveable(connectorId) { mutableStateOf(labelOf(saved?.authMode ?: if (connectorId?.let(ConnectorCatalog::endpoint) != null) McpAuthMode.BEARER else McpAuthMode.NONE)) }
    var token by rememberSaveable(connectorId) { mutableStateOf(saved?.bearerToken.orEmpty()) }
    var headers by rememberSaveable(connectorId) { mutableStateOf(McpHeaders.format(saved?.headers.orEmpty())) }
    var busy by rememberSaveable(connectorId) { mutableStateOf(false) }
    val running = busy || (connectorId != null && connectorId in checking)

    SheetHeader(
        if (con != null) con.name else "Свой сервер", close,
        onBack = connectorId?.let { id -> { vms.app.open(SheetKind.ConnectorDetail, id) } },
    )
    SheetNote(
        "Укажите адрес MCP-сервера (Streamable HTTP). Приложение выполнит initialize и tools/list; " +
            "коннектор включится только если сервер ответит.",
    )
    GrpOf({ LiField("Адрес сервера", url, { url = it }, "https://mcp.example.com/mcp") })
    Sg("Авторизация")
    Seg(AuthLabels, auth, { auth = it }, pad = androidx.compose.foundation.layout.PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 10.dp))
    when (authOf(auth)) {
        McpAuthMode.BEARER -> GrpOf({ LiField("Токен", token, { token = it }, "sk-… или OAuth access token", password = true) })
        McpAuthMode.HEADERS -> GrpOf({ LiField("Заголовки", headers, { headers = it }, "X-Api-Key: …\nX-Team: …", singleLine = false, minLines = 3) })
        McpAuthMode.NONE, McpAuthMode.GOOGLE -> {}
    }
    saved?.lastError?.let { SheetNote("Последняя ошибка: $it") }
    Box(Modifier.padding(top = 16.dp)) {
        Btn(
            if (running) "Проверяю…" else "Проверить и подключить",
            {
                val mode = authOf(auth)
                val cfg = McpConnectionConfig(
                    connectorId = connectorId.orEmpty(),
                    endpoint = url.trim(),
                    authMode = mode,
                    bearerToken = if (mode == McpAuthMode.BEARER) token.trim() else "",
                    headers = if (mode == McpAuthMode.HEADERS) McpHeaders.parse(headers) else emptyMap(),
                )
                busy = true
                if (connectorId == null) vms.connectors.addByUrl(url, cfg) { ok -> busy = false; if (ok) close() }
                else vms.connectors.connect(connectorId, cfg) { ok -> busy = false; if (ok) vms.app.open(SheetKind.ConnectorDetail, connectorId) }
            },
            Modifier.fillMaxWidth(), primary = true, enabled = !running && url.isNotBlank() && when (authOf(auth)) {
                McpAuthMode.BEARER -> token.isNotBlank()
                McpAuthMode.HEADERS -> McpHeaders.parse(headers).isNotEmpty()
                McpAuthMode.NONE -> connectorId?.let(ConnectorCatalog::browserLimit) == null
                McpAuthMode.GOOGLE -> false
            },
        )
    }
}

@Composable
fun ColumnScope.ConnectorDetails(connector: ConnectorEntity, close: () -> Unit) {
    val vms = LocalVms.current
    val ctx = LocalContext.current
    val configs by vms.connectors.configs.collectAsStateWithLifecycle()
    val checking by vms.connectors.checking.collectAsStateWithLifecycle()
    val id = connector.id
    val meta = ConnectorCatalog.get(id)
    val cfg = configs[id]?.takeIf { id != "gm" || it.authMode == McpAuthMode.GOOGLE }
    val endpoint = ConnectorCatalog.endpoint(id)
    val browser = ConnectorCatalog.browserEndpoints[id]
    val limitation = ConnectorCatalog.browserLimit(id)
    val connected = connector.status == ConStatus.ON && cfg?.enabled == true && cfg.lastError == null
    val busy = id in checking
    val google = if (id == "gm") rememberGoogleGmailLogin(vms.connectors::finishGmailLogin) else null
    SheetHeader(connector.name, close)
    if (google != null) SheetNote("Clodex не меняет почту. Когда модель использует Gmail в чате, найденные письма и черновики могут быть отправлены выбранному провайдеру модели.")
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ConnectorMark(connector, 42.dp)
        Column(Modifier.weight(1f)) {
            Text(connector.name, style = H.t.bodyMed, color = H.c.ink)
            Text(connector.description, style = H.t.small, color = H.c.mu)
        }
        Btn(when { google?.first == true -> "Вход…"; busy -> "Отмена"; connected -> "Отключить"; else -> "Подключить" }, {
            when {
                busy -> vms.connectors.cancelLogin(id)
                connected -> vms.connectors.disconnect(id)
                google != null -> google.second()
                browser != null -> vms.connectors.browserConnect(id, browser) { url ->
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        putExtras(android.os.Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
                    })
                }
                else -> vms.app.open(SheetKind.ConnectorConfig, id)
            }
        }, primary = !connected && !busy, enabled = google?.first != true && (id != "fg" || connected))
    }
    if (busy) SheetNote(if (google != null) "Проверяю доступ к Gmail…" else "Завершите вход в браузере, затем вернитесь в Clodex.")
    if (google != null) SheetNote("Вход через Google. Clodex запрашивает доступ только на чтение. После истечения доступа может потребоваться повторный вход.")
    Text(meta?.longDescription ?: connector.description, style = H.t.body, color = H.c.ink, modifier = Modifier.padding(vertical = 10.dp))
    limitation?.let { SheetNote(it) }
    ConnectorRuntimeInfo(cfg, meta?.tools.orEmpty())
    Sg("Сведения")
    Text("Автор · ${connector.developer}", style = H.t.body, color = H.c.ink)
    (cfg?.endpoint ?: endpoint)?.let { url ->
        Sg("URL коннектора")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(url, style = H.t.mono12, color = H.c.ink, modifier = Modifier.weight(1f))
            IconBtn(HIcon.Copy, "Копировать URL", {
                ctx.getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("MCP", url))
            })
        }
    }
}

/** Real server metadata after a probe; catalog tools are shown only as "expected" before that. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectorRuntimeInfo(cfg: McpConnectionConfig?, expectedTools: List<String>) {
    val verified = cfg != null && cfg.lastCheckedAt > 0 && cfg.lastError == null
    val tools = if (verified) cfg!!.tools.map { it.name } else expectedTools
    var expanded by rememberSaveable(cfg?.connectorId) { mutableStateOf(false) }
    if (verified) {
        GrpOf(
            { Li { Ic(HIcon.Plug, tint = H.c.mu); Text("Сервер", style = H.t.body, color = H.c.ink, modifier = Modifier.weight(1f)); Text(listOf(cfg!!.serverName, cfg.serverVersion).filter { it.isNotBlank() }.joinToString(" "), style = H.t.row14, color = H.c.mu, maxLines = 1) } },
            { Li { Ic(HIcon.Ck, tint = H.c.mu); Text("Проверен", style = H.t.body, color = H.c.ink, modifier = Modifier.weight(1f)); Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(cfg!!.lastCheckedAt)), style = H.t.row14, color = H.c.mu) } },
            { Li { Ic(HIcon.Globe, tint = H.c.mu); Text("Адрес", style = H.t.body, color = H.c.ink, modifier = Modifier.weight(1f)); Text(cfg!!.endpoint.removePrefix("https://"), style = H.t.mono12, color = H.c.mu, maxLines = 1) } },
        )
    }
    if (tools.isNotEmpty()) {
        Sg(if (verified) "Инструменты · ${tools.size}" else "Ожидаемые инструменты")
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (if (expanded) tools else tools.take(8)).forEach { Bdg(it, if (verified) BdgKind.Blue else BdgKind.Grey) }
            if (tools.size > 8) Btn(if (expanded) "Свернуть" else "+${tools.size - 8} ещё", { expanded = !expanded })
        }
        Box(Modifier.padding(top = 10.dp))
    }
    cfg?.lastError?.let { SheetNote("Ошибка: $it") }
}
