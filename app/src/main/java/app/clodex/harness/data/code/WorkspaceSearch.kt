package app.clodex.harness.data.code

import android.net.Uri
import app.clodex.harness.data.search.FileSearch
import app.clodex.harness.data.search.SearchHit
import app.clodex.harness.data.search.SearchQuery
import java.util.ArrayDeque

/** BFS over workspace files; reads go through [overlay] so staged edits are visible. */
class WorkspaceSearch(
    private val gateway: WorkspaceGateway,
    private val overlay: WorkspaceOverlay? = null,
) : FileSearch {
    override suspend fun search(q: String): List<SearchHit> = search(q, listOf(WorkspacePath.ROOT))

    suspend fun search(
        query: String,
        roots: List<WorkspacePath>,
        maxFiles: Int = 400,
        maxBytes: Long = 256L * 1024,
    ): List<SearchHit> {
        if (!SearchQuery.isSearchable(query)) return emptyList()
        val q = SearchQuery.normalize(query)
        val hits = mutableListOf<SearchHit>()
        val visited = HashSet<WorkspacePath>()
        val queue = ArrayDeque(roots)
        var filesScanned = 0

        while (queue.isNotEmpty() && hits.size < 100 && filesScanned < maxFiles) {
            val dir = queue.removeFirst()
            if (!visited.add(dir)) continue
            val entries = gateway.list(dir).getOrNull() ?: continue
            for (entry in entries) {
                if (hits.size >= 100 || filesScanned >= maxFiles) break
                if (entry.dir) {
                    queue.addLast(entry.path)
                    continue
                }
                if (!entry.text || entry.size > maxBytes) continue
                filesScanned++
                val text = (overlay?.read(entry.path) ?: gateway.read(entry.path)).getOrNull()?.text ?: continue
                if (text.contains(q, ignoreCase = true)) {
                    hits += SearchHit(
                        slice = app.clodex.harness.data.search.SearchSlice.FILES,
                        title = entry.path.value,
                        snippet = SearchQuery.snippet(text, q),
                        route = "browse?uri=" + Uri.encode(entry.path.value),
                    )
                }
            }
        }
        return hits
    }
}
