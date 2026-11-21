package app.clodex.harness.search

import app.clodex.harness.data.search.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {
    @Test fun shortAndBlankQueriesAreNotSearchable() {
        assertFalse(SearchQuery.isSearchable(""))
        assertFalse(SearchQuery.isSearchable(" "))
        assertFalse(SearchQuery.isSearchable("а"))
        assertTrue(SearchQuery.isSearchable("ab"))
    }

    @Test fun snippetIsBoundedAndMarksTruncation() {
        val text = "x".repeat(240) + "НУЖНО" + "y".repeat(240)
        val snippet = SearchQuery.snippet(text, "нужно", radius = 60)
        assertTrue(snippet.length <= 2 * 60 + 2)
        assertTrue(snippet.contains("…"))
        assertTrue(snippet.contains("НУЖНО", ignoreCase = true))
    }
}
