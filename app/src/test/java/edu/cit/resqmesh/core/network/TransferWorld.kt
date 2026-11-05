package edu.cit.resqmesh.core.network

import java.io.File
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.rules.TemporaryFolder

internal data class TransferWire(val from: String, val to: String, val payload: MeshPayload, val priority: Boolean)

/** In-memory phones whose transfer engines exchange frames through one queue and a shared clock. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal class TransferWorld(private val temporary: TemporaryFolder) {
    var time = 1_000_000L
    val queue = ArrayDeque<TransferWire>()
    val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    val nodes = mutableMapOf<String, Node>()
    val sentPieces = mutableListOf<Pair<String, Int>>()
    val sent = mutableListOf<TransferWire>()
    var completeFrames: (TransferWire) -> Boolean = { true }
    var drop: (TransferWire) -> Boolean = { false }
    inner class Node(val name: String, val root: File = temporary.newFolder(), var online: Boolean = true) {
        val arrived = mutableListOf<MeshPayload>()
        val events = mutableListOf<OutboundFrameEvent.Stage>()
        val logs = mutableListOf<String>()
        val progress = mutableListOf<SendProgress>()
        var autoStore = true
        var onward: ((ByteArray) -> Unit)? = null
        var neighbors = setOf<String>()
        val blockedPeers = mutableSetOf<String>()
        lateinit var engine: ReliableMeshTransfers
        init {
            nodes[name] = this
            val boot = mutableListOf<() -> Unit>()
            var starting = true
            engine = ReliableMeshTransfers(root, { online }, { nodes[it]?.online == true },
                execute = { if (starting) boot.add(it) else it() },
                schedule = { delay, action -> scheduled.add(time + delay to action) },
                send = { peer, bytes, priority, callback ->
                    val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes).copy(targetNodeId = peer)
                    if (payload.type == ReliableMeshTransfers.PIECE) {
                        val piece = ProtoBuf.decodeFromByteArray<TransferPiece>(payload.liveAudioChunk!!)
                        if (!piece.status) sentPieces.add(name to piece.index)
                    }
                    val wire = TransferWire(name, peer, payload, priority)
                    queue.add(wire); sent.add(wire)
                    if (completeFrames(wire)) {
                        engine.frame(payload, OutboundFrameEvent.Stage.STARTED)
                        engine.frame(payload, OutboundFrameEvent.Stage.COMPLETED)
                    }
                    callback(TransportDispatchResult.ACCEPTED)
                }, received = { _, bytes ->
                    val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes)
                    arrived.add(payload)
                    onward?.invoke(bytes)
                    if (autoStore) engine.stored(payload.targetMessageId.ifBlank { payload.id })
                }, response = { _, _ -> logs.add("RECEIPT_REPLAY") },
                event = { _, stage, _, _ -> events.add(stage) }, now = { time }, log = { logs.add(it) },
                blocked = { it in blockedPeers }, relayPeers = { neighbors },
                progress = { _, _, p -> progress.add(p) })
            starting = false
            boot.forEach { it() }
        }
    }
    fun pump(limit: Int = 10_000) {
        var count = 0
        while (queue.isNotEmpty()) {
            check(++count <= limit) { "Unbounded transfer loop" }
            val wire = queue.removeFirst()
            if (!drop(wire)) nodes[wire.to]?.takeIf { it.online }?.engine?.receive(wire.from, wire.payload)
        }
    }
    fun advance(ms: Long) {
        time += ms
        val due = scheduled.filter { it.first <= time }
        scheduled.removeAll(due.toSet())
        due.forEach { it.second() }
    }
    fun payload(id: String = "note", size: Int = 8192) = ProtoBuf.encodeToByteArray(
        MeshPayload(id = id, type = "CONVERSATION", conversationKind = "RADIO",
            audioBytes = ByteArray(size) { (it % 251).toByte() }))
}

