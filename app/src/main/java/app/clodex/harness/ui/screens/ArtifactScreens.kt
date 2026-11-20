package app.clodex.harness.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clodex.harness.data.ArtifactEntity
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalToasts
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.theme.H
import app.clodex.harness.util.ArtifactIo
import app.clodex.harness.util.RelativeTime
import app.clodex.harness.util.highlight
import app.clodex.harness.util.rememberDownloader
import java.io.ByteArrayInputStream

@Composable
fun ArtifactsHeader() { Header("Артефакты", big = true, actions = { GearBtn() }) }

@Composable
private fun rememberArtifactDownload(): (ArtifactEntity) -> Unit {
    val toasts = LocalToasts.current
    val dl = rememberDownloader { r -> toasts.show(r.fold({ "Скачано: $it" }, { it.message ?: "Не удалось сохранить" })) }
    return { a -> dl(a.name, a.content) }
}

@Composable
fun ColumnScope.ArtifactsBody() {
    val vms = LocalVms.current; val nav = LocalNav.current
    val all by vms.artifacts.artifacts.collectAsStateWithLifecycle()
    var f by rememberSaveable { mutableStateOf("Все") }
    val download = rememberArtifactDownload()
    val list = all.filter { f == "Все" || it.kind == f }
    Body {
        Seg(listOf("Все", "HTML", "Код", "Документ"), f, { f = it })
        if (list.isEmpty()) Emp("Здесь пока пусто", HIcon.Cards)
        list.forEach { a ->
            It(onClick = { nav.artifact(a.id) }) {
                IconPlate(HIcon.File)
                Titles(a.name, a.kind + " · " + a.source + " · " + RelativeTime.artifact(a.createdAt))
                IconBtn(HIcon.Dl, "Скачать ${a.name}", { download(a) }, circle = true)
            }
        }
    }
}

/** HTML artifact preview: JS off, network loads blocked, navigation and http(s) requests swallowed. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlPreview(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.blockNetworkLoads = true
                settings.blockNetworkImage = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                        if (request?.url?.scheme in setOf("data", "about")) null
                        else WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
        },
        update = { wv -> if (wv.tag != html) { wv.tag = html; wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) } },
        onRelease = { it.destroy() },
    )
}

@Composable
fun ArtifactScreen(id: Long) {
    val nav = LocalNav.current; val vms = LocalVms.current
    val ctx = LocalContext.current; val toasts = LocalToasts.current
    val a by vms.artifacts.artifact(id).collectAsStateWithLifecycle(null)
    val download = rememberArtifactDownload()
    val art = a
    if (art == null) { ScreenFrame { Header("", onBack = { nav.pop() }); Emp("Артефакт не найден", HIcon.Cards) }; return }
    val c = H.c
    if (art.kind == "HTML") {
        HtmlViewer(art.name, art.content) { nav.pop() }
        return
    }
    val shape = RoundedCornerShape(14.dp)
    ScreenFrame {
        Header(art.name, onBack = { nav.pop() }, subtitle = art.kind, actions = { IconBtn(HIcon.Dl, "Скачать", { download(art) }, circle = true) })
        Body {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(shape).background(c.s1).border(1.dp, c.line, shape)) {
                Text(
                    if (art.kind == "Код") highlight(art.content, c) else androidx.compose.ui.text.AnnotatedString(art.content),
                    style = H.t.monoCode, color = c.ink, softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn("Скачать", { download(art) }, Modifier.weight(1f), primary = true, icon = HIcon.Dl)
                Btn("Копировать", { ArtifactIo.copy(ctx, art.content); toasts.show("Скопировано") }, Modifier.weight(1f), icon = HIcon.Copy)
                IconBtn(HIcon.Share, "Поделиться", { ArtifactIo.share(ctx, art.name, art.content) }, circle = true, size = 48.dp)
            }
        }
    }
}
