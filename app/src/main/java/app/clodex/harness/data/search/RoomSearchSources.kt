package app.clodex.harness.data.search

import app.clodex.harness.data.ArtifactEntity
import app.clodex.harness.data.ChatEntity
import app.clodex.harness.data.HarnessDb
import app.clodex.harness.data.MessageEntity
import app.clodex.harness.data.SessionChangeEntity
import app.clodex.harness.data.SessionEntity

/** Adapts Room DAOs to the testable [SearchSources] seam. */
class RoomSearchSources(private val db: HarnessDb) : SearchSources {
    override suspend fun chats(q: String): List<ChatEntity> = db.chats().searchByTitle(q)
    override suspend fun messages(q: String): List<MessageEntity> = db.messages().search(q)
    override suspend fun sessions(q: String): List<SessionEntity> = db.sessions().search(q)
    override suspend fun changes(q: String): List<SessionChangeEntity> = db.sessions().searchChanges(q)
    override suspend fun artifacts(q: String): List<ArtifactEntity> = db.artifacts().search(q)
}
