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
    val updatedAt: Long = 0,
    /** Waiting for an earlier, larger transfer to the same next hop to finish first. */
    val heldBehind: Boolean = false,
    /** The transfer (frame digest) this report belongs to; a new one is a fresh transmission. */
    val transferId: String = "",
    /** Starting over: the receiver reset the pieces, or an earlier transmission of the message was replaced. */
    val restarted: Boolean = false
) {
    enum class State { QUEUED, SENDING, STALLED, HOP_COMPLETE, FAILED }

    val percent: Int get() = if (total <= 0) 0 else (acked * PERCENT / total).coerceIn(0, PERCENT)

    private companion object { const val PERCENT = 100 }
}

/**
 * Live progress per message ID, combined across next hops (public messages may go to several neighbors).
 * A report from a new transfer of a message replaces that message's older entries instead of adding to them.
 */
object SendProgressBoard {
    private class Entry(
        val hops: Map<String, SendProgress>,
        val retired: Set<String> = emptySet(),
        val resending: Boolean = false
    )

    private val messages = mutableMapOf<String, Entry>()
    private val combined = MutableStateFlow<Map<String, SendProgress>>(emptyMap())
    val progress: StateFlow<Map<String, SendProgress>> = combined.asStateFlow()

    // The most urgent hop decides the combined state; FAILED shows only when every hop failed.
    private val statePriority = listOf(
        SendProgress.State.STALLED, SendProgress.State.SENDING, SendProgress.State.QUEUED,
        SendProgress.State.HOP_COMPLETE, SendProgress.State.FAILED
    )

    @Synchronized fun update(messageId: String, peer: String, progress: SendProgress) {
        if (messageId.isBlank()) return
        messages.entries.removeAll { (_, entry) ->
            entry.hops.values.all { progress.updatedAt - it.updatedAt >= RETAIN_MS }
        }
        if (messageId !in messages && messages.size >= MAX_MESSAGES) {
            messages.minByOrNull { (_, entry) -> entry.hops.values.maxOf { it.updatedAt } }
                ?.let { messages.remove(it.key) }
        }
        val next = nextEntry(messages[messageId], peer, progress) ?: return
        messages[messageId] = next
        publish()
    }

    /** Null when the report belongs to a transmission this message already replaced. */
    private fun nextEntry(current: Entry?, peer: String, progress: SendProgress): Entry? {
        if (current != null && progress.transferId in current.retired) return null
        val ids = current?.hops?.values?.map { it.transferId }?.toSet().orEmpty()
        val fresh = current != null && current.hops.isNotEmpty() && progress.transferId !in ids
        val retired = if (fresh) (current!!.retired + ids).toList().takeLast(MAX_RETIRED).toSet()
            else current?.retired.orEmpty()
        val resending = fresh || current?.resending == true
        val hops = if (fresh) emptyMap() else current?.hops.orEmpty()
        val shown = progress.copy(restarted = progress.restarted || resending)
        return Entry(hops + ("$peer|${progress.transferId}" to shown), retired, resending)
    }

    /** Forgets a message, for example after the sender cancels it. */
    @Synchronized fun remove(messageId: String) {
        if (messages.remove(messageId) != null) publish()
    }

    @Synchronized fun clear() {
        messages.clear(); combined.value = emptyMap()
    }

    private fun publish() {
        combined.value = messages.mapValues { combine(it.value.hops.values) }
    }

    internal fun combine(peers: Collection<SendProgress>): SendProgress = SendProgress(
        acked = peers.sumOf { it.acked },
        total = peers.sumOf { it.total },
        state = statePriority.first { state -> peers.any { it.state == state } },
        resent = peers.sumOf { it.resent },
        reprobes = peers.sumOf { it.reprobes },
        updatedAt = peers.maxOf { it.updatedAt },
        heldBehind = peers.all { it.heldBehind },
        transferId = peers.maxBy { it.updatedAt }.transferId,
        restarted = peers.any { it.restarted }
    )

    /** Long enough to bridge custody and the delivery receipt; bounded so relays cannot grow it. */
    private const val RETAIN_MS = 10 * 60 * 1000L
    private const val MAX_MESSAGES = 64
    private const val MAX_RETIRED = 8
}
