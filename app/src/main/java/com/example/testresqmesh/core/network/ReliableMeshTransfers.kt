package com.example.testresqmesh.core.network

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

@Serializable
internal data class TransferPiece(
    val transferId: String, val digest: String, val size: Int, val index: Int,
    val bytes: ByteArray = byteArrayOf(), val complete: Boolean = false,
    val priority: Boolean = false, val reset: Boolean = false,
    val status: Boolean = false, val bitmap: ByteArray = byteArrayOf(), val requestId: String = ""
)

/** Hop custody is separate from recipient delivery. All state and disk work run on one IO worker. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal class ReliableMeshTransfers(
    root: File,
    private val active: () -> Boolean,
    private val ready: (String) -> Boolean,
    private val execute: (() -> Unit) -> Unit,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val send: (String, ByteArray, Boolean, (TransportDispatchResult) -> Unit) -> Unit,
    private val received: (String, ByteArray) -> Unit,
    private val response: (String, ByteArray) -> Unit,
    private val event: (ByteArray, OutboundFrameEvent.Stage, Long, Long) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val log: (String) -> Unit = {}
) {
    private lateinit var journal: TransferJournal
    private data class Flight(val record: TransferRecord, val bytes: ByteArray,
        val acknowledged: MutableSet<Int> = mutableSetOf(), val pending: MutableMap<Int, Long> = mutableMapOf(),
        var cursor: Int = 0, var startedAt: Long = 0, var queuedAt: Long = 0,
        val attempts: MutableMap<Int, Int> = mutableMapOf(),
        val pendingWireIds: MutableMap<Int, String> = mutableMapOf(),
        var queried: Boolean = false, var waitingStatus: Boolean = false, var queryUntil: Long = 0,
        var queryNonce: String = "", var queryWireId: String = "")
    private val flights = linkedMapOf<String, Flight>()
    private val lastTurn = mutableMapOf<String, String>()
    private val delivered = linkedMapOf<String, Long>()
    private val reserved = mutableMapOf<String, Int>()
    private val pendingInbound = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val relayRosters = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()
    private val forwardedPeers = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    private val relayFailures = mutableSetOf<Pair<String, String>>()
    private val outgoingMessages = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var timer = false
    @Volatile var busy = false
        private set

    init { execute {
        journal = TransferJournal(root, now)
        if (journal.recoveryFailures > 0) log("TRANSFER_STORAGE_CORRUPT count=${journal.recoveryFailures}")
        journal.snapshot(false).forEach { record ->
            if (record.complete && !record.stored) pendingInbound.add(record.payloadId)
            if (record.relayTargetsSet) relayRosters[record.payloadId] = record.relayTargets.toSet()
            if (record.forwardedPeers.isNotEmpty()) forwardedPeers.getOrPut(record.payloadId) {
                java.util.concurrent.ConcurrentHashMap.newKeySet()
            }.addAll(record.forwardedPeers)
        }
        load(); tick()
    } }

    fun hasPendingCustody(payloadId: String) = payloadId in pendingInbound
    fun hasOutbound(messageId: String) = outgoingMessages.containsValue(messageId)
    fun hasOutboundTo(peer: String) = outgoingMessages.keys.any { it.startsWith("$peer:") }
    fun alreadyForwarded(payloadId: String, peer: String) = forwardedPeers[payloadId]?.contains(peer) == true
    fun relayTargets(payloadId: String, current: Set<String>): Set<String> {
        val roster = relayRosters.getOrPut(payloadId) { current.toSet() }
        execute {
            journal.snapshot(false).filter { it.payloadId == payloadId && it.complete && !it.relayTargetsSet }
                .forEach { record ->
                    var updated = journal.targets(record, roster)
                    // The same logical broadcast can arrive through a different neighbor/path.
                    // Reuse its committed onward ownership instead of stranding the new record.
                    forwardedPeers[payloadId].orEmpty().filter { it in roster }.forEach { peer ->
                        updated = journal.forwarded(updated, peer)
                    }
                    releaseStoredInbound(updated)
                }
        }
        return roster
    }

    @Synchronized fun offer(peer: String, bytes: ByteArray, priority: Boolean, durableRelay: Boolean = false): TransportDispatchResult {
        if (peer.isBlank() || bytes.size !in 1..TransferJournal.MAX_BYTES) return TransportDispatchResult.REJECTED_INVALID_FRAME
        val payload = runCatching { ProtoBuf.decodeFromByteArray<MeshPayload>(bytes) }.getOrNull()
            ?: return TransportDispatchResult.REJECTED_INVALID_FRAME
        val id = TransferJournal.hash(bytes)
        val key = "$peer:$id"
        if (reserved.containsKey(key)) {
            if (durableRelay) execute { runCatching { commitOnwardCustody(peer, payload) }
                .onFailure { log("TRANSFER_STORAGE_FAILED direction=relay-progress") } }
            return TransportDispatchResult.ACCEPTED
        }
        if (!com.example.testresqmesh.core.network.bluetooth.state.OutboundQueuePolicy.canAccept(
                reserved.size, reserved.values.sumOf { it.toLong() }, bytes.size, priority)) {
            return TransportDispatchResult.REJECTED_QUEUE_FULL
        }
        reserved[key] = bytes.size
        outgoingMessages[key] = payload.targetMessageId.ifBlank { payload.id }
        execute {
            var committed = false
            try {
                val record = TransferRecord(peer, id, id, bytes.size, true, payload.id,
                    payload.targetMessageId.ifBlank { payload.id }, priority,
                    now() + if (durableRelay) RETENTION_MS else 90_000L, durableRelay)
                val existing = journal.find(peer, id, true)
                if (!journal.create(record, bytes)) {
                    if (durableRelay) relayFailures.add(peer to payload.id)
                    release(key); event(bytes, OutboundFrameEvent.Stage.FAILED, now(), 0)
                    log("TRANSFER_OVERFLOW relay=$durableRelay"); return@execute
                }
                flights.putIfAbsent(key, Flight(journal.find(peer, id, true)!!, bytes,
                    queuedAt = now(), queried = existing == null))
                committed = true
                relayFailures.remove(peer to payload.id)
                event(bytes, OutboundFrameEvent.Stage.QUEUED, now(), 0)
                // A private relay can release inbound custody only after its onward record commits.
                if (durableRelay) {
                    commitOnwardCustody(peer, payload)
                }
                log("TRANSFER_RETAINED transfer=${id.take(12)} transmission=${payload.id} nextHop=$peer bytes=${bytes.size} relay=$durableRelay")
                tick()
            } catch (_: Exception) {
                if (durableRelay) relayFailures.add(peer to payload.id)
                if (!committed) { release(key); event(bytes, OutboundFrameEvent.Stage.FAILED, now(), 0) }
                log("TRANSFER_STORAGE_FAILED direction=outbound")
                if (committed) tick()
            }
        }
        return TransportDispatchResult.ACCEPTED
    }

    fun wake(peer: String? = null, reconcile: Boolean = true) = execute {
        if (reconcile) flights.values.filter { peer == null || it.record.peer == peer }.forEach {
            it.pending.clear(); it.pendingWireIds.clear(); it.queried = false; it.waitingStatus = false
            it.queryNonce = ""; it.queryWireId = ""
        }
        journal.snapshot(false).filter { it.complete && !it.stored && (peer == null || it.peer == peer) }
            .forEach { deliver(it) }
        tick()
    }

    fun pause() = execute {
        delivered.clear()
        flights.values.forEach {
            it.pending.clear(); it.pendingWireIds.clear(); it.queried = false; it.waitingStatus = false
            it.queryNonce = ""; it.queryWireId = ""
        }
        busy = false
    }

    fun stored(messageId: String, receipt: ByteArray? = null) = execute {
        journal.snapshot(false).filter { it.messageId == messageId && it.complete && (!it.stored || receipt != null) }
            .forEach {
                val saved = journal.applicationStored(it, receipt)
                releaseStoredInbound(saved)
            }
    }

    /** Older peers have no custody ACK. Only their completed local frame releases onward work. */
    fun legacyForwarded(peer: String, payload: MeshPayload) = execute {
        runCatching { commitOnwardCustody(peer, payload) }
            .onFailure { log("TRANSFER_STORAGE_FAILED direction=legacy-progress") }
    }

    private fun releaseStoredInbound(record: TransferRecord) {
        if (record.applicationStored && (!record.requiresRelayRoster || record.relayTargetsSet) &&
            relayFailures.none { it.second == record.payloadId } &&
            (!record.relayTargetsSet || record.relayTargets.all { it in record.forwardedPeers })) {
            journal.stored(record, record.response); pendingInbound.remove(record.payloadId)
        }
    }

    fun receive(peer: String, envelope: MeshPayload) = execute {
        try {
            val packet = ProtoBuf.decodeFromByteArray<TransferPiece>(envelope.liveAudioChunk ?: return@execute)
            if (!valid(packet)) { log("TRANSFER_INVALID"); return@execute }
            if (envelope.type == ACK) {
                val flight = flights["$peer:${packet.transferId}"] ?: return@execute
                if (packet.digest != flight.record.digest || packet.size != flight.record.size) return@execute
                if (packet.reset) {
                    flight.queried = true; flight.waitingStatus = false
                    flight.acknowledged.clear(); flight.pending.clear(); flight.pendingWireIds.clear(); tick(); return@execute
                }
                if (packet.status) {
                    if (!flight.waitingStatus || packet.requestId != flight.queryNonce) return@execute
                    val count = TransferJournal.pieceCount(flight.record.size)
                    if (packet.bitmap.size != (count + 7) / 8) return@execute
                    flight.acknowledged.clear(); flight.pending.clear(); flight.pendingWireIds.clear()
                    (0 until count).filter { packet.bitmap[it / 8].toInt() and (1 shl (it % 8)) != 0 }
                        .forEach { flight.acknowledged.add(it) }
                    flight.waitingStatus = false
                    if (packet.complete) finish(flight, true)
                    tick(); return@execute
                }
                flight.acknowledged.add(packet.index)
                flight.pending.remove(packet.index)
                flight.pendingWireIds.remove(packet.index)
                if (packet.complete) finish(flight, true)
                tick()
                return@execute
            }
            if (envelope.type != PIECE) return@execute
            if (packet.status) {
                val record = journal.find(peer, packet.transferId, false)
                if (record != null && (record.digest != packet.digest || record.size != packet.size)) return@execute
                val count = TransferJournal.pieceCount(packet.size)
                val bitmap = ByteArray((count + 7) / 8)
                if (record != null) (0 until count).filter { record.complete || journal.hasPiece(record, it) }
                    .forEach { bitmap[it / 8] = (bitmap[it / 8].toInt() or (1 shl (it % 8))).toByte() }
                send(peer, envelope(ACK, packet.copy(bitmap = bitmap, complete = record?.complete == true)), true) { }
                record?.response?.let { response(peer, it) }
                if (record?.complete == true && !record.stored) deliver(record)
                return@execute
            }
            if (packet.bytes.size != minOf(TransferJournal.PIECE_BYTES, packet.size - packet.index * TransferJournal.PIECE_BYTES)) return@execute
            var record = journal.find(peer, packet.transferId, false)
            if (record != null && (record.digest != packet.digest || record.size != packet.size)) return@execute
            if (record == null) {
                record = TransferRecord(peer, packet.transferId, packet.digest, packet.size, false, "", "",
                    packet.priority, now() + RETENTION_MS)
                if (!journal.create(record)) { log("TRANSFER_OVERFLOW direction=inbound"); return@execute }
            }
            if (!record.stored) journal.putPiece(record, packet.index, packet.bytes)
            if (!record.complete && (0 until TransferJournal.pieceCount(record.size)).all { journal.hasPiece(record, it) }) {
                val bytes = journal.assemble(record)
                if (TransferJournal.hash(bytes) != record.digest) {
                    journal.delete(record); log("TRANSFER_HASH_FAILED")
                    send(peer, envelope(ACK, packet.copy(bytes = byteArrayOf(), reset = true)), true) { }
                    return@execute
                }
                val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes)
                if (payload.type !in setOf("MESSAGE", "CONVERSATION")) { journal.delete(record); return@execute }
                // Persist application IDs before acknowledging complete custody.
                record = record.copy(payloadId = payload.id, messageId = payload.targetMessageId.ifBlank { payload.id },
                    requiresRelayRoster = !payload.isPrivate &&
                        (payload.type == "MESSAGE" || payload.ttl > 1 && payload.relayHopCount < 10))
                record = journal.complete(record)
                pendingInbound.add(record.payloadId)
                deliver(record)
                log("TRANSFER_REASSEMBLED transfer=${record.id.take(12)} transmission=${record.payloadId} peer=$peer bytes=${record.size}")
            } else if (record.stored) {
                record.response?.let { response(peer, it) }
            }
            val ack = packet.copy(bytes = byteArrayOf(), complete = record.complete)
            send(peer, envelope(ACK, ack), true) { }
            tick()
        } catch (_: Exception) { log("TRANSFER_STORAGE_FAILED direction=inbound") }
    }

    /** Piece retries start after local wire completion, not while queued behind another frame. */
    fun frame(envelope: MeshPayload, stage: OutboundFrameEvent.Stage) = execute {
        if (envelope.type != PIECE) return@execute
        val packet = runCatching { ProtoBuf.decodeFromByteArray<TransferPiece>(envelope.liveAudioChunk ?: return@execute) }.getOrNull() ?: return@execute
        val flight = flights.values.firstOrNull { it.record.id == packet.transferId &&
            it.record.peer == envelope.targetNodeId } ?: return@execute
        if (packet.status) {
            if (!flight.waitingStatus || packet.requestId != flight.queryNonce || envelope.id != flight.queryWireId) return@execute
            if (stage == OutboundFrameEvent.Stage.COMPLETED || stage == OutboundFrameEvent.Stage.FAILED) flight.queryUntil = now() + ACK_WAIT_MS
            return@execute
        }
        if (flight.pendingWireIds[packet.index] != envelope.id) return@execute
        if (stage == OutboundFrameEvent.Stage.STARTED && flight.startedAt == 0L) {
            flight.startedAt = now(); event(flight.bytes, stage, flight.queuedAt, flight.startedAt)
        }
        if (packet.index !in flight.pending) return@execute
        when (stage) {
            OutboundFrameEvent.Stage.COMPLETED -> flight.pending[packet.index] = now() + retryDelay(flight, packet.index)
            OutboundFrameEvent.Stage.FAILED -> flight.pending[packet.index] = now() + retryDelay(flight, packet.index)
            else -> Unit
        }
    }

    private fun load() {
        journal.snapshot(true).forEach { record ->
            runCatching {
                val bytes = journal.payload(record)
                require(bytes.size == record.size && TransferJournal.hash(bytes) == record.digest)
                val key = "${record.peer}:${record.id}"
                synchronized(this) { reserved[key] = bytes.size }
                outgoingMessages[key] = record.messageId
                flights[key] = Flight(record, bytes, queuedAt = now())
                if (record.durableRelay) runCatching {
                    commitOnwardCustody(record.peer, ProtoBuf.decodeFromByteArray<MeshPayload>(bytes))
                }.onFailure { log("TRANSFER_STORAGE_FAILED direction=relay-progress") }
            }.onFailure { journal.delete(record) }
        }
    }
    private fun commitOnwardCustody(peer: String, payload: MeshPayload) {
        relayFailures.remove(peer to payload.id)
        journal.snapshot(false).filter { it.payloadId == payload.id && it.complete && !it.stored }.forEach {
            val updated = journal.forwarded(it, peer)
            forwardedPeers.getOrPut(payload.id) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(peer)
            if (payload.isPrivate) { journal.stored(updated); pendingInbound.remove(it.payloadId) }
            else releaseStoredInbound(updated)
        }
    }
    private fun deliver(record: TransferRecord) {
        val key = "${record.peer}:${record.id}"
        if (active() && ready(record.peer) && (key !in delivered || now() - delivered.getValue(key) >= ACK_WAIT_MS)) {
            delivered[key] = now()
            while (delivered.size > 512) delivered.remove(delivered.keys.first())
            received(record.peer, journal.assemble(record))
        }
    }
    private fun tick() {
        val at = now()
        flights.values.toList().filter { it.record.expiresAt <= at ||
            (!it.record.durableRelay && it.startedAt == 0L && at - it.queuedAt >= 30_000) ||
            (!it.record.durableRelay && it.startedAt > 0 && at - it.startedAt >= 60_000) }
            .forEach { finish(it, false) }
        journal.prune()
        val retainedIds = journal.snapshot(false).map { it.payloadId }.toSet()
        pendingInbound.retainAll(retainedIds)
        relayRosters.keys.retainAll(retainedIds)
        forwardedPeers.keys.retainAll(retainedIds)
        relayFailures.removeAll { it.second !in retainedIds }
        if (!active()) {
            busy = false
            if (flights.isNotEmpty() || journal.snapshot(false).any { !it.stored }) scheduleTick(60_000L)
            return
        }
        journal.snapshot(false).filter { it.complete && !it.stored }.forEach(::deliver)
        val perPeer = mutableMapOf<String, Int>()
        flights.values.forEach { flight ->
            flight.pending.entries.removeAll { it.value != Long.MAX_VALUE && it.value <= at }
            flight.pendingWireIds.keys.retainAll(flight.pending.keys)
            perPeer[flight.record.peer] = (perPeer[flight.record.peer] ?: 0) + flight.pending.size
            if (flight.waitingStatus) perPeer[flight.record.peer] = (perPeer[flight.record.peer] ?: 0) + 1
        }
        lastTurn.keys.retainAll(flights.values.map { it.record.peer }.toSet())
        val turns = flights.values.groupBy { it.record.peer }.flatMap { (peer, entries) ->
            val previous = entries.indexOfFirst { it.record.id == lastTurn[peer] }
            (if (previous < 0) entries else entries.drop(previous + 1) + entries.take(previous + 1))
                .sortedByDescending { it.record.priority }
        }
        // Rotate each peer's next turn before dispatch; an ACK must not always refill the oldest flight.
        turns.forEach { flight ->
            val peer = flight.record.peer
            if (!ready(peer)) return@forEach
            if (!flight.queried || (flight.waitingStatus && flight.queryUntil <= at)) {
                if ((perPeer[peer] ?: 0) >= WINDOW && !flight.waitingStatus) return@forEach
                if (!flight.waitingStatus) {
                    flight.queryNonce = UUID.randomUUID().toString()
                    perPeer[peer] = (perPeer[peer] ?: 0) + 1
                }
                flight.queried = true; flight.waitingStatus = true; flight.queryUntil = at + 30_000L
                val queryNonce = flight.queryNonce
                val query = envelope(PIECE, TransferPiece(flight.record.id, flight.record.digest,
                    flight.record.size, 0, status = true, requestId = queryNonce), peer)
                val queryWireId = ProtoBuf.decodeFromByteArray<MeshPayload>(query).id
                flight.queryWireId = queryWireId
                lastTurn[peer] = flight.record.id
                send(peer, query, true) { result -> execute {
                    if (!result.accepted && flight.waitingStatus && flight.queryWireId == queryWireId) flight.queryUntil = now() + ACK_WAIT_MS
                } }
                return@forEach
            }
            if (flight.waitingStatus || (perPeer[peer] ?: 0) >= WINDOW) return@forEach
            val count = TransferJournal.pieceCount(flight.record.size)
            val index = (0 until count).map { (flight.cursor + it) % count }
                .firstOrNull { it !in flight.acknowledged && it !in flight.pending }
                ?: (count - 1).takeIf { flight.acknowledged.size == count && flight.pending.isEmpty() }
                ?: return@forEach
            flight.cursor = (index + 1) % count
            flight.pending[index] = at + 30_000L
            flight.attempts[index] = (flight.attempts[index] ?: 0) + 1
            perPeer[peer] = (perPeer[peer] ?: 0) + 1
            val data = flight.bytes.copyOfRange(index * TransferJournal.PIECE_BYTES,
                minOf((index + 1) * TransferJournal.PIECE_BYTES, flight.record.size))
            val piece = TransferPiece(flight.record.id, flight.record.digest, flight.record.size, index, data, priority = flight.record.priority)
            val packet = envelope(PIECE, piece, peer)
            val wireId = ProtoBuf.decodeFromByteArray<MeshPayload>(packet).id
            flight.pendingWireIds[index] = wireId
            lastTurn[peer] = flight.record.id
            send(peer, packet, flight.record.priority) { result -> execute {
                if (!result.accepted && flights["$peer:${flight.record.id}"] === flight && flight.pendingWireIds[index] == wireId) {
                    flight.pending[index] = now() + retryDelay(flight, index)
                }
            } }
        }
        busy = flights.values.any { ready(it.record.peer) && (it.pending.isNotEmpty() || it.waitingStatus) }
        if (!timer && (flights.isNotEmpty() || journal.snapshot(false).any { !it.stored })) {
            scheduleTick(if (flights.isNotEmpty()) 100L else 1_000L)
        }
    }
    private fun scheduleTick(delay: Long) {
        if (timer) return
        timer = true
        schedule(delay) { execute { timer = false; tick() } }
    }
    private fun finish(flight: Flight, success: Boolean) {
        val key = "${flight.record.peer}:${flight.record.id}"
        flights.remove(key); release(key); journal.delete(flight.record)
        event(flight.bytes, if (success) OutboundFrameEvent.Stage.COMPLETED else OutboundFrameEvent.Stage.FAILED,
            flight.queuedAt, flight.startedAt)
        log("${if (success) "TRANSFER_CUSTODY_ACK" else "TRANSFER_EXPIRED"} transfer=${flight.record.id.take(12)} transmission=${flight.record.payloadId} peer=${flight.record.peer} bytes=${flight.record.size}")
    }
    @Synchronized private fun release(key: String) { reserved.remove(key); outgoingMessages.remove(key) }
    private fun retryDelay(flight: Flight, index: Int): Long =
        (ACK_WAIT_MS shl ((flight.attempts[index] ?: 1) - 1).coerceIn(0, 3)) +
            ((flight.record.id.hashCode().toLong() + index).and(255L))
    private fun valid(piece: TransferPiece) = piece.size in 1..TransferJournal.MAX_BYTES &&
        piece.index in 0 until TransferJournal.pieceCount(piece.size) &&
        piece.transferId.matches(Regex("[0-9a-f]{64}")) && piece.digest == piece.transferId
    private fun envelope(type: String, piece: TransferPiece, peer: String = "") =
        ProtoBuf.encodeToByteArray(MeshPayload(id = UUID.randomUUID().toString(), type = type,
            targetNodeId = peer, liveAudioChunk = ProtoBuf.encodeToByteArray(piece)))

    companion object {
        const val PIECE = "TRANSFER_PIECE"
        const val ACK = "TRANSFER_ACK"
        const val VERSION = 1
        const val WINDOW = 4
        const val ACK_WAIT_MS = 3_000L
        const val RETENTION_MS = 24 * 60 * 60 * 1000L
    }
}
