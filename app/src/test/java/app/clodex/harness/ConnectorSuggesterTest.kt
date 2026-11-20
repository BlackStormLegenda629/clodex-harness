package app.clodex.harness

import app.clodex.harness.data.ConnectorSuggester
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectorSuggesterTest {
    @Test fun findsRussianAndEnglishNamesInMentionOrder() {
        assertEquals(listOf("fg", "nt"), ConnectorSuggester.find("Сделай в Фигме, перенеси в Notion и GitHub").map { it.id })
        assertEquals(emptyList<String>(), ConnectorSuggester.find("x = 2").map { it.id })
    }
}
