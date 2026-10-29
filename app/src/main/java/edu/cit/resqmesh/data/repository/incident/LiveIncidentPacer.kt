package edu.cit.resqmesh.data.repository.incident

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.model.EventType
import edu.cit.resqmesh.core.network.ratelimit.OriginRateLimiter
import edu.cit.resqmesh.core.network.ratelimit.TrafficClass
import edu.cit.resqmesh.data.local.dao.DomainEventDao
import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import edu.cit.resqmesh.data.repository.IncidentEventSigning
import org.json.JSONObject

/**
 * Paces live broadcasts of new incidents (per reporter key) and offer changes (per helper key and
 * incident). Only signature-verified, first-time events from other phones are charged, so forgeries never
 * spend a victim's budget, and withdrawals and lead decisions are never charged. A limited event is neither
 * stored nor relayed; catch-up sync, which is never paced, delivers it when snapshots are compared.
 */
internal class LiveIncidentPacer(
    private val events: DomainEventDao,
    private val signing: IncidentEventSigning?,
    private val limiter: OriginRateLimiter = OriginRateLimiter()
) {
    private data class Charge(val signer: String, val bucket: String, val trafficClass: TrafficClass)

    suspend fun admit(event: DomainEventEntity?): Boolean {
        val charge = event?.let(::chargeFor)
        if (charge == null || !chargeable(event, charge.signer)) return true
        val allowed = limiter.check(charge.bucket, charge.trafficClass) == OriginRateLimiter.Decision.Allowed
        if (!allowed) AppLogger.d("INCIDENT_SYNC", "LIVE_LIMITED type=${event.eventType} incident=${event.entityId}")
        return allowed
    }

    private fun chargeFor(event: DomainEventEntity): Charge? {
        val payload = runCatching { JSONObject(event.payloadJson) }.getOrNull() ?: return null
        return when (event.eventType) {
            EventType.INCIDENT_CREATED.name -> payload.optString("reporterSigningKey")
                .let { Charge(it, it, TrafficClass.INCIDENT_NEW) }
            EventType.INCIDENT_OFFER_UPDATED.name -> payload.optString("helperKey")
                .let { Charge(it, "$it|${event.entityId}", TrafficClass.OFFER_CHANGE) }
            else -> null
        }
    }

    private suspend fun chargeable(event: DomainEventEntity, signer: String): Boolean =
        signer.isNotBlank() && signer != signing?.publicKey &&
            events.getEventById(event.eventId) == null && signing?.verify(event, signer) == true
}
