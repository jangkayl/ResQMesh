package edu.cit.resqmesh.core.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosMessageRulesTest {
    private val now = 1_000_000_000L

    private fun notifies(
        enabled: Boolean = true, muted: Boolean = false, visible: Boolean = false, sentAt: Long = now
    ) = SosMessageRules.shouldNotify(enabled, muted, visible, sentAt, now)

    @Test fun aFreshMessageNotifiesByDefault() = assertTrue(notifies())

    @Test fun settingsMuteAndAnOpenChatStayQuiet() {
        assertFalse(notifies(enabled = false))
        assertFalse(notifies(muted = true))
        assertFalse(notifies(visible = true))
    }

    @Test fun messagesPastTenMinutesStayQuiet() {
        assertTrue(notifies(sentAt = now - SosMessageRules.MAX_AGE_MS))
        assertFalse(notifies(sentAt = now - SosMessageRules.MAX_AGE_MS - 1))
    }

    @Test fun soundPlaysAtMostOnceEveryThirtySeconds() {
        assertTrue(SosMessageRules.playsSound(null, now))
        assertFalse(SosMessageRules.playsSound(now - SosMessageRules.SOUND_GAP_MS + 1, now))
        assertTrue(SosMessageRules.playsSound(now - SosMessageRules.SOUND_GAP_MS, now))
    }
}
