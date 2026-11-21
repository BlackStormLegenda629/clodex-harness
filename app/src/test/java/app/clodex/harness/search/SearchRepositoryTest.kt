package app.clodex.harness.search

import app.clodex.harness.data.search.FileSearch
import app.clodex.harness.data.search.SearchHit
import app.clodex.harness.data.search.SearchRepository
import app.clodex.harness.data.search.SearchSlice
import app.clodex.harness.data.search.SearchSources
import app.clodex.harness.data.ArtifactEntity
import app.clodex.harness.data.ChatEntity
import app.clodex.harness.data.MessageEntity
import app.clodex.harness.data.SessionChangeEntity
import app.clodex.harness.data.SessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRepositoryTest {
    private class FakeSources : SearchSources {
        var calls = 0
        override suspend fun chats(q: String): List<ChatEntity> { calls++; return emptyList() }
        override suspend fun messages(q: String): List<MessageEntity> { calls++; return emptyList() }
        override suspend fun sessions(q: String): List<SessionEntity> { calls++; return emptyList() }
        override suspend fun changes(q: String): List<SessionChangeEntity> { calls++; return emptyList() }
        override suspend fun artifacts(q: String): List<ArtifactEntity> { calls++; return emptyList() }
    }

    @Test fun shortQuerySkipsAllSources() = runBlocking {
        val sources = FakeSources()
        val files = object : FileSearch {
            override suspend fun search(q: String): List<SearchHit> { throw AssertionError("should not scan") }
        }
        val repo = SearchRepository(sources, files)
        assertTrue(repo.search("а").isEmpty())
        assertEquals(0, sources.calls)
    }

    @Test fun resultsAreCappedAndSorted() = runBlocking {
        val many = (0 until 300).map { i ->
            SearchHit(SearchSlice.CHATS, "title-$i", "snippet", "chat/$i", score = i)
        }
        val files = object : FileSearch {
            override suspend fun search(q: String): List<SearchHit> = many
        }
        val repo = SearchRepository(FakeSources(), files)
        val result = repo.search("query")
        assertEquals(100, result.size)
        assertEquals("title-299", result.first().title)
        assertTrue(result.zipWithNext().all { (a, b) -> a.score >= b.score })
    }
}
