package app.clodex.harness

import app.clodex.harness.data.MIGRATION_2_3
import app.clodex.harness.data.MIGRATION_2_3_SQL
import app.clodex.harness.data.ThinkingCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingCodecTest {
    @Test fun `empty list encodes to empty string and back`() {
        assertEquals("", ThinkingCodec.encode(emptyList()))
        assertEquals(emptyList<String>(), ThinkingCodec.decode(""))
    }

    @Test fun `unicode and newlines round trip`() {
        val steps = listOf("Ищу файлы 🔍", "строка 1\nстрока 2", "\"кавычки\"")
        assertEquals(steps, ThinkingCodec.decode(ThinkingCodec.encode(steps)))
    }

    @Test fun `malformed legacy data yields nothing`() {
        assertEquals(emptyList<String>(), ThinkingCodec.decode("[broken"))
        assertEquals(emptyList<String>(), ThinkingCodec.decode("{\"a\":1}"))
        assertEquals(listOf("ok"), ThinkingCodec.decode("[\"ok\", 5, null]"))
    }

    @Test fun `steps are bounded`() {
        val decoded = ThinkingCodec.decode(ThinkingCodec.encode(List(50) { "ш".repeat(400) }))
        assertEquals(30, decoded.size)
        assertTrue(decoded.all { it.length == 240 })
    }

    @Test fun `migration adds non null columns with defaults`() {
        assertEquals(2, MIGRATION_2_3.startVersion)
        assertEquals(3, MIGRATION_2_3.endVersion)
        assertTrue(MIGRATION_2_3_SQL.all { "NOT NULL DEFAULT" in it })
        assertTrue(MIGRATION_2_3_SQL.any { "thinkingSeconds" in it } && MIGRATION_2_3_SQL.any { "thinkingSteps" in it })
    }
}
