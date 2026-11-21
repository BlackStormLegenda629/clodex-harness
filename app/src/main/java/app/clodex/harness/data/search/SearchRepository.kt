package app.clodex.harness.data.search

import app.clodex.harness.data.ArtifactEntity
import app.clodex.harness.data.ChatEntity
import app.clodex.harness.data.MessageEntity
import app.clodex.harness.data.SessionChangeEntity
import app.clodex.harness.data.SessionEntity

/** Merges every search slice into one ranked, capped list. */
class SearchRepository(private val sources: SearchSources, private val files: FileSearch) {
    suspend fun search(raw: String): List<SearchHit> {
        val q = SearchQuery.normalize(raw)
        if (!SearchQuery.isSearchable(raw)) return emptyList()

        val hits = mutableListOf<SearchHit>()
        sources.chats(q).forEach { hits += it.hit(q) }
        sources.messages(q).forEach { hits += it.hit(q) }
        sources.sessions(q).forEach { hits += it.hit(q) }
        sources.changes(q).forEach { hits += it.hit(q) }
        sources.artifacts(q).forEach { hits += it.hit(q) }
        hits += files.search(q)

        return hits.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title }).take(100)
    }

    private fun ChatEntity.hit(q: String) = SearchHit(
        SearchSlice.CHATS, title, SearchQuery.snippet(title, q), "chat/$id", score = 3,
    )

    private fun MessageEntity.hit(q: String) = SearchHit(
        SearchSlice.MESSAGES, role, SearchQuery.snippet(text, q), "chat/$chatId", score = 2,
    )

    private fun SessionEntity.hit(q: String) = SearchHit(
        SearchSlice.SESSIONS, title, SearchQuery.snippet(summary.ifBlank { diff }, q), "session/$id", score = 3,
    )

    private fun SessionChangeEntity.hit(q: String) = SearchHit(
        SearchSlice.CHANGES, path, SearchQuery.snippet(afterText, q), "diff/$sessionId", score = 1,
    )

    private fun ArtifactEntity.hit(q: String) = SearchHit(
        SearchSlice.ARTIFACTS, name, SearchQuery.snippet(content, q), "artifact/$id", score = 2,
    )
}
