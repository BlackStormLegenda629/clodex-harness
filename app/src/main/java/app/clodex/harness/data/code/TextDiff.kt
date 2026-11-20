package app.clodex.harness.data.code

enum class DiffKind { SAME, ADD, DEL }

data class DiffLine(val kind: DiffKind, val text: String)

data class DiffSummary(val added: Int, val removed: Int) {
    override fun toString() = "+$added −$removed"
}

/** Deterministic line diff: common prefix/suffix, then LCS on the middle when it is small enough. */
object TextDiff {
    const val MAX_CELLS = 4_000_000

    private fun split(s: String?): List<String> = if (s.isNullOrEmpty()) emptyList() else s.removeSuffix("\n").split('\n')

    fun lines(before: String?, after: String): List<DiffLine> {
        val a = split(before); val b = split(after)
        var pre = 0
        while (pre < a.size && pre < b.size && a[pre] == b[pre]) pre++
        var suf = 0
        while (suf < a.size - pre && suf < b.size - pre && a[a.size - 1 - suf] == b[b.size - 1 - suf]) suf++
        val am = a.subList(pre, a.size - suf); val bm = b.subList(pre, b.size - suf)
        val out = ArrayList<DiffLine>(a.size + b.size)
        a.subList(0, pre).forEach { out += DiffLine(DiffKind.SAME, it) }
        if (am.size.toLong() * bm.size <= MAX_CELLS) out += lcs(am, bm)
        else { am.forEach { out += DiffLine(DiffKind.DEL, it) }; bm.forEach { out += DiffLine(DiffKind.ADD, it) } }
        a.subList(a.size - suf, a.size).forEach { out += DiffLine(DiffKind.SAME, it) }
        return out
    }

    private fun lcs(a: List<String>, b: List<String>): List<DiffLine> {
        val n = a.size; val m = b.size
        if (n == 0) return b.map { DiffLine(DiffKind.ADD, it) }
        if (m == 0) return a.map { DiffLine(DiffKind.DEL, it) }
        val t = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            t[i][j] = if (a[i] == b[j]) t[i + 1][j + 1] + 1 else maxOf(t[i + 1][j], t[i][j + 1])
        }
        val out = ArrayList<DiffLine>(n + m)
        var i = 0; var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { out += DiffLine(DiffKind.SAME, a[i]); i++; j++ }
                t[i + 1][j] >= t[i][j + 1] -> { out += DiffLine(DiffKind.DEL, a[i]); i++ }
                else -> { out += DiffLine(DiffKind.ADD, b[j]); j++ }
            }
        }
        while (i < n) out += DiffLine(DiffKind.DEL, a[i++])
        while (j < m) out += DiffLine(DiffKind.ADD, b[j++])
        return out
    }

    fun summarize(before: String?, after: String): DiffSummary {
        val l = lines(before, after)
        return DiffSummary(l.count { it.kind == DiffKind.ADD }, l.count { it.kind == DiffKind.DEL })
    }

    fun total(changes: List<Pair<String?, String>>): DiffSummary =
        changes.map { summarize(it.first, it.second) }.fold(DiffSummary(0, 0)) { s, d -> DiffSummary(s.added + d.added, s.removed + d.removed) }
}
