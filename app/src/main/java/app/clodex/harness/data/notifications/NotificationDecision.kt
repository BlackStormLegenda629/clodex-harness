package app.clodex.harness.data.notifications

enum class NotifyEvent { APPROVAL_NEEDED, SESSION_DONE, REPLY_READY }

/** Maps user-facing notification toggles (n1/n2/n3) to their events. */
object NotificationDecision {
    fun shouldNotify(flags: Map<String, Boolean>, event: NotifyEvent): Boolean = when (event) {
        NotifyEvent.APPROVAL_NEEDED -> flags["n1"] == true
        NotifyEvent.SESSION_DONE -> flags["n2"] == true
        NotifyEvent.REPLY_READY -> flags["n3"] == true
    }
}
