package edu.cit.resqmesh.core.network

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import edu.cit.resqmesh.core.utils.collections.BoundedLruMap

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
    private val flights = linkedMapOf<String, TransferFlight>()
    private val lastTurn = mutableMapOf<String, String>()
    private val delivered = linkedMapOf<String, Long>()
    private val reserved = mutableMapOf<String, Int>()
    private val pendingInbound = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    // Message-id state is LRU-bounded far above the journal (128 records) so live custody is never evicted.
    private val relayRosters = BoundedLruMap<String, Set<String>>(MAX_TRACKED_MESSAGES)
    private val forwardedPeers = BoundedLruMap<String, MutableSet<String>>(MAX_TRACKED_MESSAGES)
    private val relayFailures = mutableSetOf<Pair<String, String>>()
    private val outgoingMessages = BoundedLruMap<String, String>(MAX_TRACKED_MESSAGES)
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
        restoreOutboundFlights(); runTransferCycle()
    } }

    fun hasPendingCustody(payloadId: String) = payloadId in pendingInbound
    fun hasOutbound(messageId: String) = messageId in outgoingMessages.values()
    fun hasOutboundTo(peer: String) = outgoingMessages.keys().any { it.startsWith("$peer:") }
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
        if (!edu.cit.resqmesh.core.network.bluetooth.state.OutboundQueuePolicy.canAccept(
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
                    now() + if (durableRelay) RETENTION_MS else NON_DURABLE_RETENTION_MS, durableRelay)
                val existing = journal.find(peer, id, true)
                if (!journal.create(record, bytes)) {
                    if (durableRelay) relayFailures.add(peer to payload.id)
                    releaseReservation(key); event(bytes, OutboundFrameEvent.Stage.FAILED, now(), 0)
                    log("TRANSFER_OVERFLOW relay=$durableRelay"); return@execute
                }
                flights.putIfAbsent(key, TransferFlight(journal.find(peer, id, true)!!, bytes,
                    queuedAt = now(), queried = existing == null))
                committed = true
                relayFailures.remove(peer to payload.id)
                event(bytes, OutboundFrameEvent.Stage.QUEUED, now(), 0)
                // A private relay can release inbound custody only after its onward record commits.
                if (durableRelay) {
                    commitOnwardCustody(peer, payload)
                }
                log("TRANSFER_RETAINED transfer=${id.take(12)} transmission=${payload.id} nextHop=$peer bytes=${bytes.size} relay=$durableRelay")
                runTransferCycle()
            } catch (_: Exception) {
                if (durableRelay) relayFailures.add(peer to payload.id)
                if (!committed) { releaseReservation(key); event(bytes, OutboundFrameEvent.Stage.FAILED, now(), 0) }
                log("TRANSFER_STORAGE_FAILED direction=outbound")
                if (committed) runTransferCycle()
            }
        }
        return TransportDispatchResult.ACCEPTED
    }

    fun wake(peer: String? = null, reconcile: Boolean = true) = execute {
        if (reconcile) flights.values.filter { peer == null || it.record.peer == peer }.forEach { it.resetProbe() }
        journal.snapshot(false).filter { it.complete && !it.stored && (peer == null || it.peer == peer) }
            .forEach { deliver(it) }
        runTransferCycle()
    }

    fun pause() = execute {
        delivered.clear()
        flights.values.forEach { it.resetProbe() }
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
            if (!isValidPiece(packet)) { log("TRANSFER_INVALID"); return@execute }
            when {
                envelope.type == ACK -> handleAck(peer, packet)
                envelope.type != PIECE -> Unit
                packet.status -> answerStatusQuery(peer, packet)
                else -> storePiece(peer, packet)
            }
        } catch (_: Exception) { log("TRANSFER_STORAGE_FAILED direction=inbound") }
    }

    private fun handleAck(peer: String, packet: TransferPiece) {
        val flight = flights["$peer:${packet.transferId}"] ?: return
        if (packet.digest != flight.record.digest || packet.size != flight.record.size) return
        if (packet.reset) {
            flight.queried = true; flight.waitingStatus = false
            flight.clearProgress(); runTransferCycle()
            return
        }
        if (packet.status) {
            applyStatusBitmap(flight, packet)
            return
        }
        flight.acknowledged.add(packet.index)
        flight.pending.remove(packet.index)
        flight.pendingWireIds.remove(packet.index)
        if (packet.complete) finishFlight(flight, true)
        runTransferCycle()
    }

    private fun applyStatusBitmap(flight: TransferFlight, packet: TransferPiece) {
        if (!flight.waitingStatus || packet.requestId != flight.queryNonce) return
        val count = flight.pieceCount
        if (packet.bitmap.size != bitmapSize(count)) return
        flight.clearProgress()
        (0 until count).filter { packet.bitmap[it / Byte.SIZE_BITS].toInt() and (1 shl (it % Byte.SIZE_BITS)) != 0 }
            .forEach { flight.acknowledged.add(it) }
        flight.waitingStatus = false
        if (packet.complete) finishFlight(flight, true)
        runTransferCycle()
    }

    private fun answerStatusQuery(peer: String, packet: TransferPiece) {
        val record = journal.find(peer, packet.transferId, false)
        if (record != null && (record.digest != packet.digest || record.size != packet.size)) return
        val count = TransferJournal.pieceCount(packet.size)
        val bitmap = ByteArray(bitmapSize(count))
        if (record != null) for (piece in 0 until count) {
            if (!record.complete && !journal.hasPiece(record, piece)) continue
            val slot = piece / Byte.SIZE_BITS
            bitmap[slot] = (bitmap[slot].toInt() or (1 shl (piece % Byte.SIZE_BITS))).toByte()
        }
        send(peer, encodeEnvelope(ACK, packet.copy(bitmap = bitmap, complete = record?.complete == true)), true) { }
        record?.response?.let { response(peer, it) }
        if (record?.complete == true && !record.stored) deliver(record)
    }

    private fun storePiece(peer: String, packet: TransferPiece) {
        if (packet.bytes.size != minOf(TransferJournal.PIECE_BYTES, packet.size - packet.index * TransferJournal.PIECE_BYTES)) return
        var record = journal.find(peer, packet.transferId, false)
        if (record != null && (record.digest != packet.digest || record.size != packet.size)) return
        if (record == null) record = createInboundRecord(peer, packet) ?: return
        if (!record.stored) journal.putPiece(record, packet.index, packet.bytes)
        if (!record.complete && (0 until TransferJournal.pieceCount(record.size)).all { journal.hasPiece(record, it) }) {
            record = reassemble(peer, packet, record) ?: return
        } else if (record.stored) {
            record.response?.let { response(peer, it) }
        }
        val ack = packet.copy(bytes = byteArrayOf(), complete = record.complete)
        send(peer, encodeEnvelope(ACK, ack), true) { }
        runTransferCycle()
    }

    private fun createInboundRecord(peer: String, packet: TransferPiece): TransferRecord? {
        val record = TransferRecord(peer, packet.transferId, packet.digest, packet.size, false, "", "",
            // Priority is peer-supplied: only a frame that fits the control budget may claim it.
            packet.priority && packet.size <= TransferJournal.CONTROL_BYTES, now() + RETENTION_MS)
        if (journal.create(record)) return record
        val quota = if (journal.peerOverQuota(record)) " reason=peer_quota peer=$peer" else ""
        log("TRANSFER_OVERFLOW direction=inbound$quota")
        return null
    }

    /** The completed record, or null when the assembled frame was discarded. */
    private fun reassemble(peer: String, packet: TransferPiece, assembled: TransferRecord): TransferRecord? {
        val bytes = journal.assemble(assembled)
        if (TransferJournal.hash(bytes) != assembled.digest) {
            journal.delete(assembled); log("TRANSFER_HASH_FAILED")
            send(peer, encodeEnvelope(ACK, packet.copy(bytes = byteArrayOf(), reset = true)), true) { }
            return null
        }
        val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes)
        if (payload.type !in setOf("MESSAGE", "CONVERSATION")) { journal.delete(assembled); return null }
        // Persist application IDs before acknowledging complete custody.
        val record = journal.complete(assembled.copy(payloadId = payload.id,
            messageId = payload.targetMessageId.ifBlank { payload.id },
            requiresRelayRoster = !payload.isPrivate &&
                (payload.type == "MESSAGE" || payload.ttl > 1 && payload.relayHopCount < MAX_RELAY_HOPS)))
        pendingInbound.add(record.payloadId)
        deliver(record)
        log("TRANSFER_REASSEMBLED transfer=${record.id.take(12)} transmission=${record.payloadId} peer=$peer bytes=${record.size}")
        return record
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
            OutboundFrameEvent.Stage.COMPLETED -> flight.pending[packet.index] = now() + flight.retryDelay(packet.index)
            OutboundFrameEvent.Stage.FAILED -> flight.pending[packet.index] = now() + flight.retryDelay(packet.index)
            else -> Unit
        }
    }

    private fun restoreOutboundFlights() {
        journal.snapshot(true).forEach { record ->
            runCatching {
                val bytes = journal.payload(record)
                require(bytes.size == record.size && TransferJournal.hash(bytes) == record.digest)
                val key = "${record.peer}:${record.id}"
                synchronized(this) { reserved[key] = bytes.size }
                outgoingMessages[key] = record.messageId
                flights[key] = TransferFlight(record, bytes, queuedAt = now())
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
            while (delivered.size > MAX_DELIVERY_ATTEMPT_MARKS) delivered.remove(delivered.keys.first())
            received(record.peer, journal.assemble(record))
        }
    }
    private fun runTransferCycle() {
        val at = now()
        flights.values.toList().filter { it.isExpired(at, QUEUED_STALL_MS, STARTED_STALL_MS) }
            .forEach { finishFlight(it, false) }
        pruneRetainedState()
        if (!active()) {
            busy = false
            if (flights.isNotEmpty() || journal.snapshot(false).any { !it.stored }) scheduleTick(TICK_IDLE_MS)
            return
        }
        journal.snapshot(false).filter { it.complete && !it.stored }.forEach(::deliver)
        val perPeer = countInFlightByPeer(at)
        lastTurn.keys.retainAll(flights.values.map { it.record.peer }.toSet())
        // Rotate each peer's next turn before dispatch; an ACK must not always refill the oldest flight.
        rotatedTurns().forEach { dispatchTurn(it, perPeer, at) }
        busy = flights.values.any { ready(it.record.peer) && (it.pending.isNotEmpty() || it.waitingStatus) }
        if (!timer && (flights.isNotEmpty() || journal.snapshot(false).any { !it.stored })) {
            scheduleTick(if (flights.isNotEmpty()) TICK_ACTIVE_MS else TICK_RETAINED_MS)
        }
    }

    private fun pruneRetainedState() {
        journal.prune()
        val retainedIds = journal.snapshot(false).map { it.payloadId }.toSet()
        pendingInbound.retainAll(retainedIds)
        relayRosters.retainKeys { it in retainedIds }
        forwardedPeers.retainKeys { it in retainedIds }
        relayFailures.removeAll { it.second !in retainedIds }
    }

    private fun countInFlightByPeer(at: Long): MutableMap<String, Int> {
        val perPeer = mutableMapOf<String, Int>()
        flights.values.forEach { flight ->
            flight.pending.entries.removeAll { it.value != Long.MAX_VALUE && it.value <= at }
            flight.pendingWireIds.keys.retainAll(flight.pending.keys)
            perPeer[flight.record.peer] = (perPeer[flight.record.peer] ?: 0) + flight.pending.size
            if (flight.waitingStatus) perPeer[flight.record.peer] = (perPeer[flight.record.peer] ?: 0) + 1
        }
        return perPeer
    }

    private fun rotatedTurns(): List<TransferFlight> = flights.values.groupBy { it.record.peer }
        .flatMap { (peer, entries) ->
            val previous = entries.indexOfFirst { it.record.id == lastTurn[peer] }
            (if (previous < 0) entries else entries.drop(previous + 1) + entries.take(previous + 1))
                .sortedByDescending { it.record.priority }
        }

    private fun dispatchTurn(flight: TransferFlight, perPeer: MutableMap<String, Int>, at: Long) {
        val peer = flight.record.peer
        if (!ready(peer)) return
        if (!flight.queried || (flight.waitingStatus && flight.queryUntil <= at)) {
            sendStatusQuery(flight, perPeer, at)
            return
        }
        if (flight.waitingStatus || (perPeer[peer] ?: 0) >= WINDOW) return
        sendNextPiece(flight, perPeer, at)
    }

    private fun sendStatusQuery(flight: TransferFlight, perPeer: MutableMap<String, Int>, at: Long) {
        val peer = flight.record.peer
        if ((perPeer[peer] ?: 0) >= WINDOW && !flight.waitingStatus) return
        if (!flight.waitingStatus) {
            flight.queryNonce = UUID.randomUUID().toString()
            perPeer[peer] = (perPeer[peer] ?: 0) + 1
        }
        flight.queried = true; flight.waitingStatus = true; flight.queryUntil = at + STATUS_QUERY_MS
        val queryNonce = flight.queryNonce
        val query = encodeEnvelope(PIECE, TransferPiece(flight.record.id, flight.record.digest,
            flight.record.size, 0, status = true, requestId = queryNonce), peer)
        val queryWireId = ProtoBuf.decodeFromByteArray<MeshPayload>(query).id
        flight.queryWireId = queryWireId
        lastTurn[peer] = flight.record.id
        send(peer, query, true) { result -> execute {
            if (!result.accepted && flight.waitingStatus && flight.queryWireId == queryWireId) flight.queryUntil = now() + ACK_WAIT_MS
        } }
    }

    private fun sendNextPiece(flight: TransferFlight, perPeer: MutableMap<String, Int>, at: Long) {
        val peer = flight.record.peer
        val index = flight.nextPieceIndex() ?: return
        flight.cursor = (index + 1) % flight.pieceCount
        flight.pending[index] = at + PIECE_RETRY_MS
        flight.attempts[index] = (flight.attempts[index] ?: 0) + 1
        perPeer[peer] = (perPeer[peer] ?: 0) + 1
        val piece = TransferPiece(flight.record.id, flight.record.digest, flight.record.size, index,
            flight.pieceBytes(index), priority = flight.record.priority)
        val packet = encodeEnvelope(PIECE, piece, peer)
        val wireId = ProtoBuf.decodeFromByteArray<MeshPayload>(packet).id
        flight.pendingWireIds[index] = wireId
        lastTurn[peer] = flight.record.id
        send(peer, packet, flight.record.priority) { result -> execute {
            if (!result.accepted && flights["$peer:${flight.record.id}"] === flight && flight.pendingWireIds[index] == wireId) {
                flight.pending[index] = now() + flight.retryDelay(index)
            }
        } }
    }

    private fun scheduleTick(delay: Long) {
        if (timer) return
        timer = true
        schedule(delay) { execute { timer = false; runTransferCycle() } }
    }
    private fun finishFlight(flight: TransferFlight, success: Boolean) {
        val key = flight.key
        flights.remove(key); releaseReservation(key); journal.delete(flight.record)
        event(flight.bytes, if (success) OutboundFrameEvent.Stage.COMPLETED else OutboundFrameEvent.Stage.FAILED,
            flight.queuedAt, flight.startedAt)
        log("${if (success) "TRANSFER_CUSTODY_ACK" else "TRANSFER_EXPIRED"} transfer=${flight.record.id.take(12)} transmission=${flight.record.payloadId} peer=${flight.record.peer} bytes=${flight.record.size}")
    }
    @Synchronized private fun releaseReservation(key: String) { reserved.remove(key); outgoingMessages.remove(key) }
    private fun isValidPiece(piece: TransferPiece) = piece.size in 1..TransferJournal.MAX_BYTES &&
        piece.index in 0 until TransferJournal.pieceCount(piece.size) &&
        piece.transferId.matches(TRANSFER_ID_FORMAT) && piece.digest == piece.transferId
    private fun bitmapSize(pieces: Int) = (pieces + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS
    private fun encodeEnvelope(type: String, piece: TransferPiece, peer: String = "") =
        ProtoBuf.encodeToByteArray(MeshPayload(id = UUID.randomUUID().toString(), type = type,
            targetNodeId = peer, liveAudioChunk = ProtoBuf.encodeToByteArray(piece)))

    companion object {
        const val PIECE = "TRANSFER_PIECE"
        const val ACK = "TRANSFER_ACK"
        const val VERSION = 1
        private const val MAX_TRACKED_MESSAGES = 1024
        const val WINDOW = 4
        const val ACK_WAIT_MS = 3_000L
        const val RETENTION_MS = 24 * 60 * 60 * 1000L
        private const val NON_DURABLE_RETENTION_MS = 90_000L
        private const val QUEUED_STALL_MS = 30_000L
        private const val STARTED_STALL_MS = 60_000L
        private const val TICK_IDLE_MS = 60_000L
        private const val STATUS_QUERY_MS = 30_000L
        private const val PIECE_RETRY_MS = 30_000L
        private const val TICK_ACTIVE_MS = 100L
        private const val TICK_RETAINED_MS = 1_000L
        private const val MAX_RELAY_HOPS = 10
        private const val MAX_DELIVERY_ATTEMPT_MARKS = 512
        private val TRANSFER_ID_FORMAT = Regex("[0-9a-f]{64}")
    }
}
