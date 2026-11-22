package app.clodex.harness.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.clodex.harness.data.search.*
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.nav.RouteParser
import app.clodex.harness.ui.vm.AppTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen() {
    val nav = LocalNav.current
    val vms = LocalVms.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember(query) { mutableStateOf<List<SearchHit>?>(null) }
    var failed by remember(query) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, retry) {
        if (!SearchQuery.isSearchable(query)) return@LaunchedEffect
        failed = false
        delay(200)
        try {
            results = vms.container.searchRepository.search(query)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        }
    }
    var active by rememberSaveable { mutableStateOf<SearchSlice?>(null) }
    ScreenFrame {
        Header("Поиск", onBack = { nav.pop() })
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchBar(inputField = { androidx.compose.material3.SearchBarDefaults.InputField(query, { query = it }, {}, false, {}, placeholder = { Text("Поиск по Clodex") }) }, expanded = false, onExpandedChange = {}, modifier = Modifier.fillMaxWidth()) {}
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = active == null, onClick = { active = null }, label = { Text("Все") })
                SearchSlice.entries.filterNot { it == SearchSlice.FILES }.forEach { slice ->
                    FilterChip(selected = active == slice, onClick = { active = slice }, label = { Text(sliceLabel(slice)) })
                }
            }
            val visible = results.orEmpty().filter { active == null || it.slice == active }
            when {
                !SearchQuery.isSearchable(query) -> Emp("Введите хотя бы два символа.", HIcon.Search, "Поиск по Clodex")
                failed -> Column {
                    Emp("Попробуйте выполнить поиск ещё раз.", HIcon.Search, "Не удалось выполнить поиск")
                    Btn("Повторить", { retry++ }, Modifier.fillMaxWidth())
                }
                results == null -> CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
                visible.isEmpty() -> Emp("Попробуйте другой запрос или снимите фильтр.", HIcon.Search, "Ничего не найдено")
                else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible) { hit ->
                        It(onClick = {
                            val destination = RouteParser.parse(hit.route)
                            if (destination?.first == "chat") {
                                destination.second?.toLongOrNull()?.let { id ->
                                    vms.chat.open(id)
                                    nav.toTab(AppTab.CHAT)
                                }
                            } else if (destination != null) nav.push(hit.route)
                        }) { IconPlate(HIcon.Search); Titles(hit.title, hit.snippet) }
                    }
                }
            }
        }
    }
}

private fun sliceLabel(slice: SearchSlice) = when (slice) {
    SearchSlice.MESSAGES -> "Сообщения"; SearchSlice.CHATS -> "Чаты"; SearchSlice.SESSIONS -> "Сессии"
    SearchSlice.CHANGES -> "Правки"; SearchSlice.ARTIFACTS -> "Артефакты"; SearchSlice.FILES -> "Файлы"
}
