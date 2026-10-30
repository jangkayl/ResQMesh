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
import edu.cit.resqmesh.core.network.ratelimit.RateLimitedLog
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

/** Lets one drop event per key through each interval, so a hostile peer cannot flood the trace rings. */
internal class TraceDropLimiter(intervalMs: Long = DROP_INTERVAL_MS, clock: () -> Long = System::currentTimeMillis) {
    private val log = RateLimitedLog(intervalMs, clock)
    fun allow(key: String): Boolean = log.shouldLog(key)

    companion object {
        const val DROP_INTERVAL_MS = 500L
    }
}

/**
 * Observe-only packet metadata emits. They return at once when the gate is off and never throw.
 * Reasons and decodes are computed only on drop or failure paths, after the gate and rate limit.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object PacketTraceEmitter {
    @Volatile
    internal var limiter = TraceDropLimiter()

    @Suppress("LongParameterList")
    fun emit(
        payload: MeshPayload?, stage: TraceStage, dir: TraceDir, bytes: Int, transport: String?,
        nextHop: String? = null, selfId: String? = null, dropReason: TraceDropReason = TraceDropReason.NONE
    ) {
        if (!TraceGate.enabled || payload == null) return
        try {
            TraceStore.addPacket(build(payload, stage, dir, bytes, transport, nextHop, selfId, dropReason))
        } catch (_: Exception) {
            // Tracing must never affect transport behaviour.
        }
    }

    /** Inbound drop of a decoded payload. One event per endpoint and reason per interval. */
    fun drop(
        payload: MeshPayload?, reason: TraceDropReason, endpoint: String, bytes: Int, transport: String?
    ) {
        if (!TraceGate.enabled) return
        try {
            if (!limiter.allow("$endpoint/IN/$reason")) return
            if (payload == null) {
                TraceStore.addPacket(
                    PacketTrace(
                        TraceStore.nextSeq(), System.currentTimeMillis(), TraceStage.DROPPED, TraceDir.IN,
                        TraceScope.UNKNOWN,
                        TraceType.OTHER, "", null, null, null, null, null, null, bytes, transport, reason
                    )
                )
            } else {
                TraceStore.addPacket(
                    build(payload, TraceStage.DROPPED, TraceDir.IN, bytes, transport, null, null, reason)
                )
            }
        } catch (_: Exception) {
            // Tracing must never affect transport behaviour.
        }
    }

    /** Outbound rejection from the raw send path. The tracing-only decode runs after the gate and rate limit. */
    fun sendRejected(bytes: ByteArray, endpoint: String, nextHop: String?, result: TransportDispatchResult) {
        if (!TraceGate.enabled) return
        try {
            val reason = dropReasonOf(result)
            if (reason == TraceDropReason.NONE || !limiter.allow("$endpoint/OUT/$reason")) return
            val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes)
            TraceStore.addPacket(
                build(payload, TraceStage.FAILED, TraceDir.OUT, bytes.size, null, nextHop, null, reason)
            )
        } catch (_: Exception) {
            // Undecodable frames are simply not traced.
        }
    }

    /** Per-neighbor rejections of a broadcast; [payload] is already decoded by the caller. */
    fun broadcastRejected(payload: MeshPayload?, bytes: Int, results: Map<String, TransportDispatchResult>) {
        if (!TraceGate.enabled || payload == null) return
        try {
            results.forEach { (peer, result) ->
                val reason = dropReasonOf(result)
                if (reason == TraceDropReason.NONE || !limiter.allow("$peer/OUT/$reason")) return@forEach
                TraceStore.addPacket(build(payload, TraceStage.FAILED, TraceDir.OUT, bytes, null, peer, null, reason))
            }
        } catch (_: Exception) {
            // Tracing must never affect transport behaviour.
        }
    }

    /** Local delivery outcome (retries exhausted, expired unsent) for a message id; no payload is available. */
    fun localOutcome(messageId: String, scope: TraceScope, type: TraceType, reason: TraceDropReason) {
        if (!TraceGate.enabled) return
        try {
            TraceStore.addPacket(
                PacketTrace(
                    TraceStore.nextSeq(), System.currentTimeMillis(), TraceStage.FAILED, TraceDir.OUT, scope, type,
                    shortId(messageId).orEmpty(), null, null, null, null, null, null, 0, null, reason
                )
            )
        } catch (_: Exception) {
            // Tracing must never affect delivery behaviour.
        }
    }

    @Suppress("LongParameterList")
    private fun build(
        payload: MeshPayload, stage: TraceStage, dir: TraceDir, bytes: Int, transport: String?,
        nextHop: String?, selfId: String?, reason: TraceDropReason
    ): PacketTrace {
        val type = TraceType.of(payload.type)
        return PacketTrace(
            seq = TraceStore.nextSeq(),
            epochMs = System.currentTimeMillis(),
            stage = stage,
            dir = dir,
            scope = if (payload.isPrivate) TraceScope.PRIVATE else TraceScope.PUBLIC,
            type = type,
            packetKey = shortId(payload.targetMessageId.ifBlank { payload.id }).orEmpty(),
            sourceId = if (hasNoSender(type)) null else shortId(payload.senderNodeId),
            destId = shortId(payload.targetNodeId),
            nextHop = shortId(nextHop),
            hop = payload.relayHopCount,
            ttl = payload.ttl,
            pathLen = payload.directedRouteNodeIds.size.takeIf { it > 0 },
            bytes = bytes,
            transport = transport,
            dropReason = reason,
            routeStep = routeStepOf(payload.directedRouteNodeIds, if (dir == TraceDir.IN) selfId else nextHop)
        )
    }

    private fun hasNoSender(type: TraceType) =
        type == TraceType.SEEN || type == TraceType.DELIVERED || type == TraceType.PIECE || type == TraceType.ACK

    fun stageOf(stage: OutboundFrameEvent.Stage): TraceStage = when (stage) {
        OutboundFrameEvent.Stage.QUEUED -> TraceStage.QUEUED
        OutboundFrameEvent.Stage.STARTED -> TraceStage.STARTED
        OutboundFrameEvent.Stage.COMPLETED -> TraceStage.COMPLETED
        OutboundFrameEvent.Stage.FAILED -> TraceStage.FAILED
    }

    fun dropReasonOf(result: TransportDispatchResult): TraceDropReason = when (result) {
        TransportDispatchResult.ACCEPTED -> TraceDropReason.NONE
        TransportDispatchResult.REJECTED_INVALID_ENDPOINT -> TraceDropReason.SEND_INVALID_ENDPOINT
        TransportDispatchResult.REJECTED_NOT_READY -> TraceDropReason.SEND_NOT_READY
        TransportDispatchResult.REJECTED_QUEUE_FULL -> TraceDropReason.SEND_QUEUE_FULL
        TransportDispatchResult.REJECTED_INVALID_FRAME -> TraceDropReason.SEND_INVALID_FRAME
    }

    /** Position of [nodeId] in a private directed route (0 is the origin), or null when absent or unknown. */
    fun routeStepOf(route: List<String>, nodeId: String?): Int? {
        if (route.isEmpty() || nodeId.isNullOrBlank()) return null
        return route.indexOfFirst { it.equals(nodeId, ignoreCase = true) }.takeIf { it >= 0 }
    }
}
