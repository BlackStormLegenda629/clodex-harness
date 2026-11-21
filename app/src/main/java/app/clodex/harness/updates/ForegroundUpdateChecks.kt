package app.clodex.harness.updates

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

/**
 * One foreground session retries transient failures; only a successful check starts the cooldown.
 * Mirrors the updater behaviour of AndroidHarness so a flaky network at launch does not hide updates.
 */
internal class ForegroundUpdateChecks(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val retryDelays: List<Long> = listOf(0, 10_000, 30_000),
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val cooldownMs: Long = 60_000,
) {
    private val running = Mutex()
    private var lastSuccess: Long? = null

    suspend fun run(check: suspend () -> Boolean) {
        if (!running.tryLock()) return
        try {
            if (lastSuccess?.let { clock() - it in 0 until cooldownMs } == true) return
            for (pause in retryDelays) {
                if (pause > 0) wait(pause)
                if (check()) {
                    lastSuccess = clock()
                    return
                }
            }
        } finally {
            running.unlock()
        }
    }
}
