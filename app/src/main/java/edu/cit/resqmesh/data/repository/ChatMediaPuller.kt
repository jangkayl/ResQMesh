package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.network.DetachedSender
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.network.MeshPayload
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal data class ChatSyncLink(val node: String, val endpoint: String, val generation: Long)

/** Sync copies travel one hop: TTL 1, late lane, and only to the neighbor that asked. */
internal fun chatSyncMediaFrame(message: ChatMessage): ByteArray = PayloadFactory.buildPublicPayload(
    msgId = message.id, timestamp = message.timestamp, senderName = message.senderName, text = message.text,
    imageBase64 = message.imageBase64, audioBase64 = message.audioBase64, locationLat = message.locationLat,
    locationLng = message.locationLng, isSOS = false, isSOSCancel = false, channelId = message.channelId, ttl = 1,
    conversationKind = message.conversationKind, sosId = message.sosId, senderNodeId = message.senderNodeId,
    chatSync = true
)

/**
 * Fills placeholders one at a time, newest first, after a new link settles. Sending uses the late lane, so a live
 * transfer on the link pauses the copy, which resumes from its saved pieces.
 */
internal class ChatMediaPuller(
    private val store: MessageStore,
    private val gateway: MeshNetworkGateway,
    private val sink: ChatSyncSink,
    private val peers: MeshReadyPeerEvents,
    private val request: suspend (ChatSyncLink, ChatSyncPacket) -> ChatSyncPacket
) {
    /** Who was asked for each id. It outlives the wait, so a copy that arrives late still counts. */
    private val asked = ConcurrentHashMap<String, String>()
    private val arrivals = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val spent = ConcurrentHashMap<ChatSyncLink, Long>()

    fun retain(current: (ChatSyncLink) -> Boolean) = spent.keys.retainAll(current)

    suspend fun pull(l: ChatSyncLink) {
        if (store.pendingSyncMedia(MAX_PER_CONVERSATION).isEmpty()) return
        if (spent.putIfAbsent(l, 0L) == null) delay(SETTLE_MS)
        val tried = mutableSetOf<String>()
        var next = nextFor(l, tried)
        while (next != null) {
            tried += next
            fetch(l, next)
            next = nextFor(l, tried)
        }
    }

    private suspend fun nextFor(l: ChatSyncLink, tried: Set<String>): String? =
        if (spent.getValue(l) >= MAX_BYTES_PER_NEIGHBOR) null
        else store.pendingSyncMedia(MAX_PER_CONVERSATION).firstOrNull { it !in tried && !arrivals.containsKey(it) }

    private suspend fun fetch(l: ChatSyncLink, id: String) {
        val arrived = CompletableDeferred<Unit>()
        if (arrivals.putIfAbsent(id, arrived) != null) return
        try {
            asked[id] = l.node
            val answer = request(l, ChatSyncPacket(UUID.randomUUID().toString(), ChatSyncCoordinator.MEDIA,
                ids = listOf(id), cursor = (MAX_BYTES_PER_NEIGHBOR - spent.getValue(l)).toInt()))
            if (answer.size > 0) {
                spent.merge(l, answer.size.toLong(), Long::plus)
                withTimeoutOrNull(ARRIVAL_TIMEOUT_MS) { arrived.await() }
            }
        } finally {
            arrivals.remove(id, arrived)
        }
    }

    /** Offers one copy to the neighbor that asked. A zero size tells it to try someone else. */
    suspend fun serve(l: ChatSyncLink, packet: ChatSyncPacket, pages: ChatSyncPages): ChatSyncPacket {
        val frame = pages.mediaCopy(packet)
        val accepted = frame != null && gateway.sendSyncCopy(l.node, frame).accepted
        return ChatSyncPacket(packet.request, ChatSyncCoordinator.MEDIA, size = if (accepted) frame!!.size else 0)
    }

    /** Only a copy this phone asked that neighbor for is stored; custody is released either way. */
    suspend fun receive(endpoint: String, payload: MeshPayload) {
        val peer = DetachedSender.peerOf(endpoint)
            ?: peers.peers.value.firstOrNull { it.endpointId == endpoint }?.nodeId
        val requested = peer != null && asked[payload.id].equals(peer, ignoreCase = true)
        if (requested && sink.ingestMedia(payload)) {
            asked.remove(payload.id)
            arrivals[payload.id]?.complete(Unit)
            AppLogger.d("CHAT_SYNC", "media filled peer=$peer")
        }
        gateway.markPayloadStored(payload.id)
    }

    companion object {
        const val MAX_PER_CONVERSATION = 10
        const val MAX_BYTES_PER_NEIGHBOR = 1024L * 1024L
        const val SETTLE_MS = 10_000L
        private const val ARRIVAL_TIMEOUT_MS = 120_000L
    }
}
