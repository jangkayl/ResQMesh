package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.ratelimit.SingleFlightGate
import edu.cit.resqmesh.data.local.entity.ChatSyncEntry
import edu.cit.resqmesh.data.local.entity.ChatSyncIngestion
import edu.cit.resqmesh.data.local.entity.ChatSyncRow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf

@Serializable
internal data class ChatSyncPacket(
    val request: String, val op: String = ChatSyncCoordinator.INDEX, val digest: String = "", val cursor: Int = 0,
    val next: Int = -1, val unchanged: Boolean = false, val ids: List<String> = emptyList(),
    val entries: List<ChatSyncEntry> = emptyList(), val rows: List<ChatSyncRow> = emptyList(), val size: Int = 0
)

internal val chatSyncWire = Json { ignoreUnknownKeys = true }

/** Takes pulled rows and attachments into local storage. */
interface ChatSyncSink {
    suspend fun ingest(row: ChatSyncRow): ChatSyncIngestion
    suspend fun ingestMedia(payload: MeshPayload): Boolean
}

private fun newChatSyncRequest() = UUID.randomUUID().toString()

/** Builds answers from local history. Small pages stay in the small-frame lane, so sync yields to live messages. */
internal class ChatSyncPages(
    private val store: MessageStore,
    private val blocked: (String) -> Boolean,
    private val mediaFrame: (ChatMessage) -> ByteArray
) {
    suspend fun answer(packet: ChatSyncPacket): ChatSyncPacket =
        if (packet.op == ChatSyncCoordinator.ROWS) rowsPage(packet) else indexPage(packet)

    /** The rebuilt frame for one held attachment, when it fits the budget the requester has left. */
    suspend fun mediaCopy(packet: ChatSyncPacket): ByteArray? =
        packet.ids.firstOrNull()?.let { store.chatSyncMessage(it) }?.takeUnless { blocked(it.senderName) }
            ?.let(mediaFrame)?.takeIf { it.size <= packet.cursor }

    private suspend fun indexPage(packet: ChatSyncPacket): ChatSyncPacket {
        val entries = store.chatSyncIndex(
            ChatSyncCoordinator.MAX_MESSAGES_PER_CONVERSATION, ChatSyncCoordinator.MAX_SOS_THREADS
        ).filterNot { blocked(it.senderName) }
        val digest = SosProtocol.hash(chatSyncWire.encodeToString(entries))
        val reply = ChatSyncPacket(packet.request, digest = digest)
        return when {
            packet.cursor == 0 && packet.digest == digest -> reply.copy(unchanged = true)
            packet.cursor > 0 && packet.digest != digest -> reply
            else -> {
                val page = entries.drop(packet.cursor).take(MAX_ENTRIES_PER_PAGE)
                    .fitted { chatSyncWire.encodeToString(it).length }
                val end = packet.cursor + page.size
                reply.copy(entries = page, next = if (end < entries.size) end else -1)
            }
        }
    }

    private suspend fun rowsPage(packet: ChatSyncPacket): ChatSyncPacket {
        val rows = store.chatSyncRows(packet.ids).filterNot { blocked(it.senderName) }
        return ChatSyncPacket(packet.request, ChatSyncCoordinator.ROWS,
            rows = rows.fitted { chatSyncWire.encodeToString(it).toByteArray().size })
    }

    private fun <T> List<T>.fitted(size: (T) -> Int): List<T> {
        var used = 0
        return takeWhile { item ->
            val itemSize = size(item)
            used += itemSize
            used <= MAX_PAGE_BYTES || (used == itemSize && used <= MAX_LONE_ITEM_BYTES)
        }
    }

    companion object {
        const val MAX_ENTRIES_PER_PAGE = 64
        private const val MAX_PAGE_BYTES = 3_584
        private const val MAX_LONE_ITEM_BYTES = 12_288
    }
}

private fun ChatSyncEntry.describes(row: ChatSyncRow) =
    row.kind == kind && row.sosId == sosId && row.sentAt == sentAt && row.senderName == senderName

private fun decodeChatSync(payload: MeshPayload): ChatSyncPacket? =
    payload.text.takeIf { it.toByteArray().size <= ChatSyncCoordinator.MAX_PACKET_TEXT_BYTES }
        ?.let { text -> runCatching { chatSyncWire.decodeFromString<ChatSyncPacket>(text) }.getOrNull() }
        ?.takeIf {
            it.request.length in 1..ChatSyncCoordinator.MAX_REQUEST_LENGTH && it.cursor >= 0 &&
                it.ids.size <= ChatSyncCoordinator.MAX_ROWS_PER_PAGE &&
                it.rows.size <= ChatSyncCoordinator.MAX_ROWS_PER_PAGE &&
                it.entries.size <= ChatSyncPages.MAX_ENTRIES_PER_PAGE
        }

/**
 * Pulls recent Community and SOS text from direct neighbors. A row is taken only when its id was asked for on
 * that link, is stored silently and is never relayed onward; each neighbor's own sync spreads it further.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, FlowPreview::class)
@Suppress("LongParameterList") // the media frame builder is a seam over Android Base64
internal class ChatSyncCoordinator(
    private val gateway: MeshNetworkGateway, private val peers: MeshReadyPeerEvents,
    private val store: MessageStore, private val scope: CoroutineScope,
    private val sink: ChatSyncSink,
    changes: Flow<Unit>,
    mediaFrame: (ChatMessage) -> ByteArray = ::chatSyncMediaFrame
) {
    private data class Waiting(val link: ChatSyncLink, val result: CompletableDeferred<ChatSyncPacket>)
    private class Index(val digest: String, val entries: List<ChatSyncEntry>)
    private val jobs = ConcurrentHashMap<ChatSyncLink, Job>()
    private val mediaJobs = ConcurrentHashMap<ChatSyncLink, Job>()
    private val media = ChatMediaPuller(store, gateway, sink, peers, ::request)
    private val waiting = ConcurrentHashMap<String, Waiting>()
    private val synced = ConcurrentHashMap<String, String>()
    private val requestGate = SingleFlightGate(REQUEST)
    private val pages = ChatSyncPages(store, gateway::isDeviceBlocked, mediaFrame)

    init {
        gateway.onChatSyncPacket = ::receive
        scope.launch { peers.peers.collect { refresh() } }
        scope.launch { changes.debounce(CHANGE_DEBOUNCE_MS).collect { refresh() } }
        scope.launch {
            while (isActive) {
                delay(RESYNC_INTERVAL_MS + Random.nextLong(-RESYNC_JITTER_MS, RESYNC_JITTER_MS + 1))
                refresh()
            }
        }
    }

    private fun link(p: ConnectedDevice) = ChatSyncLink(p.nodeId, p.endpointId, gateway.linkEstablishedAt(p.endpointId))
    private fun current(l: ChatSyncLink) = gateway.hasReadyEndpoint(l.endpoint) && peers.peers.value.any {
        link(it) == l && !gateway.isDeviceBlocked(it.name)
    }

    @Synchronized private fun refresh() {
        listOf(jobs, mediaJobs).forEach { running ->
            running.entries.toList().forEach { (l, job) -> if (!current(l)) { job.cancel(); running.remove(l, job) } }
        }
        media.retain { current(it) }
        peers.peers.value.filter { it.nodeId.isNotBlank() }.forEach { peer ->
            val l = link(peer)
            if (current(l)) {
                launchOnce(jobs, l) { reconcile(l) }
                launchOnce(mediaJobs, l) { media.pull(l) }
            }
        }
    }

    private fun launchOnce(running: ConcurrentHashMap<ChatSyncLink, Job>, l: ChatSyncLink, work: suspend () -> Unit) {
        if (running[l]?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { work() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { AppLogger.d(TAG, "pending peer=${l.node}") }
        }
        running[l] = job; job.start()
    }

    private suspend fun reconcile(l: ChatSyncLink) {
        val index = fetchIndex(l) ?: return
        val wanted = index.entries.filter { !gateway.isDeviceBlocked(it.senderName) && !store.contains(it.id) }
        if (pull(l, wanted)) synced[l.node] = index.digest
        if (wanted.any { it.mediaKind.isNotEmpty() }) refresh()
        AppLogger.d(TAG, "pass peer=${l.node} offered=${index.entries.size} wanted=${wanted.size}")
    }

    /** Null when the neighbor still offers what was last taken from it, or its list moved mid-read. */
    private suspend fun fetchIndex(l: ChatSyncLink): Index? {
        val first = request(l, ChatSyncPacket(newChatSyncRequest(), digest = synced[l.node].orEmpty()))
        val entries = first.entries.toMutableList()
        var next = if (first.unchanged) -1 else first.next
        var stable = !first.unchanged
        var read = 1
        while (stable && next >= 0 && read++ < MAX_INDEX_PAGES) {
            val page = request(l, ChatSyncPacket(newChatSyncRequest(), digest = first.digest, cursor = next))
            if (page.next in 0..next) error("Invalid chat sync cursor")
            stable = page.digest == first.digest
            entries += page.entries
            next = page.next
        }
        return if (stable) Index(first.digest, entries) else null
    }

    /** True when nothing is left to retry from this neighbor. */
    private suspend fun pull(l: ChatSyncLink, wanted: List<ChatSyncEntry>): Boolean {
        var remaining = wanted
        var deferred = false
        var read = 0
        while (remaining.isNotEmpty() && read++ < MAX_ROW_PAGES) {
            val batch = remaining.take(MAX_ROWS_PER_PAGE).associateBy { it.id }
            val page = request(l, ChatSyncPacket(newChatSyncRequest(), ROWS, ids = batch.keys.toList()))
            val served = page.rows.filter { batch[it.id]?.describes(it) == true }
            served.forEach { if (sink.ingest(it) == ChatSyncIngestion.DEFERRED) deferred = true }
            val done = if (served.isEmpty()) batch.keys else served.map { it.id }.toSet()
            remaining = remaining.filterNot { it.id in done }
        }
        return remaining.isEmpty() && !deferred
    }

    private suspend fun request(l: ChatSyncLink, packet: ChatSyncPacket): ChatSyncPacket {
        val response = CompletableDeferred<ChatSyncPacket>()
        waiting[packet.request] = Waiting(l, response)
        try {
            repeat(MAX_REQUEST_ATTEMPTS) { attempt ->
                check(current(l))
                gateway.sendPriorityPayload(l.endpoint, envelope(REQUEST, packet))
                val answer = withTimeoutOrNull(REQUEST_TIMEOUT_MS * (attempt + 1)) { response.await() }
                if (answer != null) { check(current(l)); return answer }
            }
            error("Chat sync timeout")
        } finally { waiting.remove(packet.request) }
    }

    private fun envelope(type: String, packet: ChatSyncPacket) = ProtoBuf.encodeToByteArray(MeshPayload(
        id = UUID.randomUUID().toString(), type = type, text = chatSyncWire.encodeToString(packet),
        senderNodeId = gateway.myNodeId))

    fun receive(endpoint: String, payload: MeshPayload) {
        if (payload.type == "CONVERSATION") {
            scope.launch { media.receive(endpoint, payload) }
            return
        }
        val packet = decodeChatSync(payload) ?: return
        if (payload.type == RESPONSE) {
            waiting[packet.request]?.takeIf { it.link.endpoint == endpoint && current(it.link) }
                ?.result?.complete(packet)
        } else peers.peers.value.firstOrNull { it.endpointId == endpoint }?.let(::link)?.let { l ->
            scope.launch { requestGate.run(endpoint) {
                val reply = if (packet.op == MEDIA) media.serve(l, packet, pages) else pages.answer(packet)
                if (current(l)) gateway.sendDirectPayload(endpoint, envelope(RESPONSE, reply))
            } }
        }
    }

    companion object {
        const val INDEX = "INDEX"
        const val ROWS = "ROWS"
        const val MEDIA = "MEDIA"
        const val REQUEST = "CHAT_SYNC_REQ"
        const val RESPONSE = "CHAT_SYNC_RES"
        const val MAX_MESSAGES_PER_CONVERSATION = 50
        const val MAX_SOS_THREADS = 8
        const val MAX_ROWS_PER_PAGE = 8
        const val MAX_REQUEST_LENGTH = 100
        const val MAX_PACKET_TEXT_BYTES = 16_384
        private const val TAG = "CHAT_SYNC"
        private const val CHANGE_DEBOUNCE_MS = 500L
        private const val RESYNC_INTERVAL_MS = 30_000L
        private const val RESYNC_JITTER_MS = 3_000L
        private const val REQUEST_TIMEOUT_MS = 1_500L
        private const val MAX_REQUEST_ATTEMPTS = 3
        private const val MAX_INDEX_PAGES = 16
        private const val MAX_ROW_PAGES = 64
    }
}
