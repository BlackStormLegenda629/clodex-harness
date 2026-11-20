package app.clodex.harness

import app.clodex.harness.data.ScheduleRepeat
import org.junit.Assert.*
import org.junit.Test

class ScheduleTimingTest {
    @Test fun missedIntervalsAreSkippedNotReplayed() {
        val hour = 3_600_000L
        assertEquals(100 + 4 * hour, ScheduleRepeat.Hourly.nextAfter(100, 3 * hour + 123))
        assertEquals(100 + 24 * hour, ScheduleRepeat.Daily.nextAfter(100, 2 * hour))
        assertNull(ScheduleRepeat.Once.nextAfter(100, 200))
    }
}
