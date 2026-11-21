package app.clodex.harness.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clodex.harness.data.search.*
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalVms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val nav = LocalNav.current
    val vms = LocalVms.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    LaunchedEffect(query) { results = vms.container.searchRepository.search(query) }
    var active by rememberSaveable { mutableStateOf<SearchSlice?>(null) }
    ScreenFrame {
        Header("Поиск", onBack = { nav.pop() })
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchBar(inputField = { androidx.compose.material3.SearchBarDefaults.InputField(query, { query = it }, {}, false, {}, placeholder = { Text("Поиск по Clodex") }) }, expanded = false, onExpandedChange = {}, modifier = Modifier.fillMaxWidth()) {}
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { active = null }, label = { Text("Все") })
                SearchSlice.entries.forEach { slice -> AssistChip(onClick = { active = slice }, label = { Text(sliceLabel(slice)) }) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results.filter { active == null || it.slice == active }) { hit ->
                    It(onClick = { nav.push(hit.route) }) { IconPlate(HIcon.Search); Titles(hit.title, hit.snippet) }
                }
            }
        }
    }
}

private fun sliceLabel(slice: SearchSlice) = when (slice) {
    SearchSlice.MESSAGES -> "Сообщения"; SearchSlice.CHATS -> "Чаты"; SearchSlice.SESSIONS -> "Сессии"
    SearchSlice.CHANGES -> "Правки"; SearchSlice.ARTIFACTS -> "Артефакты"; SearchSlice.FILES -> "Файлы"
}
