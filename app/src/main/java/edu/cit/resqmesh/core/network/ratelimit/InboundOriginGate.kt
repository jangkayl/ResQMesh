package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.TerminalLogCategory
import edu.cit.resqmesh.core.logging.TerminalLogLevel
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.dedup.ExpiringIdCache

/**
 * Applies [OriginRateLimiter] to decoded payloads, keyed by the payload's claimed ORIGIN node (not
 * the relay that forwarded it). Origin claims are unauthenticated, so a payload received directly
 * from its origin must match the link's admitted node id, and everything else is keyed by
 * (origin, ingress endpoint): a forger on one link only spends buckets and penalties of that link.
 * Re-deliveries of an already admitted payload id are not charged again.
 *
 * SOS_EVENT is never charged here; [admitVerifiedSos] charges it after the signature verified.
 */
class InboundOriginGate(
    private val limiter: OriginRateLimiter,
    clock: () -> Long = System::currentTimeMillis,
    logIntervalMs: Long = LOG_INTERVAL_MS
) {
    private val admittedIds = ExpiringIdCache(clock = clock)
    private val dropLog = RateLimitedLog(logIntervalMs, clock)

    /** True when [payload] may continue to its handler. [admittedNodeId] is the link's admitted peer id, if known. */
    fun admit(endpointId: String, payload: MeshPayload, myNodeId: String, admittedNodeId: String? = null): Boolean {
        val origin = originOf(payload, endpointId)
        val exempt = myNodeId.isNotBlank() && origin.equals(myNodeId, ignoreCase = true) ||
            payload.id.isNotEmpty() && admittedIds.contains(payload.id)
        if (exempt) return true
        val trafficClass = classify(payload)
        if (trafficClass == TrafficClass.OTHER) return true
        val direct = isDirect(payload)
        if (direct && !admittedNodeId.isNullOrBlank() && !admittedNodeId.equals(origin, ignoreCase = true)) {
            logMismatch(endpointId, payload)
            return false
        }
        // A direct payload from the link's admitted peer is authenticated by the link; anything else
        // is only a claim and is tracked per ingress endpoint.
        val verified = direct && !admittedNodeId.isNullOrBlank()
        val key = if (verified) origin else "$origin@$endpointId"
        val decision = limiter.check(key, trafficClass)
        val allowed = decision == OriginRateLimiter.Decision.Allowed
        if (allowed) {
            if (payload.id.isNotEmpty()) admittedIds.markSeen(payload.id)
        } else {
            logDrop(endpointId, payload, origin, trafficClass, decision)
        }
        return allowed
    }

    /**
     * Charges the SOS bucket of a signature-verified event. [signingKeyPrefix] is derived from the
     * signing key, so it cannot be claimed by anyone who does not hold the key.
     */
    fun admitVerifiedSos(endpointId: String, signingKeyPrefix: String, isNewAlert: Boolean): Boolean {
        val trafficClass = if (isNewAlert) TrafficClass.SOS_NEW else TrafficClass.SOS_UPDATE
        val decision = limiter.check(signingKeyPrefix, trafficClass)
        if (decision == OriginRateLimiter.Decision.Allowed) return true
        val short = signingKeyPrefix.take(SHORT_ID)
        if (decision == OriginRateLimiter.Decision.CooldownStarted) {
            log("RL_COOLDOWN", SOS_EVENT, trafficClass, short, endpointId)
        } else if (dropLog.shouldLog("$signingKeyPrefix/$trafficClass")) {
            log("RL_DROP", SOS_EVENT, trafficClass, short, endpointId)
        }
        return false
    }

    /** Hop count 0 and no relay appended to the route (only the origin itself): received from the origin. */
    private fun isDirect(payload: MeshPayload) = payload.relayHopCount == 0 &&
        (payload.routePath.isEmpty() || payload.routePath.size == 1 && NodeIdentity.matches(payload.routePath[0], payload.senderName))

    private fun logMismatch(endpointId: String, payload: MeshPayload) =
        AppLogger.event(
            category = TerminalLogCategory.SECURITY,
            event = "INBOUND_REJECT",
            message = "INBOUND_REJECT type=${payload.type.take(MAX_TYPE_LOG)} reason=origin_mismatch",
            level = TerminalLogLevel.WARN,
            tag = "PAYLOAD_DISPATCHER",
            endpoint = endpointId
        )

    private fun logDrop(
        endpointId: String,
        payload: MeshPayload,
        origin: String,
        trafficClass: TrafficClass,
        decision: OriginRateLimiter.Decision
    ) {
        val short = origin.take(SHORT_ID)
        if (decision == OriginRateLimiter.Decision.CooldownStarted) {
            log("RL_COOLDOWN", payload.type, trafficClass, short, endpointId)
        } else if (dropLog.shouldLog("$origin@$endpointId/$trafficClass")) {
            log("RL_DROP", payload.type, trafficClass, short, endpointId)
        }
    }

    private fun log(marker: String, type: String, trafficClass: TrafficClass, origin: String, endpointId: String) =
        AppLogger.event(
            category = TerminalLogCategory.SECURITY,
            event = marker,
            message = "$marker type=${type.take(MAX_TYPE_LOG)} class=$trafficClass origin=$origin",
            level = TerminalLogLevel.WARN,
            tag = "PAYLOAD_DISPATCHER",
            endpoint = endpointId
        )

    private fun originOf(payload: MeshPayload, endpointId: String): String =
        payload.senderNodeId.ifBlank { NodeIdentity.idOf(payload.senderName).orEmpty() }
            .uppercase().ifBlank { "EP:$endpointId" }

    private fun classify(payload: MeshPayload): TrafficClass = when (payload.type) {
        "MESSAGE", "CONVERSATION" -> TrafficClass.CHAT
        "SOS_SYNC_REQ", "EVENT_SYNC_REQ" -> TrafficClass.SYNC_REQUEST
        // PING/PONG are per-link liveness probes (link shaping only), not origin presence.
        "SYSTEM" -> TrafficClass.PRESENCE
        // SOS_EVENT is charged after signature verification (admitVerifiedSos).
        else -> TrafficClass.OTHER
    }

    private companion object {
        const val SOS_EVENT = "SOS_EVENT"
        const val SHORT_ID = 8
        const val MAX_TYPE_LOG = 32
        const val LOG_INTERVAL_MS = 5_000L
    }
}
