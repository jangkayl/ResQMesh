package edu.cit.resqmesh.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sender-side piece progress of one message to one next hop. In memory only.
 * [State.HOP_COMPLETE] is hop custody: the next phone holds every piece, not a recipient receipt.
 */
data class SendProgress(
    val acked: Int,
    val total: Int,
    val state: State,
    val resent: Int = 0,
    val reprobes: Int = 0,
    val updatedAt: Long = 0
) {
    enum class State { QUEUED, SENDING, STALLED, HOP_COMPLETE, FAILED }

    val percent: Int get() = if (total <= 0) 0 else (acked * PERCENT / total).coerceIn(0, PERCENT)

    private companion object { const val PERCENT = 100 }
}

/** Live progress per message ID, combined across next hops (public messages may go to several neighbors). */
object SendProgressBoard {
    private val perPeer = mutableMapOf<String, Map<String, SendProgress>>()
    private val combined = MutableStateFlow<Map<String, SendProgress>>(emptyMap())
    val progress: StateFlow<Map<String, SendProgress>> = combined.asStateFlow()

    // The most urgent hop decides the combined state; FAILED shows only when every hop failed.
    private val statePriority = listOf(
        SendProgress.State.STALLED, SendProgress.State.SENDING, SendProgress.State.QUEUED,
        SendProgress.State.HOP_COMPLETE, SendProgress.State.FAILED
    )

    @Synchronized fun update(messageId: String, peer: String, progress: SendProgress) {
        if (messageId.isBlank()) return
        perPeer.entries.removeAll { (_, peers) -> peers.values.all { progress.updatedAt - it.updatedAt >= RETAIN_MS } }
        if (messageId !in perPeer && perPeer.size >= MAX_MESSAGES) {
            perPeer.minByOrNull { (_, peers) -> peers.values.maxOf { it.updatedAt } }?.let { perPeer.remove(it.key) }
        }
        perPeer[messageId] = perPeer[messageId].orEmpty() + (peer to progress)
        combined.value = perPeer.mapValues { combine(it.value.values) }
    }

    @Synchronized fun clear() {
        perPeer.clear(); combined.value = emptyMap()
    }

    internal fun combine(peers: Collection<SendProgress>): SendProgress = SendProgress(
        acked = peers.sumOf { it.acked },
        total = peers.sumOf { it.total },
        state = statePriority.first { state -> peers.any { it.state == state } },
        resent = peers.sumOf { it.resent },
        reprobes = peers.sumOf { it.reprobes },
        updatedAt = peers.maxOf { it.updatedAt }
    )

    /** Long enough to bridge custody and the delivery receipt; bounded so relays cannot grow it. */
    private const val RETAIN_MS = 10 * 60 * 1000L
    private const val MAX_MESSAGES = 64
}
