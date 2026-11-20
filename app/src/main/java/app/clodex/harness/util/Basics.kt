package app.clodex.harness.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One-shot toast messages. `last` exists so tests (and nothing else) can read what was shown. */
class ToastBus {
    private val _flow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val flow: SharedFlow<String> = _flow.asSharedFlow()
    @Volatile var last: String? = null
        private set
    fun show(message: String) { last = message; _flow.tryEmit(message) }
}

object RelativeTime {
    private val ru = Locale("ru")
    private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy 'г.'", ru)
    private val zone get() = ZoneId.systemDefault()

    private fun day(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    private fun days(ts: Long, now: Long): Long = ChronoUnit.DAYS.between(day(ts), day(now))
    private fun date(ts: Long): String = day(ts).format(fmt)

    fun plural(n: Long): String {
        val m10 = n % 10; val m100 = n % 100
        return if (m10 == 1L && m100 != 11L) "день" else if (m10 in 2..4 && m100 !in 12..14) "дня" else "дней"
    }

    /** Chat list: "Сегодня", "Вчера", "9 нояб. 2026 г." */
    fun chat(ts: Long, now: Long = System.currentTimeMillis()): String =
        when (val d = days(ts, now)) { in Long.MIN_VALUE..0 -> "Сегодня"; 1L -> "Вчера"; else -> date(ts) }

    /** Project list: "Изменено: вчера", "Изменено: 3 дня назад", "Изменено: 24 сент. 2026 г." */
    fun modified(ts: Long, now: Long = System.currentTimeMillis()): String {
        val d = days(ts, now)
        return "Изменено: " + when {
            now - ts < 60_000 -> "только что"
            d <= 0 -> "сегодня"
            d == 1L -> "вчера"
            d < 7 -> "$d ${plural(d)} назад"
            else -> date(ts)
        }
    }

    /** Artifacts: "сегодня", "вчера", "3 дня назад". */
    fun artifact(ts: Long, now: Long = System.currentTimeMillis()): String {
        val d = days(ts, now)
        return when { d <= 0 -> "сегодня"; d == 1L -> "вчера"; d < 7 -> "$d ${plural(d)} назад"; else -> date(ts) }
    }

    /** Sessions: "сейчас", "2м", "1ч", "вчера". */
    fun session(ts: Long, now: Long = System.currentTimeMillis()): String {
        val ms = now - ts
        return when {
            ms < 60_000 -> "сейчас"
            ms < 3_600_000 -> "${ms / 60_000}м"
            days(ts, now) <= 0 -> "${ms / 3_600_000}ч"
            days(ts, now) == 1L -> "вчера"
            else -> date(ts)
        }
    }
}
