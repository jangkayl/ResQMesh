package edu.cit.resqmesh.core.network

import edu.cit.resqmesh.core.logging.trace.PacketTrace
import edu.cit.resqmesh.core.logging.trace.TraceDir
import edu.cit.resqmesh.core.logging.trace.TraceDropReason
import edu.cit.resqmesh.core.logging.trace.TraceGate
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.logging.trace.TraceStage
import edu.cit.resqmesh.core.logging.trace.TraceStore
import edu.cit.resqmesh.core.logging.trace.TraceType
import edu.cit.resqmesh.core.logging.trace.shortId
import edu.cit.resqmesh.core.utils.collections.BoundedLruMap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

/** What this phone can honestly say about a payload's content. */
internal enum class VoiceKind { VOICE, UNKNOWN, NOT_VOICE }

internal object VoiceClassifier {
    /**
     * Public audio is visible to everyone. Private audio is encrypted, so a relay cannot tell: only the sender
     * and the recipient tag it as voice, through the local-knowledge emits instead of this function.
     */
    fun classify(p: MeshPayload?): VoiceKind = when {
        p == null -> VoiceKind.UNKNOWN
        p.isPrivate || p.isEncrypted -> VoiceKind.UNKNOWN
        (p.audioBytes?.isNotEmpty() == true) -> VoiceKind.VOICE
        else -> VoiceKind.NOT_VOICE
    }
}

internal object TransferProgress {
    /** Emit at about every tenth of a transfer, plus the start and the end, so collapsed rows stay short. */
    fun shouldEmit(done: Int, pieces: Int): Boolean {
        if (pieces <= 0) return false
        val step = maxOf(1, (pieces + PROGRESS_STEPS - 1) / PROGRESS_STEPS)
        return done == 0 || done >= pieces || done % step == 0
    }

    private const val PROGRESS_STEPS = 10
}

/** One transfer observation: ids and counts only. [payload] is read to classify, never stored. */
@Suppress("LongParameterList")
internal data class TransferTrace(
    val peer: String,
    val transferId: String,
    val dir: TraceDir,
    val stage: TraceStage,
    val size: Int,
    val done: Int,
    val pieces: Int,
    val reason: TraceDropReason = TraceDropReason.NONE,
    val messageId: String = "",
    val payload: MeshPayload? = null
)

/** Observe-only transfer and voice emits. They return at once when the gate is off and never throw. */
@OptIn(ExperimentalSerializationApi::class)
internal object TransferTraceEmitter {
    private class Meta(val kind: VoiceKind, val scope: TraceScope, val dst: String?)

    // Trace-only state. Cleared when tracing is turned off; the mesh never reads it.
    private val metas = BoundedLruMap<String, Meta>(MAX_TRACKED)
    private val held = BoundedLruMap<String, java.util.BitSet>(MAX_TRACKED)
    private val lastDone = BoundedLruMap<String, Int>(MAX_TRACKED)
    private val localVoice = BoundedLruMap<String, Boolean>(MAX_TRACKED)

    init {
        TraceGate.onDisabled { metas.clear(); held.clear(); lastDone.clear(); localVoice.clear() }
    }

    /** Count of pieces held after [index] arrives, or -1 when it was already held (a replay records nothing). */
    @Suppress("ReturnCount") // flat early exits
    fun inboundHeld(peer: String, transferId: String, index: Int, pieces: Int): Int {
        if (!TraceGate.enabled) return -1
        val bits = held.getOrPut("$peer:$transferId") { java.util.BitSet(pieces) }
        if (bits.get(index)) return -1
        bits.set(index)
        return bits.cardinality()
    }

    @Suppress("ReturnCount") // flat early exits
    fun transfer(t: TransferTrace) {
        if (!TraceGate.enabled) return
        try {
            val failed = t.reason != TraceDropReason.NONE
            if (failed && !PacketTraceEmitter.limiter.allow("${t.peer}/XFER/${t.reason}")) return
            if (t.stage == TraceStage.STARTED && !progressGrew(t)) return
            val meta = metaOf(t)
            val packet = build(t, meta)
            TraceStore.addPacket(packet)
            when (meta.kind) {
                VoiceKind.NOT_VOICE -> Unit
                VoiceKind.VOICE -> addVoiceCopy(packet, TraceType.VOICE_NOTE)
                VoiceKind.UNKNOWN -> addVoiceCopy(packet, TraceType.MEDIA_TRANSFER)
            }
        } catch (_: Exception) {
            // Tracing must never affect transfer behaviour.
        }
    }

    /** Outbound ACK replays are already filtered by the caller; this also drops any non-growing progress event. */
    private fun progressGrew(t: TransferTrace): Boolean {
        val key = "${t.transferId}/${t.dir}"
        val previous = lastDone[key]
        if (previous != null && t.done <= previous) return false
        lastDone[key] = t.done
        return true
    }

    private fun addVoiceCopy(p: PacketTrace, type: TraceType) =
        TraceStore.addVoice(p.copy(seq = TraceStore.nextSeq(), type = type))

    /**
     * Never decodes transfer bytes: a payload is read only when the caller already holds it (offer, reassembly).
     * Without one the content is UNKNOWN, except a message id this phone knows to be its own or received voice note.
     */
    @Suppress("ReturnCount") // flat early exits
    private fun metaOf(t: TransferTrace): Meta {
        val known = metas[t.transferId]
        val localVoiceNote = t.messageId.isNotBlank() && localVoice[t.messageId] == true
        if (known != null && t.payload == null) {
            if (known.kind != VoiceKind.UNKNOWN || !localVoiceNote) return known
            return Meta(VoiceKind.VOICE, known.scope, known.dst).also { metas[t.transferId] = it }
        }
        val p = t.payload
        var kind = VoiceClassifier.classify(p)
        if (kind == VoiceKind.UNKNOWN && localVoiceNote) kind = VoiceKind.VOICE
        val meta = Meta(
            kind,
            when { p == null -> TraceScope.UNKNOWN; p.isPrivate -> TraceScope.PRIVATE; else -> TraceScope.PUBLIC },
            p?.targetNodeId?.let(::shortId)
        )
        metas[t.transferId] = meta
        return meta
    }

    /** Pieces and acknowledgements carry no sender, so [PacketTrace.sourceId] stays null. */
    private fun build(t: TransferTrace, meta: Meta) = PacketTrace(
        seq = TraceStore.nextSeq(),
        epochMs = System.currentTimeMillis(),
        stage = t.stage,
        dir = t.dir,
        scope = meta.scope,
        type = TraceType.PIECE,
        packetKey = shortId(t.messageId.ifBlank { t.transferId }).orEmpty(),
        sourceId = null,
        destId = meta.dst,
        nextHop = shortId(t.peer),
        hop = null,
        ttl = null,
        pathLen = null,
        bytes = t.size,
        transport = "TRANSFER",
        dropReason = t.reason,
        progress = t.done.takeIf { t.pieces > 0 },
        total = t.pieces.takeIf { it > 0 }
    )

    /** Voice event known locally (record/playback error, media reject, or a voice note this phone sends or opens). */
    @Suppress("LongParameterList")
    fun voice(
        dir: TraceDir, stage: TraceStage, scope: TraceScope, messageId: String, reason: TraceDropReason, rateKey: String
    ) {
        if (!TraceGate.enabled) return
        try {
            if (reason != TraceDropReason.NONE && !PacketTraceEmitter.limiter.allow("$rateKey/VOICE/$reason")) return
            if (reason == TraceDropReason.NONE && messageId.isNotBlank()) localVoice[messageId] = true
            TraceStore.addVoice(
                PacketTrace(
                    TraceStore.nextSeq(), System.currentTimeMillis(), stage, dir, scope, TraceType.VOICE_NOTE,
                    shortId(messageId).orEmpty(), null, null, null, null, null, null, 0, null, reason
                )
            )
        } catch (_: Exception) {
            // Tracing must never affect audio behaviour.
        }
    }

    private const val MAX_TRACKED = 64
}
