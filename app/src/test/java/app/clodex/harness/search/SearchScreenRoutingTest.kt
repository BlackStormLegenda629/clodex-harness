package app.clodex.harness.search

import app.clodex.harness.ui.nav.RouteParser
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchScreenRoutingTest {
    @Test fun allHitRoutesAreParsable() {
        listOf("chat/1", "session/2", "diff/3", "artifact/4", "browse?uri=a.txt")
            .forEach { assertTrue("Нераспознанный маршрут: $it", RouteParser.parse(it) != null) }
    }
}
