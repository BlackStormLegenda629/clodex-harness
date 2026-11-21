package app.clodex.harness.notifications

import app.clodex.harness.data.notifications.NotifyEvent
import app.clodex.harness.data.notifications.NotificationDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDecisionTest {
    @Test fun flagsControlEvents() {
        val none = emptyMap<String, Boolean>()
        assertFalse(NotificationDecision.shouldNotify(none, NotifyEvent.APPROVAL_NEEDED))
        assertFalse(NotificationDecision.shouldNotify(none, NotifyEvent.SESSION_DONE))
        assertFalse(NotificationDecision.shouldNotify(none, NotifyEvent.REPLY_READY))

        val all = mapOf("n1" to true, "n2" to true, "n3" to true)
        assertTrue(NotificationDecision.shouldNotify(all, NotifyEvent.APPROVAL_NEEDED))
        assertTrue(NotificationDecision.shouldNotify(all, NotifyEvent.SESSION_DONE))
        assertTrue(NotificationDecision.shouldNotify(all, NotifyEvent.REPLY_READY))

        val onlyN2 = mapOf("n1" to false, "n2" to true, "n3" to false)
        assertFalse(NotificationDecision.shouldNotify(onlyN2, NotifyEvent.APPROVAL_NEEDED))
        assertTrue(NotificationDecision.shouldNotify(onlyN2, NotifyEvent.SESSION_DONE))
        assertFalse(NotificationDecision.shouldNotify(onlyN2, NotifyEvent.REPLY_READY))
    }

    @Test fun missingFlagDefaultsToOff() {
        assertFalse(NotificationDecision.shouldNotify(mapOf("n1" to true), NotifyEvent.REPLY_READY))
    }
}
