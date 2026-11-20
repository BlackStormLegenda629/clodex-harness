package app.clodex.harness.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clodex.harness.data.ScheduleRepeat
import app.clodex.harness.ui.components.*
import app.clodex.harness.ui.icons.HIcon
import app.clodex.harness.ui.nav.LocalNav
import app.clodex.harness.ui.nav.LocalVms
import app.clodex.harness.ui.theme.H
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
internal fun ScheduleScreen() {
    val vms = LocalVms.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val settings by vms.settings.settings.collectAsStateWithLifecycle()
    val chat by vms.chat.current.collectAsStateWithLifecycle()
    val chats by vms.chat.chats.collectAsStateWithLifecycle()
    val rows by vms.chat.schedules.collectAsStateWithLifecycle(emptyList())
    val provider = settings.activeProvider()
    var text by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableLongStateOf(0L) }
    var repeat by rememberSaveable { mutableStateOf(ScheduleRepeat.Once.name) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    ScreenFrame {
        Header("Запланированные сообщения", onBack = nav::pop)
        Body {
            Sg("Новое сообщение")
            GrpOf(
                { Li { Titles(chat?.title ?: "Нет активного чата", "${provider.name} · ${settings.model}", ellipsis = false) } },
                { LiField("Сообщение", text, { text = it.take(8000) }, singleLine = false, minLines = 3) },
                { Li(onClick = {
                    val time = Calendar.getInstance().apply { timeInMillis = if (due > 0) due else System.currentTimeMillis() + 60_000 }
                    DatePickerDialog(context, { _, year, month, day ->
                        TimePickerDialog(context, { _, hour, minute ->
                            time.set(year, month, day, hour, minute, 0)
                            time.set(Calendar.MILLISECOND, 0)
                            due = time.timeInMillis
                        }, time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
                    }, time.get(Calendar.YEAR), time.get(Calendar.MONTH), time.get(Calendar.DAY_OF_MONTH)).apply {
                        datePicker.minDate = System.currentTimeMillis()
                    }.show()
                }) { Titles(if (due > 0) dateFormat.format(Date(due)) else "Выбрать дату и время"); Ic(HIcon.Wait) } },
            )
            Seg(ScheduleRepeat.entries.map { it.label }, ScheduleRepeat.valueOf(repeat).label, {
                repeat = ScheduleRepeat.entries.first { rule -> rule.label == it }.name
            })
            SheetNote("Сообщение отправляется в указанный чат выбранной модели, без коннекторов и веб-поиска. Android может отложить отправку до появления сети или освобождения чата.", Modifier.padding(horizontal = 20.dp))
            Btn("Запланировать", {
                val target = chat ?: return@Btn
                vms.chat.schedule(target, settings, text, due, ScheduleRepeat.valueOf(repeat))
                if (due > System.currentTimeMillis()) { text = ""; due = 0L }
            }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), primary = true,
                enabled = chat != null && provider.ready && text.isNotBlank() && due > System.currentTimeMillis())
            Sg("Расписание")
            if (rows.isEmpty()) Emp("Нет запланированных сообщений")
            else Grp(rows, animate = false) { row, _ ->
                val status = when (row.state) {
                    "waiting" -> "Ожидает"; "running" -> "Выполняется"; "done" -> "Выполнено"
                    "cancelled" -> "Отменено"; else -> "Ошибка"
                }
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.text, style = H.t.body, color = H.c.ink, maxLines = 4)
                    Text("${chats.firstOrNull { it.id == row.chatId }?.title ?: "Чат недоступен"} · ${row.model}", style = H.t.small, color = H.c.mu)
                    Text("$status · ${dateFormat.format(Date(row.due))} · ${ScheduleRepeat.valueOf(row.repeat).label}", style = H.t.small, color = H.c.mu)
                    if (row.lastResult.isNotBlank()) Text(row.lastResult, style = H.t.small, color = H.c.mu)
                    if (row.state == "waiting" || row.state == "running") Btn("Отменить", { vms.chat.cancelSchedule(row.id) })
                }
            }
        }
    }
}
