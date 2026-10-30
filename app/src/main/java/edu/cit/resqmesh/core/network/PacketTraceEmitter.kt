package edu.cit.resqmesh.core.network

import edu.cit.resqmesh.core.logging.trace.PacketTrace
import edu.cit.resqmesh.core.logging.trace.TraceDir
import edu.cit.resqmesh.core.logging.trace.TraceGate
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.logging.trace.TraceStage
import edu.cit.resqmesh.core.logging.trace.TraceStore
import edu.cit.resqmesh.core.logging.trace.TraceType
import edu.cit.resqmesh.core.logging.trace.shortId

/** Observe-only packet metadata emits. They return at once when the gate is off and never throw. */
internal object PacketTraceEmitter {
    fun emit(payload: MeshPayload?, stage: TraceStage, dir: TraceDir, bytes: Int, transport: String?) {
        if (!TraceGate.enabled || payload == null) return
        try {
            TraceStore.addPacket(
                PacketTrace(
                    seq = TraceStore.nextSeq(),
                    epochMs = System.currentTimeMillis(),
                    stage = stage,
                    dir = dir,
                    scope = if (payload.isPrivate) TraceScope.PRIVATE else TraceScope.PUBLIC,
                    type = TraceType.of(payload.type),
                    packetKey = shortId(payload.targetMessageId.ifBlank { payload.id }).orEmpty(),
                    sourceId = shortId(payload.senderNodeId),
                    destId = shortId(payload.targetNodeId),
                    nextHop = null,
                    hop = null,
                    ttl = null,
                    pathLen = payload.directedRouteNodeIds.size.takeIf { it > 0 },
                    bytes = bytes,
                    transport = transport
                )
            )
        } catch (_: Exception) {
            // Tracing must never affect transport behaviour.
        }
    }

    fun stageOf(stage: OutboundFrameEvent.Stage): TraceStage = when (stage) {
        OutboundFrameEvent.Stage.QUEUED -> TraceStage.QUEUED
        OutboundFrameEvent.Stage.STARTED -> TraceStage.STARTED
        OutboundFrameEvent.Stage.COMPLETED -> TraceStage.COMPLETED
        OutboundFrameEvent.Stage.FAILED -> TraceStage.FAILED
    }
}
