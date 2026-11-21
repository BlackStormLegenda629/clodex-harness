package app.clodex.harness

import app.clodex.harness.updates.ForegroundUpdateChecks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundUpdateChecksTest {
    @Test fun `a successful check stops retries and starts the cooldown`() = runTest {
        var now = 0L
        val waits = mutableListOf<Long>()
        var calls = 0
        val checks = ForegroundUpdateChecks(
            clock = { now }, retryDelays = listOf(0, 10, 30), wait = { waits += it },
        )

        checks.run { calls++; true }
        assertEquals(1, calls)
        assertEquals(emptyList<Long>(), waits)

        now = 59_999
        checks.run { calls++; true }
        assertEquals(1, calls)

        now = 60_000
        checks.run { calls++; true }
        assertEquals(2, calls)
    }

    @Test fun `a failed check retries every pause and keeps no cooldown`() = runTest {
        val waits = mutableListOf<Long>()
        var calls = 0
        val checks = ForegroundUpdateChecks(
            retryDelays = listOf(0, 10, 30), wait = { waits += it },
        )

        checks.run { calls++; false }
        assertEquals(3, calls)
        assertEquals(listOf(10L, 30L), waits)

        checks.run { calls++; false }
        assertEquals(6, calls)
        assertEquals(listOf(10L, 30L, 10L, 30L), waits)
    }
}
