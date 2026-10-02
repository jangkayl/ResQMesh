package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.network.*
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Serializable
internal data class SosSyncPacket(val request: String, val digest: String = "", val cursor: Int = -1,
    val next: Int = -1, val events: List<SosEvent> = emptyList(), val complete: Boolean = false)

/** Latest signed snapshots include terminal records. Local silence/transmission never affects digests. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, kotlinx.coroutines.FlowPreview::class)
internal class SosSyncCoordinator(
    private val gateway: MeshNetworkGateway, private val peers: MeshReadyPeerEvents,
    private val store: SosStore, private val scope: CoroutineScope,
    private val ingest: suspend (SosEvent, Boolean) -> SosIngestion,
    changes: Flow<Unit>
) {
    private data class Link(val node: String, val endpoint: String, val generation: Long)
    private data class Waiting(val link: Link, val result: CompletableDeferred<SosSyncPacket>)
    private val jobs = ConcurrentHashMap<Link, Job>()
    private val waiting = ConcurrentHashMap<String, Waiting>()
    private val cursors = ConcurrentHashMap<Link, Pair<String, Int>>()
    init {
        scope.launch { peers.peers.collect { refresh() } }
        scope.launch { changes.debounce(500).collect { refresh() } }
        scope.launch { while (isActive) { delay(30_000 + Random.nextLong(-3_000, 3_001)); refresh() } }
    }
    private fun link(p: ConnectedDevice) = Link(p.nodeId, p.endpointId, gateway.linkEstablishedAt(p.endpointId))
    private fun current(l: Link) = gateway.hasReadyEndpoint(l.endpoint) && peers.peers.value.any {
        link(it) == l && !gateway.isDeviceBlocked(it.name)
    }
    @Synchronized private fun refresh() {
        jobs.entries.toList().forEach { (l, j) -> if (!current(l)) { j.cancel(); jobs.remove(l, j); cursors.remove(l) } }
        peers.peers.value.forEach { peer ->
            val l = link(peer)
            if (current(l) && jobs[l]?.isActive != true) {
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    try { reconcile(l) } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { AppLogger.d("SOS_SYNC", "pending peer=${l.node}") }
                }
                jobs[l] = job; job.start()
            }
        }
    }
    private suspend fun snapshot() = store.all().sortedBy { it.sosId }
    private fun digest(rows: List<com.example.testresqmesh.data.local.entity.SosAlertEntity>) =
        SosProtocol.hash(rows.joinToString("\u0000") { it.eventJson })

    private suspend fun reconcile(l: Link) {
        repeat(4) {
            val local = snapshot()
            val root = request(l, SosSyncPacket(UUID.randomUUID().toString(), digest(local)))
            if (root.complete && root.digest == digest(local)) {
                if (digest(snapshot()) == root.digest && current(l)) {
                    val peer = peers.peers.value.firstOrNull { link(it) == l } ?: return
                    local.forEach { row ->
                        if (row.originNodeId == gateway.myNodeId) {
                            store.transmission(row.sosId, row.revision, "CONFIRMED:${peer.name}")
                            val e = SosProtocol.json.decodeFromString<SosEvent>(row.eventJson)
                            store.accepted(e.eventId)
                        }
                    }
                    AppLogger.d("SOS_SYNC", "complete peer=${l.node} snapshot=${root.digest.take(12)}")
                    return
                }
            } else {
                var cursor = cursors[l]?.takeIf { it.first == root.digest }?.second ?: 0
                // A cycle has a bounded page budget; large histories resume on subsequent cycles.
                var pages = 0
                while (cursor >= 0 && pages++ < 8) {
                    val page = request(l, SosSyncPacket(UUID.randomUUID().toString(), root.digest, cursor))
                    if (page.digest != root.digest) break
                    page.events.forEach { ingest(it, false) }
                    if (page.next >= 0 && page.next <= cursor) error("Invalid SOS sync cursor")
                    cursor = page.next
                }
                if (cursor >= 0 && pages > 8) { cursors[l] = root.digest to cursor; return }
                cursors.remove(l)
            }
        }
    }

    private suspend fun request(l: Link, packet: SosSyncPacket): SosSyncPacket {
        val response = CompletableDeferred<SosSyncPacket>()
        waiting[packet.request] = Waiting(l, response)
        try {
            repeat(3) { attempt ->
                check(current(l))
                val payload = MeshPayload(id = UUID.randomUUID().toString(), type = "SOS_SYNC_REQ",
                    text = SosProtocol.json.encodeToString(packet), senderNodeId = gateway.myNodeId)
                gateway.sendPriorityPayload(l.endpoint, ProtoBuf.encodeToByteArray(payload))
                val answer = withTimeoutOrNull(1_500L * (attempt + 1)) { response.await() }
                if (answer != null) { check(current(l)); return answer }
            }
            error("SOS sync timeout")
        } finally { waiting.remove(packet.request) }
    }

    fun receive(endpoint: String, payload: MeshPayload) {
        if (payload.text.toByteArray().size > 16_384) return
        val packet = runCatching { SosProtocol.json.decodeFromString<SosSyncPacket>(payload.text) }.getOrNull() ?: return
        if (packet.request.length !in 1..100 || packet.events.size > 8 || packet.cursor < -1) return
        if (payload.type == "SOS_SYNC_RES" && packet.digest.length != 64) return
        if (payload.type == "SOS_SYNC_RES") {
            val w = waiting[packet.request] ?: return
            if (w.link.endpoint == endpoint && current(w.link)) w.result.complete(packet)
            return
        }
        val l = peers.peers.value.firstOrNull { it.endpointId == endpoint }?.let(::link) ?: return
        scope.launch {
            if (!current(l)) return@launch
            val rows = snapshot()
            val token = digest(rows)
            var reply = SosSyncPacket(packet.request, token, complete = packet.cursor == -1 && token == packet.digest)
            if (packet.cursor >= 0 && token == packet.digest && packet.cursor <= rows.size) {
                val events = mutableListOf<SosEvent>()
                var index = packet.cursor
                while (index < rows.size && events.size < 8) {
                    val next = SosProtocol.json.decodeFromString<SosEvent>(rows[index].eventJson)
                    val trial = reply.copy(events = events + next, next = index + 1)
                    if (SosProtocol.json.encodeToString(trial).toByteArray().size > 12_288) break
                    events.add(next); index++
                }
                reply = reply.copy(events = events, next = if (index >= rows.size) -1 else index)
            }
            val bytes = ProtoBuf.encodeToByteArray(MeshPayload(id = UUID.randomUUID().toString(), type = "SOS_SYNC_RES",
                text = SosProtocol.json.encodeToString(reply), senderNodeId = gateway.myNodeId))
            if (current(l)) {
                if (reply.events.isEmpty()) gateway.sendPriorityPayload(endpoint, bytes)
                else gateway.sendDirectPayload(endpoint, bytes)
            }
        }
    }
}
