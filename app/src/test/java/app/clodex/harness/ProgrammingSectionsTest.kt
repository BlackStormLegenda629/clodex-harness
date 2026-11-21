package app.clodex.harness

import app.clodex.harness.data.ProgrammingSection
import app.clodex.harness.data.programmingSections
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgrammingSectionsTest {
    @Test fun `programming page lists only supported sections in order`() {
        assertEquals(
            listOf(
                ProgrammingSection.CAVEMAN,
                ProgrammingSection.PERMISSIONS,
                ProgrammingSection.WORKSPACE,
                ProgrammingSection.MODE,
                ProgrammingSection.MODEL,
                ProgrammingSection.LINKS,
            ),
            programmingSections(),
        )
    }
}
