package edu.cit.resqmesh.feature.sos.alert

import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.notification.NotificationHelper
import edu.cit.resqmesh.core.notification.NotificationPreferences
import edu.cit.resqmesh.core.notification.SosMessageNotifier
import edu.cit.resqmesh.core.notification.SosMessageRules
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One replaceable notification per SOS thread, with sound limited per thread. */
class SosMessageController(
    private val notifications: NotificationHelper,
    private val preferences: NotificationPreferences,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) : SosMessageNotifier {
    private val lastSoundAt = ConcurrentHashMap<String, Long>()
    @Volatile private var visibleThread: String? = null

    init {
        scope.launch {
            preferences.sosMessageAlertsEnabled.collect { if (!it) notifications.clearAllSosMessages() }
        }
        scope.launch {
            preferences.mutedSosThreads.collect { muted -> muted.forEach(notifications::clearSosMessages) }
        }
    }

    override fun received(message: ChatMessage) {
        val id = message.sosId
        val now = clock()
        val notify = SosMessageRules.shouldNotify(
            enabled = preferences.sosMessageAlertsEnabled.value,
            threadMuted = id in preferences.mutedSosThreads.value,
            threadVisible = id == visibleThread,
            sentAtMs = message.timestamp,
            nowMs = now
        )
        if (!notify) return
        val sound = SosMessageRules.playsSound(lastSoundAt[id], now)
        if (sound) lastSoundAt[id] = now
        val sender = NodeIdentity.displayNameOf(message.senderName).ifBlank { message.senderName }
        notifications.showSosMessageNotification(id, sender, message.text, sound)
    }

    override fun threadVisible(id: String, visible: Boolean) {
        if (visible) {
            visibleThread = id
            notifications.clearSosMessages(id)
        } else if (visibleThread == id) {
            visibleThread = null
        }
    }
}
