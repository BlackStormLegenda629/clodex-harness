package app.clodex.harness.data.search

enum class SearchSlice { MESSAGES, CHATS, SESSIONS, CHANGES, ARTIFACTS, FILES }

data class SearchHit(
    val slice: SearchSlice,
    val title: String,
    val snippet: String,
    val route: String,
    val score: Int = 0,
)
