package app.clodex.harness.data.search

import app.clodex.harness.data.ArtifactEntity
import app.clodex.harness.data.ChatEntity
import app.clodex.harness.data.MessageEntity
import app.clodex.harness.data.SessionChangeEntity
import app.clodex.harness.data.SessionEntity

/** Room-backed sources for cross search. Kept as an interface so the repository is JVM-testable. */
interface SearchSources {
    suspend fun chats(q: String): List<ChatEntity>
    suspend fun messages(q: String): List<MessageEntity>
    suspend fun sessions(q: String): List<SessionEntity>
    suspend fun changes(q: String): List<SessionChangeEntity>
    suspend fun artifacts(q: String): List<ArtifactEntity>
}

/** Implemented by WorkspaceSearch; file hits are folded into the same result list. */
interface FileSearch {
    suspend fun search(q: String): List<SearchHit>
}
