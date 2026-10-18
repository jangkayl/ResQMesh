package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.TerminalLogCategory
import edu.cit.resqmesh.core.logging.TerminalLogLevel
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.dedup.ExpiringIdCache

/**
 * Applies [OriginRateLimiter] to decoded payloads, keyed by the payload's ORIGIN node (not the relay
 * that forwarded it). Re-deliveries of an already admitted payload id are not charged again, so
 * flooded copies of one SOS or a retried chat message cannot push a legitimate origin into cooldown.
 */
class InboundOriginGate(
    private val limiter: OriginRateLimiter,
    clock: () -> Long = System::currentTimeMillis,
    logIntervalMs: Long = LOG_INTERVAL_MS
) {
    private val admittedIds = ExpiringIdCache(clock = clock)
    private val knownSosIds = ExpiringIdCache(clock = clock)
    private val dropLog = RateLimitedLog(logIntervalMs, clock)

    /** True when [payload] may continue to its handler. */
    fun admit(endpointId: String, payload: MeshPayload, myNodeId: String): Boolean {
        val origin = originOf(payload, endpointId)
        val exempt = myNodeId.isNotBlank() && origin.equals(myNodeId, ignoreCase = true) ||
            payload.id.isNotEmpty() && admittedIds.contains(payload.id)
        if (exempt) return true
        val trafficClass = classify(payload)
        val decision = limiter.check(origin, trafficClass)
        val allowed = decision == OriginRateLimiter.Decision.Allowed
        if (allowed) recordAdmitted(payload) else logDrop(endpointId, payload, origin, trafficClass, decision)
        return allowed
    }

    private fun recordAdmitted(payload: MeshPayload) {
        if (payload.id.isNotEmpty()) admittedIds.markSeen(payload.id)
        if (payload.type == SOS_EVENT && payload.sosId.isNotEmpty()) knownSosIds.markSeen(payload.sosId)
    }

    private fun logDrop(
        endpointId: String,
        payload: MeshPayload,
        origin: String,
        trafficClass: TrafficClass,
        decision: OriginRateLimiter.Decision
    ) {
        val short = origin.take(SHORT_ID)
        if (decision == OriginRateLimiter.Decision.CooldownStarted) {
            log("RL_COOLDOWN", payload, trafficClass, short, endpointId)
        } else if (dropLog.shouldLog("$origin/$trafficClass")) {
            log("RL_DROP", payload, trafficClass, short, endpointId)
        }
    }

    private fun log(
        marker: String,
        payload: MeshPayload,
        trafficClass: TrafficClass,
        origin: String,
        endpointId: String
    ) =
        AppLogger.event(
            category = TerminalLogCategory.SECURITY,
            event = marker,
            message = "$marker type=${payload.type.take(MAX_TYPE_LOG)} class=$trafficClass origin=$origin",
            level = TerminalLogLevel.WARN,
            tag = "PAYLOAD_DISPATCHER",
            endpoint = endpointId
        )

    private fun originOf(payload: MeshPayload, endpointId: String): String =
        payload.senderNodeId.ifBlank { NodeIdentity.idOf(payload.senderName).orEmpty() }
            .uppercase().ifBlank { "EP:$endpointId" }

    private fun classify(payload: MeshPayload): TrafficClass = when (payload.type) {
        "MESSAGE", "CONVERSATION" -> TrafficClass.CHAT
        SOS_EVENT ->
            if (payload.sosId.isEmpty() || !knownSosIds.contains(payload.sosId)) TrafficClass.SOS_NEW
            else TrafficClass.SOS_UPDATE
        "SOS_SYNC_REQ", "EVENT_SYNC_REQ" -> TrafficClass.SYNC_REQUEST
        // PING/PONG are per-link liveness probes (link shaping only), not origin presence.
        "SYSTEM" -> TrafficClass.PRESENCE
        else -> TrafficClass.OTHER
    }

    private companion object {
        const val SOS_EVENT = "SOS_EVENT"
        const val SHORT_ID = 8
        const val MAX_TYPE_LOG = 32
        const val LOG_INTERVAL_MS = 5_000L
    }
}
