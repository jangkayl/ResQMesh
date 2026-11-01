package edu.cit.resqmesh.core.notification

import edu.cit.resqmesh.core.model.ChatMessage

/** Notifies about live SOS chat messages. Separate from the alarm, which [SosAlertNotifier] owns. */
interface SosMessageNotifier {
    fun received(message: ChatMessage)
    fun threadVisible(id: String, visible: Boolean)
}

/** Pure notification rules, so old or already visible messages never alert. */
object SosMessageRules {
    const val MAX_AGE_MS = 10 * 60_000L
    const val SOUND_GAP_MS = 30_000L

    fun shouldNotify(enabled: Boolean, threadMuted: Boolean, threadVisible: Boolean, sentAtMs: Long, nowMs: Long) =
        enabled && !threadMuted && !threadVisible && nowMs - sentAtMs <= MAX_AGE_MS

    fun playsSound(lastSoundAtMs: Long?, nowMs: Long) =
        lastSoundAtMs == null || nowMs - lastSoundAtMs >= SOUND_GAP_MS
}
