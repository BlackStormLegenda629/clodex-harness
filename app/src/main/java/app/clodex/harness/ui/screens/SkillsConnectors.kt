package app.clodex.harness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clodex.harness.data.ConStatus
import app.clodex.harness.data.ConnCatalogEntry
import app.clodex.harness.data.ConnectorCatalog
import app.clodex.harness.data.ConnectorEntity
import app.clodex.harness.data.Src
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.icons.HIcons
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.theme.HDim
import app.clodex.harness.ui.vm.SheetKind

@Composable
fun connectorBrand(c: ConnectorEntity): Color = when (c.color) {
    "ink" -> H.c.ink
    "mu" -> H.c.mu
    else -> runCatching { Color(android.graphics.Color.parseColor(c.color)) }.getOrDefault(H.c.mu)
}

/** Neutral letter mark: brand artwork is not redistributed. */
@Composable
fun ConnectorMark(c: ConnectorEntity, size: Dp = 40.dp) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        Modifier.size(size).clip(shape).background(Color.White)
            .border(1.dp, H.c.line.copy(alpha = 0.55f), shape).padding(size * 0.09f),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size * 0.78f).clip(RoundedCornerShape(size * 0.18f)).background(connectorBrand(c)), contentAlignment = Alignment.Center) {
            Text(
                c.name.take(1).uppercase(), style = H.t.ctl.copy(fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}

@Composable
fun statusText(st: String): String = when (st) {
    ConStatus.ON -> "Подключён"; ConStatus.OFF -> "Выключен"; ConStatus.WARN -> "Нужно переподключить"; else -> "Не подключён"
}

@Composable
fun SkillsScreen() {
    val nav = LocalNav.current; val vms = LocalVms.current
    val all by vms.skills.skills.collectAsStateWithLifecycle()
    var f by rememberSaveable { mutableStateOf("Все") }
    var q by rememberSaveable { mutableStateOf("") }
    val src = mapOf("Встроенные" to Src.BUILTIN, "Мои" to Src.MINE, "GitHub" to Src.GITHUB)
    val list = all.filter { (f == "Все" || it.source == src[f]) && (it.name + it.description).lowercase().contains(q.trim().lowercase()) }
    ScreenFrame {
        Header("Скиллы", onBack = { nav.pop() }, actions = { IconBtn(HIcon.Plus, "Добавить скилл", { vms.app.open(SheetKind.SkillAdd) }, circle = true) })
        Body {
            Seg(listOf("Все", "Встроенные", "Мои", "GitHub"), f, { f = it }, pad = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 8.dp))
            Srch(q, { q = it }, "Поиск скиллов")
            if (list.isEmpty()) Emp("Скиллов не найдено", HIcon.Pen)
            else Grp(list) { s, _ ->
                Row(
                    Modifier.fillMaxWidth().hClick { vms.app.open(SheetKind.SkillDetail, s.id) }.padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconPlate(HIcons.byName(s.icon))
                    Titles(s.name, s.source + " · " + s.description)
                    Sw(s.enabled, onToggle = { vms.skills.toggle(s.id) })
                }
            }
        }
    }
}

@Composable
private fun ConnectorCard(c: ConnectorEntity, e: ConnCatalogEntry?, index: Int, onOpen: () -> Unit, onConnect: () -> Unit) {
    val theme = H.c
    val shape = RoundedCornerShape(HDim.card)
    Column(
        Modifier.fillMaxWidth().rise(index).clip(shape).background(theme.s1)
            .border(1.dp, theme.line, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().hClick(onClick = onOpen), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectorMark(c, 42.dp)
            if (e?.badge != null) Bdg(e.badge, BdgKind.Grey)
        }
        Column(Modifier.fillMaxWidth().hClick(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.name, style = H.t.bodyMed, color = theme.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.developer, style = H.t.small, color = theme.mu, maxLines = 1)
        }
        Text(c.description, style = H.t.small, color = theme.mu, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 2.dp))
        Btn(if (c.status == ConStatus.NEW) "Подключить" else "Открыть", if (c.status == ConStatus.NEW) onConnect else onOpen, Modifier.fillMaxWidth())
    }
}

@Composable
fun ConnectorsScreen() {
    val nav = LocalNav.current; val vms = LocalVms.current
    val all by vms.connectors.connectors.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Обзор") }
    var category by rememberSaveable { mutableStateOf("Все") }
    var q by rememberSaveable { mutableStateOf("") }
    val matches = all.filter { (it.name + it.description).lowercase().contains(q.trim().lowercase()) }
    val mine = matches.filter { it.status != ConStatus.NEW }
    val catalog = matches.filter { it.status == ConStatus.NEW }.filter {
        ConnectorCatalog.get(it.id)?.let { e -> ConnectorCatalog.inCategory(e, category) } ?: (category == "Все")
    }
    ScreenFrame {
        Header("Коннекторы", onBack = { nav.pop() }, subtitle = "${all.count { it.status == ConStatus.ON }} подключено", actions = {
            IconBtn(HIcon.Plus, "Добавить свой сервер", { vms.app.open(SheetKind.ConnectorAdd) }, circle = true)
        })
        Body {
            Seg(listOf("Обзор", "Ваши"), tab, { tab = it }, pad = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 8.dp))
            Srch(q, { q = it }, "Поиск коннекторов")
            if (tab == "Ваши") {
                if (mine.isEmpty()) Emp("Подключённых коннекторов пока нет. Загляните в «Обзор».", HIcon.Plug)
                else Grp(mine) { c, _ ->
                    Row(
                        Modifier.fillMaxWidth().hClick { vms.app.open(SheetKind.ConnectorDetail, c.id) }.padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ConnectorMark(c)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = H.t.bodyMed, color = H.c.ink, maxLines = 1)
                            if (c.status == ConStatus.WARN) Text(statusText(c.status), style = H.t.small, color = H.c.warn)
                            else Text(c.developer, style = H.t.small, color = H.c.mu, maxLines = 1)
                        }
                        if (c.status == ConStatus.WARN) Btn("Переподключить", { vms.app.open(SheetKind.ConnectorDetail, c.id) }, Modifier)
                        else Sw(c.status == ConStatus.ON, onToggle = { vms.connectors.toggle(c.id) })
                    }
                }
            } else {
                Seg(ConnectorCatalog.categories, category, { category = it })
                if (catalog.isEmpty()) {
                    Emp("Ничего не нашлось. Попробуйте другую категорию или поиск.", HIcon.Plug)
                } else {
                    catalog.chunked(2).forEachIndexed { i, pair ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { c ->
                                Box(Modifier.weight(1f)) {
                                    ConnectorCard(
                                        c, ConnectorCatalog.get(c.id), i,
                                        onOpen = { vms.app.open(SheetKind.ConnectorDetail, c.id) },
                                        onConnect = { vms.app.open(SheetKind.ConnectorDetail, c.id) },
                                    )
                                }
                            }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                    Box(Modifier.padding(20.dp))
                }
            }
        }
    }
}
