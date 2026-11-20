package app.clodex.harness

import app.clodex.harness.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEventCodecTest {
    private fun ev(kind: String = EventKind.TOOL, title: String = "Прочитал a.kt", detail: String = "", status: String = EventStatus.OK) =
        SessionEventEntity(sessionId = 1, kind = kind, title = title, detail = detail, status = status, createdAt = 0)

    @Test fun `titles and details are bounded and collapsed`() {
        val e = SessionEventCodec.bound(ev(title = "  Прочитал\n   " + "я".repeat(500), detail = "д".repeat(5000)))
        assertEquals(SessionEventCodec.MAX_TITLE, e.title.length)
        assertTrue(e.title.startsWith("Прочитал я"))
        assertEquals(SessionEventCodec.MAX_DETAIL, e.detail.length)
    }

    @Test fun `malformed enums fall back to safe values`() {
        val e = SessionEventCodec.bound(ev(kind = "shell", status = "passed", title = "   "))
        assertEquals(EventKind.STEP, e.kind)
        assertEquals(EventStatus.OK, e.status)
        assertEquals("Шаг", e.title)
        assertEquals(ev(), SessionEventCodec.bound(ev()))
    }

    @Test fun `migration 3 to 4 only adds columns with defaults and new tables`() {
        assertEquals(3, MIGRATION_3_4.startVersion)
        assertEquals(4, MIGRATION_3_4.endVersion)
        val alters = MIGRATION_3_4_SQL.filter { it.startsWith("ALTER") }
        assertEquals(5, alters.size)
        assertTrue(alters.all { "NOT NULL DEFAULT" in it })
        assertTrue(MIGRATION_3_4_SQL.none { it.startsWith("DROP") || it.startsWith("DELETE") || it.startsWith("INSERT") })
        assertTrue(MIGRATION_3_4_SQL.any { "`session_events`" in it } && MIGRATION_3_4_SQL.any { "`session_changes`" in it })
    }
}
