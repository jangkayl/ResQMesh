package com.example.testresqmesh.data.repository

import androidx.room.withTransaction
import com.example.testresqmesh.core.model.EntityType
import com.example.testresqmesh.core.model.EventType
import com.example.testresqmesh.core.model.IncidentState
import com.example.testresqmesh.data.local.AppDatabase
import com.example.testresqmesh.data.local.dao.DomainEventDao
import com.example.testresqmesh.data.local.dao.IncidentDao
import com.example.testresqmesh.data.local.dao.IncidentOfferDao
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

/** Version-2 civilian coordination. Incident decisions have one reporter writer; offers have one helper writer each. */
class IncidentHelpWorkflow(
    private val database: AppDatabase?,
    private val incidents: IncidentDao,
    private val offers: IncidentOfferDao,
    private val events: DomainEventDao,
    private val identity: IdentityProvider,
    private val signer: IncidentEventSigning,
    private val publish: (DomainEventEntity, Boolean) -> Unit
) {
    private val mutex = Mutex()

    fun observeOffers(incidentId: String): Flow<List<IncidentOfferEntity>> = offers.observeForIncident(incidentId)
    fun observeAllActiveOffers(): Flow<List<IncidentOfferEntity>> = offers.observeAllActiveOffers()

    suspend fun offerHelp(incidentId: String, note: String): Boolean {
        val clean = note.trim()
        if (clean.length !in 1..240) return false
        val incident = incidents.getIncidentById(incidentId) ?: return false
        if (incident.workflowVersion != 2 || incident.status in TERMINAL) return false
        val user = identity.getOrCreateUser()
        if (user.userId == incident.creatorId) return false
        val key = signer.publicKey
        val existing = offers.getByHelper(incidentId, key)
        val next = (existing?.revision ?: 0L) + 1L
        val payload = JSONObject().apply {
            put("offerId", existing?.offerId ?: UUID.randomUUID().toString())
            put("helperKey", key)
            put("helperNodeId", identity.getDeviceId())
            put("note", clean)
            put("revision", next)
        }
        return emit(incident, EventType.INCIDENT_OFFER_UPDATED, next, payload)
    }

    suspend fun withdrawOffer(incidentId: String): Boolean {
        val incident = incidents.getIncidentById(incidentId) ?: return false
        val offer = offers.getByHelper(incidentId, signer.publicKey) ?: return false
        if (offer.withdrawn || incident.status in TERMINAL) return false
        return emit(incident, EventType.INCIDENT_OFFER_WITHDRAWN, offer.revision + 1,
            JSONObject().put("offerId", offer.offerId).put("helperKey", signer.publicKey)
                .put("revision", offer.revision + 1))
    }

    suspend fun selectLead(incidentId: String, offerId: String): Boolean {
        val incident = incidents.getIncidentById(incidentId) ?: return false
        if (!isLocalReporter(incident) || incident.status != IncidentState.OPEN.name) return false
        val offer = offers.getById(offerId) ?: return false
        if (offer.incidentId != incidentId || offer.withdrawn) return false
        return emit(incident, EventType.INCIDENT_LEAD_SELECTED, incident.version + 1,
            JSONObject().put("selectionId", UUID.randomUUID().toString())
                .put("offerId", offer.offerId).put("offerRevision", offer.revision)
                .put("helperKey", offer.helperKey).put("helperName", offer.helperName))
    }

    suspend fun confirmLead(incidentId: String): Boolean = helperDecision(incidentId, EventType.INCIDENT_LEAD_CONFIRMED)
    suspend fun declineLead(incidentId: String): Boolean = helperDecision(incidentId, EventType.INCIDENT_LEAD_DECLINED)

    private suspend fun helperDecision(incidentId: String, type: EventType): Boolean {
        val incident = incidents.getIncidentById(incidentId) ?: return false
        if (incident.status != IncidentState.AWAITING_HELPER.name ||
            incident.selectedHelperKey != signer.publicKey || incident.selectionId == null) return false
        return emit(incident, type, incident.version,
            JSONObject().put("selectionId", incident.selectionId))
    }

    suspend fun revokeLead(incidentId: String): Boolean {
        val incident = incidents.getIncidentById(incidentId) ?: return false
        if (!isLocalReporter(incident) || incident.selectionId == null || incident.status in TERMINAL) return false
        return emit(incident, EventType.INCIDENT_LEAD_REVOKED, incident.version + 1,
            JSONObject().put("selectionId", incident.selectionId))
    }

    suspend fun resolve(incidentId: String): Boolean {
        val incident = incidents.getIncidentById(incidentId) ?: return false
        if (!isLocalReporter(incident) || incident.status != IncidentState.RESPONDING.name) return false
        return emit(incident, EventType.INCIDENT_RESOLVED, incident.version + 1, JSONObject())
    }

    suspend fun cancel(incidentId: String): Boolean {
        val incident = incidents.getIncidentById(incidentId) ?: return false
        if (!isLocalReporter(incident) || incident.status in TERMINAL) return false
        return emit(incident, EventType.INCIDENT_CANCELLED, incident.version + 1, JSONObject())
    }

    private suspend fun isLocalReporter(incident: IncidentEntity): Boolean =
        incident.workflowVersion == 2 && incident.creatorId == identity.getOrCreateUser().userId &&
            incident.reporterSigningKey == signer.publicKey

    private suspend fun emit(incident: IncidentEntity, type: EventType, revision: Long, payload: JSONObject): Boolean {
        val user = identity.getOrCreateUser()
        val unsigned = DomainEventEntity(
            eventId = UUID.randomUUID().toString(), entityId = incident.incidentId,
            entityType = EntityType.INCIDENT.name, eventType = type.name,
            actorId = user.userId, actorName = user.displayName,
            logicalVersion = revision, timestamp = System.currentTimeMillis(),
            payloadJson = payload.toString()
        )
        val event = unsigned.copy(signature = signer.sign(unsigned))
        val applied = apply(event)
        if (applied) publish(event, type == EventType.INCIDENT_LEAD_SELECTED || type == EventType.INCIDENT_CANCELLED)
        return applied
    }

    suspend fun apply(event: DomainEventEntity): Boolean = mutex.withLock {
        if (event.entityType != EntityType.INCIDENT.name ||
            event.eventType !in TYPES && event.eventType !in setOf(
                EventType.INCIDENT_RESOLVED.name, EventType.INCIDENT_CANCELLED.name)) return@withLock false
        val prior = events.getEventById(event.eventId)
        if (prior?.applied == true) return@withLock false
        if (prior != null && prior.copy(applied = false) != event.copy(applied = false)) return@withLock false
        val incident = incidents.getIncidentById(event.entityId)
        suspend fun defer() {
            if (prior == null) events.insertEvent(event.copy(applied = false))
        }
        if (incident == null) {
            defer()
            return@withLock false
        }
        if (incident.workflowVersion != 2 || incident.status in TERMINAL) return@withLock false
        val payload = runCatching { JSONObject(event.payloadJson) }.getOrNull() ?: return@withLock false
        val offer = payload.optString("offerId").takeIf { it.isNotBlank() }?.let { offers.getById(it) }
        val helperKey = payload.optString("helperKey")
        if ((event.eventType == EventType.INCIDENT_OFFER_WITHDRAWN.name && offer == null) ||
            (event.eventType in setOf(EventType.INCIDENT_LEAD_CONFIRMED.name,
                EventType.INCIDENT_LEAD_DECLINED.name) && incident.selectedHelperKey == null)) {
            if (!event.signature.isNullOrBlank()) defer()
            return@withLock false
        }
        val actorKey = when (event.eventType) {
            EventType.INCIDENT_OFFER_UPDATED.name -> helperKey
            EventType.INCIDENT_OFFER_WITHDRAWN.name -> offer?.helperKey
            EventType.INCIDENT_LEAD_CONFIRMED.name, EventType.INCIDENT_LEAD_DECLINED.name -> incident.selectedHelperKey
            else -> incident.reporterSigningKey
        }
        if (actorKey.isNullOrBlank() || !signer.verify(event, actorKey) ||
            (event.eventType == EventType.INCIDENT_OFFER_WITHDRAWN.name && helperKey != actorKey)) return@withLock false

        var updatedIncident: IncidentEntity? = null
        var updatedOffer: IncidentOfferEntity? = null
        when (event.eventType) {
            EventType.INCIDENT_OFFER_UPDATED.name -> {
                val offerId = payload.optString("offerId")
                val note = payload.optString("note").trim()
                val revision = payload.optLong("revision")
                val byHelper = offers.getByHelper(event.entityId, helperKey)
                if (offerId.isBlank() || note.length !in 1..240 || revision < 1 ||
                    event.logicalVersion != revision || (offer != null && offer.helperKey != helperKey) ||
                    (byHelper != null && byHelper.offerId != offerId) ||
                    event.actorId == incident.creatorId) return@withLock false
                if (revision > (byHelper?.revision ?: 0L) + 1L) {
                    defer()
                    return@withLock false
                }
                if (revision != (byHelper?.revision ?: 0L) + 1L) return@withLock false
                updatedOffer = IncidentOfferEntity(offerId, event.entityId, helperKey,
                    payload.optString("helperNodeId"), event.actorName,
                    note, revision, false, event.timestamp)
            }
            EventType.INCIDENT_OFFER_WITHDRAWN.name -> {
                if (offer == null || offer.incidentId != event.entityId || offer.withdrawn ||
                    payload.optLong("revision") != event.logicalVersion)
                    return@withLock false
                if (event.logicalVersion > offer.revision + 1) {
                    defer()
                    return@withLock false
                }
                if (event.logicalVersion != offer.revision + 1) return@withLock false
                updatedOffer = offer.copy(revision = event.logicalVersion, withdrawn = true, updatedAt = event.timestamp)
            }
            EventType.INCIDENT_LEAD_SELECTED.name -> {
                if (event.actorId != incident.creatorId || payload.optString("selectionId").isBlank())
                    return@withLock false
                if (event.logicalVersion > incident.version + 1 ||
                    (event.logicalVersion > incident.version && incident.status != IncidentState.OPEN.name) ||
                    (event.logicalVersion == incident.version + 1 &&
                        (offer == null || offer.revision < payload.optLong("offerRevision")))) {
                    defer()
                    return@withLock false
                }
                if (incident.status != IncidentState.OPEN.name ||
                    event.logicalVersion != incident.version + 1 || offer == null || offer.withdrawn ||
                    offer.incidentId != event.entityId || offer.revision != payload.optLong("offerRevision") ||
                    offer.helperKey != helperKey) return@withLock false
                updatedIncident = incident.copy(status = IncidentState.AWAITING_HELPER.name,
                    version = event.logicalVersion, updatedAt = event.timestamp,
                    selectionId = payload.getString("selectionId"), selectionOfferId = offer.offerId,
                    selectionOfferRevision = offer.revision, selectedHelperKey = offer.helperKey,
                    primaryResponderId = null, primaryResponderName = offer.helperName,
                    selectionConfirmedAt = null)
            }
            EventType.INCIDENT_LEAD_CONFIRMED.name, EventType.INCIDENT_LEAD_DECLINED.name -> {
                if (incident.status != IncidentState.AWAITING_HELPER.name ||
                    incident.selectionId != payload.optString("selectionId") ||
                    event.logicalVersion != incident.version || offerForSelectionIsWithdrawn(incident)) return@withLock false
                updatedIncident = if (event.eventType == EventType.INCIDENT_LEAD_CONFIRMED.name)
                    incident.copy(status = IncidentState.RESPONDING.name, selectionConfirmedAt = System.currentTimeMillis(),
                        primaryResponderId = event.actorId, updatedAt = event.timestamp)
                else clearedSelection(incident)
            }
            EventType.INCIDENT_LEAD_REVOKED.name -> {
                if (event.actorId == incident.creatorId && event.logicalVersion > incident.version + 1) {
                    defer()
                    return@withLock false
                }
                if (event.actorId != incident.creatorId || incident.selectionId == null ||
                    incident.selectionId != payload.optString("selectionId") ||
                    event.logicalVersion != incident.version + 1) return@withLock false
                updatedIncident = clearedSelection(incident).copy(version = event.logicalVersion, updatedAt = event.timestamp)
            }
            EventType.INCIDENT_RESOLVED.name, EventType.INCIDENT_CANCELLED.name -> {
                if (event.actorId == incident.creatorId && event.logicalVersion > incident.version + 1) {
                    defer()
                    return@withLock false
                }
                if (event.actorId != incident.creatorId || event.logicalVersion != incident.version + 1 ||
                    (event.eventType == EventType.INCIDENT_RESOLVED.name && incident.status != IncidentState.RESPONDING.name))
                    return@withLock false
                updatedIncident = incident.copy(status = if (event.eventType == EventType.INCIDENT_RESOLVED.name)
                    IncidentState.RESOLVED.name else IncidentState.CANCELLED.name,
                    version = event.logicalVersion, updatedAt = event.timestamp)
            }
            else -> return@withLock false
        }
        suspend fun persist() {
            updatedOffer?.let { offers.upsert(it) }
            updatedIncident?.let { incidents.insertOrUpdate(it) }
            if (prior == null) events.insertEvent(event.copy(applied = true))
            else events.setApplied(event.eventId, true)
        }
        if (database != null) database.withTransaction { persist() } else persist()
        true
    }

    private suspend fun offerForSelectionIsWithdrawn(incident: IncidentEntity): Boolean =
        incident.selectionOfferId?.let { id ->
            offers.getById(id)?.let { it.withdrawn || it.revision != incident.selectionOfferRevision }
        } != false

    private fun clearedSelection(incident: IncidentEntity) = incident.copy(
        status = IncidentState.OPEN.name, selectionId = null, selectionOfferId = null,
        selectionOfferRevision = null, selectedHelperKey = null, selectionConfirmedAt = null,
        primaryResponderId = null, primaryResponderName = null)

    companion object {
        private val TERMINAL = setOf(IncidentState.RESOLVED.name, IncidentState.CANCELLED.name)
        val TYPES = setOf(EventType.INCIDENT_OFFER_UPDATED.name, EventType.INCIDENT_OFFER_WITHDRAWN.name,
            EventType.INCIDENT_LEAD_SELECTED.name, EventType.INCIDENT_LEAD_CONFIRMED.name,
            EventType.INCIDENT_LEAD_DECLINED.name, EventType.INCIDENT_LEAD_REVOKED.name)
    }
}
