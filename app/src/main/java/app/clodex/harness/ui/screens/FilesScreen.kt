package app.clodex.harness.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.theme.H
import app.clodex.harness.ui.vm.SheetKind
import app.clodex.harness.util.Saf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Folder browser for a SAF tree. Tapping a folder pushes the next level; files open in a preview sheet. */
@Composable
fun FilesScreen(uriArg: String) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val app = LocalVms.current.app
    val uri = remember(uriArg) { Uri.parse(Uri.decode(uriArg)) }
    val title = remember(uri) { Saf.name(ctx, uri) }
    val entries by produceState(emptyList<Saf.Entry>(), uri) {
        value = withContext(Dispatchers.IO) { Saf.children(ctx, uri) }
    }
    ScreenFrame {
        Header(
            title, onBack = { nav.pop() }, subtitle = "Папка на устройстве", actions = {
                IconBtn(HIcon.Code, "Терминал", { nav.terminal() }, circle = true)
            },
        )
        Body {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${entries.count { it.dir }} папок · ${entries.count { !it.dir }} файлов", style = H.t.small, color = H.c.mu)
            }
            if (entries.isEmpty()) Emp("Папка пуста или доступ к ней больше не выдан", HIcon.Folder, "Здесь пусто")
            entries.forEach { e ->
                It(onClick = {
                    if (e.dir) nav.browse(e.uri) else app.open(SheetKind.FilePreview, e.uri)
                }) {
                    IconPlate(if (e.dir) HIcon.Folder else HIcon.File)
                    Titles(e.name, if (e.dir) "Папка" else Saf.humanSize(e.size).ifBlank { "Файл" })
                    if (e.dir) Ic(HIcon.Ch, tint = H.c.mu, size = 16.dp)
                }
            }
        }
    }
}
