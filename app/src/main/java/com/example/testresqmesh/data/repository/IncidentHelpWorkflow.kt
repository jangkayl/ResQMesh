package com.example.testresqmesh.data.repository

import androidx.room.withTransaction
import com.example.testresqmesh.core.model.EntityType
import com.example.testresqmesh.core.model.EventType
import com.example.testresqmesh.core.model.IncidentState
import com.example.testresqmesh.core.utils.AppLogger
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
import org.json.JSONArray
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
    private var deferredDuringApply = false

    fun observeOffers(incidentId: String): Flow<List<IncidentOfferEntity>> = offers.observeForIncident(incidentId)
    fun observeAllActiveOffers(): Flow<List<IncidentOfferEntity>> = offers.observeAllActiveOffers()

    /** Repair active projections whose selected offer was withdrawn or superseded. */
    internal suspend fun reconcileWithdrawnSelection(incidentId: String) = mutex.withLock {
        reconcileWithdrawnSelectionLocked(incidentId)
    }

    private suspend fun reconcileWithdrawnSelectionLocked(incidentId: String) {
        val incident = incidents.getIncidentById(incidentId) ?: return
        if (incident.workflowVersion != 2 || incident.status in TERMINAL) return
        val offer = incident.selectionOfferId?.let { offers.getById(it) } ?: return
        if (offer.incidentId != incidentId || offer.helperKey != incident.selectedHelperKey) return
        val selectionRevision = incident.selectionOfferRevision ?: return
        val superseding = supersedingOfferEvent(incident, offer.offerId, offer.helperKey, selectionRevision)
        if (!offer.withdrawn && offer.revision <= selectionRevision && superseding == null) return
        val repaired = clearedSelection(incident).copy(updatedAt = maxOf(incident.updatedAt,
            superseding?.timestamp ?: offer.updatedAt))
        if (database != null) database.withTransaction { incidents.insertOrUpdate(repaired) }
        else incidents.insertOrUpdate(repaired)
        val marker = if (offer.withdrawn) "WITHDRAWAL_REPAIRED" else "OFFER_SELECTION_REPAIRED"
        AppLogger.d("INCIDENT_HELP", "$marker incident=$incidentId version=${incident.version}")
    }

    suspend fun offerHelp(incidentId: String, note: String): Boolean = mutex.withLock {
        reconcileWithdrawnSelectionLocked(incidentId)
        val clean = note.trim()
        if (clean.length !in 1..240) return@withLock false
        val incident = incidents.getIncidentById(incidentId) ?: return@withLock false
        if (incident.workflowVersion != 2 || incident.status in TERMINAL) return@withLock false
        val user = identity.getOrCreateUser()
        if (user.userId == incident.creatorId) return@withLock false
        val key = signer.publicKey
        val existing = offers.getByHelper(incidentId, key)
        if (existing != null && !existing.withdrawn && incident.selectionOfferId == existing.offerId &&
            incident.selectedHelperKey == key) return@withLock false
        val next = (existing?.revision ?: 0L) + 1L
        val payload = JSONObject().apply {
            put("offerId", existing?.offerId ?: UUID.randomUUID().toString())
            put("helperKey", key)
            put("helperNodeId", identity.getDeviceId())
            put("note", clean)
            put("revision", next)
        }
        emit(incident, EventType.INCIDENT_OFFER_UPDATED, next, payload, alreadyLocked = true)
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
        reconcileWithdrawnSelection(incidentId)
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

    suspend fun resolve(incidentId: String): Boolean = close(incidentId, EventType.INCIDENT_RESOLVED)

    suspend fun cancel(incidentId: String): Boolean = close(incidentId, EventType.INCIDENT_CANCELLED)

    private suspend fun close(incidentId: String, type: EventType): Boolean = mutex.withLock {
        reconcileWithdrawnSelectionLocked(incidentId)
        val incident = incidents.getIncidentById(incidentId) ?: return@withLock false
        if (!isLocalReporter(incident) || incident.status in TERMINAL ||
            (type == EventType.INCIDENT_RESOLVED && incident.status != IncidentState.RESPONDING.name))
            return@withLock false
        val history = events.getEventsForEntity(incidentId).filter { it.applied }
        // Offer revisions are independent of reporter versions. Record each observed helper
        // frontier, the preceding reporter decision, and the current helper commitment.
        val offerFrontier = history.filter { it.eventType in OFFER_TYPES }
            .groupBy { JSONObject(it.payloadJson).optString("offerId") }
            .values.map { stream -> stream.maxBy { it.logicalVersion } }
        val reporterDecision = history.filter { it.actorId == incident.creatorId &&
            it.eventType in REPORTER_TYPES && it.logicalVersion == incident.version }
        val commitment = history.filter { it.eventType in HELPER_DECISIONS &&
            it.logicalVersion == incident.version }
        val dependencies = (offerFrontier + reporterDecision + commitment).map { it.eventId }.distinct().sorted()
        val payload = JSONObject().put("closureProtocol", 1)
            .put("closureDependencies", JSONArray(dependencies))
            .put("closureSelectionId", incident.selectionId ?: "")
            .put("closureConfirmationId", commitment.firstOrNull {
                it.eventType == EventType.INCIDENT_LEAD_CONFIRMED.name &&
                    JSONObject(it.payloadJson).optString("selectionId") == incident.selectionId
            }?.eventId ?: "")
        emit(incident, type, incident.version + 1, payload, alreadyLocked = true)
    }

    private suspend fun isLocalReporter(incident: IncidentEntity): Boolean =
        incident.workflowVersion == 2 &&
            IncidentOwnership.isReporter(incident, identity.getOrCreateUser().userId, signer.publicKey)

    private suspend fun emit(incident: IncidentEntity, type: EventType, revision: Long, payload: JSONObject, alreadyLocked: Boolean = false): Boolean {
        val user = identity.getOrCreateUser()
        val unsigned = DomainEventEntity(
            eventId = UUID.randomUUID().toString(), entityId = incident.incidentId,
            entityType = EntityType.INCIDENT.name, eventType = type.name,
            actorId = user.userId, actorName = user.displayName,
            logicalVersion = revision, timestamp = System.currentTimeMillis(),
            payloadJson = payload.toString()
        )
        val event = unsigned.copy(signature = signer.sign(unsigned))
        val applied = if (alreadyLocked) applyLocked(event) else apply(event)
        if (applied) publish(event, type == EventType.INCIDENT_LEAD_SELECTED || type == EventType.INCIDENT_CANCELLED)
        return applied
    }

    suspend fun apply(event: DomainEventEntity): Boolean = mutex.withLock { applyValidated(event) }

    internal suspend fun <T> withReplayLock(block: suspend () -> T): T = mutex.withLock { block() }
    internal suspend fun applyDuringReplay(event: DomainEventEntity): Boolean = applyValidated(event)

    private suspend fun applyValidated(event: DomainEventEntity): Boolean {
        val prior = events.getEventById(event.eventId)
        if (prior != null && (IncidentSyncSnapshot.eventHash(prior) != IncidentSyncSnapshot.eventHash(event) ||
                prior.validationStatus == "REJECTED" || prior.applied)) return false
        deferredDuringApply = false
        val applied = applyLocked(event)
        if (!applied && !deferredDuringApply && events.getEventById(event.eventId)?.applied != true) {
            if (prior == null) events.insertEvent(event.copy(applied = false, validationStatus = "REJECTED"))
            else events.setValidationStatus(event.eventId, "REJECTED")
        }
        return applied
    }

    private suspend fun applyLocked(event: DomainEventEntity): Boolean {
        if (event.entityType != EntityType.INCIDENT.name ||
            event.eventType !in TYPES && event.eventType !in setOf(
                EventType.INCIDENT_RESOLVED.name, EventType.INCIDENT_CANCELLED.name)) return false
        reconcileWithdrawnSelectionLocked(event.entityId)
        val prior = events.getEventById(event.eventId)
        if (prior?.applied == true) return false
        if (prior != null && IncidentSyncSnapshot.eventHash(prior) != IncidentSyncSnapshot.eventHash(event)) return false
        val incident = incidents.getIncidentById(event.entityId)
        suspend fun defer(verified: Boolean = false) {
            deferredDuringApply = true
            if (prior == null) events.insertEvent(event.copy(applied = false, validationStatus = if (verified) "ACCEPTED" else "UNVERIFIED"))
            else events.setValidationStatus(event.eventId, if (verified) "ACCEPTED" else "UNVERIFIED")
        }
        if (incident == null) {
            defer()
            return false
        }
        if (incident.workflowVersion != 2) return false
        val payload = runCatching { JSONObject(event.payloadJson) }.getOrNull() ?: return false
        val offer = payload.optString("offerId").takeIf { it.isNotBlank() }?.let { offers.getById(it) }
        val helperKey = payload.optString("helperKey")
        if ((event.eventType == EventType.INCIDENT_OFFER_WITHDRAWN.name && offer == null) ||
            (event.eventType in setOf(EventType.INCIDENT_LEAD_CONFIRMED.name,
                EventType.INCIDENT_LEAD_DECLINED.name) && incident.selectedHelperKey == null)) {
            val historicalSelection = selectionEvent(incident, payload.optString("selectionId"))
            if (historicalSelection != null && selectionSupersededBy(incident, historicalSelection) != null) {
                retainHistoricalDecision(event, incident, historicalSelection)
                return false
            }
            if (!event.signature.isNullOrBlank()) defer()
            return false
        }
        val actorKey = when (event.eventType) {
            EventType.INCIDENT_OFFER_UPDATED.name -> helperKey
            EventType.INCIDENT_OFFER_WITHDRAWN.name -> offer?.helperKey
            EventType.INCIDENT_LEAD_CONFIRMED.name, EventType.INCIDENT_LEAD_DECLINED.name -> incident.selectedHelperKey
            else -> incident.reporterSigningKey
        }
        if (actorKey.isNullOrBlank() || !signer.verify(event, actorKey) ||
            (event.eventType == EventType.INCIDENT_OFFER_WITHDRAWN.name && helperKey != actorKey)) {
            if (prior != null) events.setValidationStatus(event.eventId, "REJECTED")
            return false
        }

        // Valid late helper decisions remain immutable history; they never reopen terminal state.
        if (incident.status in TERMINAL) {
            val frontier = events.getEventsForEntity(event.entityId).filter {
                it.applied && it.validationStatus == "ACCEPTED" && it.eventType in OFFER_TYPES &&
                    runCatching { JSONObject(it.payloadJson).optString("offerId") == payload.optString("offerId") }.getOrDefault(false)
            }.maxOfOrNull { it.logicalVersion } ?: (offer?.revision ?: 0L)
            val byHelper = offers.getByHelper(event.entityId, actorKey)
            val selection = selectionEvent(incident, payload.optString("selectionId"))
            if (event.eventType in HELPER_DECISIONS && selection != null)
                retainHistoricalDecision(event, incident, selection)
            else if (event.eventType in OFFER_TYPES && event.actorId != incident.creatorId &&
                payload.optString("offerId").isNotBlank() && payload.optLong("revision") == event.logicalVersion &&
                (event.eventType != EventType.INCIDENT_OFFER_UPDATED.name || payload.optString("note").trim().length in 1..240) &&
                (offer == null || offer.helperKey == actorKey) &&
                (byHelper == null || byHelper.offerId == payload.optString("offerId"))) {
                if (event.logicalVersion > frontier + 1L) { defer(verified = true); return false }
                if (event.logicalVersion != frontier + 1L) return false
                if (prior == null) events.insertEvent(event.copy(applied = true, validationStatus = "ACCEPTED"))
                else { events.setApplied(event.eventId, true); events.setValidationStatus(event.eventId, "ACCEPTED") }
            }
            return false
        }

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
                    event.actorId == incident.creatorId) return false
                if (revision > (byHelper?.revision ?: 0L) + 1L) {
                    defer(verified = true)
                    return false
                }
                if (revision != (byHelper?.revision ?: 0L) + 1L) return false
                updatedOffer = IncidentOfferEntity(offerId, event.entityId, helperKey,
                    payload.optString("helperNodeId"), event.actorName,
                    note, revision, false, event.timestamp)
                if (incident.selectionOfferId == offerId && incident.selectedHelperKey == helperKey &&
                    revision > (incident.selectionOfferRevision ?: 0L)) {
                    updatedIncident = clearedSelection(incident).copy(updatedAt = maxOf(incident.updatedAt, event.timestamp))
                }
            }
            EventType.INCIDENT_OFFER_WITHDRAWN.name -> {
                if (offer == null || offer.incidentId != event.entityId || offer.withdrawn ||
                    payload.optLong("revision") != event.logicalVersion)
                    return false
                if (event.logicalVersion > offer.revision + 1) {
                    defer(verified = true)
                    return false
                }
                if (event.logicalVersion != offer.revision + 1) return false
                updatedOffer = offer.copy(revision = event.logicalVersion, withdrawn = true, updatedAt = event.timestamp)
                if (incident.selectionOfferId == offer.offerId && incident.selectedHelperKey == offer.helperKey) {
                    updatedIncident = clearedSelection(incident).copy(updatedAt = maxOf(incident.updatedAt, event.timestamp))
                }
            }
            EventType.INCIDENT_LEAD_SELECTED.name -> {
                if (event.actorId != incident.creatorId || payload.optString("selectionId").isBlank())
                    return false
                if (event.logicalVersion > incident.version + 1 ||
                    (event.logicalVersion > incident.version && incident.status != IncidentState.OPEN.name) ||
                    (event.logicalVersion == incident.version + 1 &&
                        (offer == null || offer.revision < payload.optLong("offerRevision")))) {
                    defer(verified = true)
                    return false
                }
                if (incident.status != IncidentState.OPEN.name ||
                    event.logicalVersion != incident.version + 1 || offer == null ||
                    offer.incidentId != event.entityId || payload.optLong("offerRevision") < 1 ||
                    offer.helperKey != helperKey) return false
                val withdrawnSelection = supersedingOfferEvent(incident, offer.offerId, offer.helperKey,
                    payload.optLong("offerRevision")) != null
                if (!withdrawnSelection && (offer.withdrawn || offer.revision != payload.optLong("offerRevision")))
                    return false
                // Consume the reporter's version even when a newer offer revision reached this replica first.
                updatedIncident = if (withdrawnSelection) clearedSelection(incident).copy(
                    version = event.logicalVersion, updatedAt = maxOf(incident.updatedAt, event.timestamp))
                else incident.copy(status = IncidentState.AWAITING_HELPER.name,
                    version = event.logicalVersion, updatedAt = event.timestamp,
                    selectionId = payload.getString("selectionId"), selectionOfferId = offer.offerId,
                    selectionOfferRevision = offer.revision, selectedHelperKey = offer.helperKey,
                    primaryResponderId = null, primaryResponderName = offer.helperName,
                    selectionConfirmedAt = null)
            }
            EventType.INCIDENT_LEAD_CONFIRMED.name, EventType.INCIDENT_LEAD_DECLINED.name -> {
                if (incident.status != IncidentState.AWAITING_HELPER.name ||
                    incident.selectionId != payload.optString("selectionId") ||
                    event.logicalVersion != incident.version || offerForSelectionIsWithdrawn(incident)) return false
                updatedIncident = if (event.eventType == EventType.INCIDENT_LEAD_CONFIRMED.name)
                    incident.copy(status = IncidentState.RESPONDING.name, selectionConfirmedAt = event.timestamp,
                        primaryResponderId = event.actorId, updatedAt = event.timestamp)
                else clearedSelection(incident)
            }
            EventType.INCIDENT_LEAD_REVOKED.name -> {
                if (event.actorId == incident.creatorId && event.logicalVersion > incident.version + 1) {
                    defer(verified = true)
                    return false
                }
                if (event.actorId != incident.creatorId ||
                    event.logicalVersion != incident.version + 1) return false
                val target = payload.optString("selectionId")
                if (incident.selectionId == target && target.isNotBlank()) {
                    updatedIncident = clearedSelection(incident).copy(version = event.logicalVersion, updatedAt = event.timestamp)
                } else {
                    val historicalSelection = selectionEvent(incident, target) ?: return false
                    if (selectionSupersededBy(incident, historicalSelection) == null) return false
                    // A reporter can revoke after offer invalidation; retain any newer selection.
                    updatedIncident = incident.copy(version = event.logicalVersion,
                        updatedAt = maxOf(incident.updatedAt, event.timestamp))
                }
            }
            EventType.INCIDENT_RESOLVED.name, EventType.INCIDENT_CANCELLED.name -> {
                if (event.actorId != incident.creatorId) return false
                var closingIncident = incident
                if (payload.has("closureProtocol") || payload.has("closureDependencies")) {
                    if (payload.optInt("closureProtocol") != 1) return false
                    val dependencies = payload.optJSONArray("closureDependencies") ?: return false
                    for (i in 0 until dependencies.length()) {
                        val id = dependencies.opt(i) as? String ?: return false
                        if (id.isBlank() || id == event.eventId) return false
                        val prerequisite = events.getEventById(id)
                        if (prerequisite != null && (prerequisite.entityId != event.entityId ||
                                prerequisite.entityType != EntityType.INCIDENT.name ||
                                prerequisite.eventType !in TYPES)) return false
                        if (prerequisite?.applied != true) {
                            defer(verified = true)
                            AppLogger.d("INCIDENT_HELP", "CLOSURE_WAITING_DEPENDENCIES incident=${event.entityId}")
                            return false
                        }
                    }
                    // Freeze only the helper history that the reporter observed. A newer
                    // offline offer revision may already have cleared this replica's selection.
                    closingIncident = closureProjection(incident, payload) ?: return false
                }
                if (event.actorId == incident.creatorId && event.logicalVersion > incident.version + 1) {
                    defer(verified = true)
                    return false
                }
                if (event.actorId != incident.creatorId || event.logicalVersion != incident.version + 1 ||
                    (event.eventType == EventType.INCIDENT_RESOLVED.name && closingIncident.status != IncidentState.RESPONDING.name))
                    return false
                updatedIncident = closingIncident.copy(status = if (event.eventType == EventType.INCIDENT_RESOLVED.name)
                    IncidentState.RESOLVED.name else IncidentState.CANCELLED.name,
                    version = event.logicalVersion, updatedAt = event.timestamp)
            }
            else -> return false
        }
        suspend fun persist() {
            updatedOffer?.let { offers.upsert(it) }
            updatedIncident?.let { incidents.insertOrUpdate(it) }
            if (prior == null) events.insertEvent(event.copy(applied = true, validationStatus = "ACCEPTED"))
            else { events.setApplied(event.eventId, true); events.setValidationStatus(event.eventId, "ACCEPTED") }
        }
        if (database != null) database.withTransaction { persist() } else persist()
        if (event.eventType == EventType.INCIDENT_OFFER_WITHDRAWN.name && updatedIncident != null) {
            AppLogger.d("INCIDENT_HELP", "WITHDRAWAL_CLEARED_SELECTION incident=${event.entityId} version=${incident.version}")
        } else if (event.eventType == EventType.INCIDENT_OFFER_UPDATED.name && updatedIncident != null) {
            AppLogger.d("INCIDENT_HELP", "OFFER_REVISION_CLEARED_SELECTION incident=${event.entityId} version=${incident.version}")
        } else if (event.eventType == EventType.INCIDENT_LEAD_SELECTED.name && updatedIncident?.selectionId == null) {
            AppLogger.d("INCIDENT_HELP", "SUPERSEDED_SELECTION_SKIPPED incident=${event.entityId} version=${event.logicalVersion}")
        }
        return true
    }

    private suspend fun retainHistoricalDecision(event: DomainEventEntity, incident: IncidentEntity,
        selection: DomainEventEntity) {
        if (event.eventType !in HELPER_DECISIONS || event.actorId == incident.creatorId ||
            event.logicalVersion != selection.logicalVersion) return
        val key = JSONObject(selection.payloadJson).optString("helperKey")
        if (key.isBlank() || !signer.verify(event, key)) return
        if (events.getEventById(event.eventId) == null)
            events.insertEvent(event.copy(applied = true, validationStatus = "ACCEPTED"))
        else { events.setApplied(event.eventId, true); events.setValidationStatus(event.eventId, "ACCEPTED") }
    }

    private suspend fun offerForSelectionIsWithdrawn(incident: IncidentEntity): Boolean =
        incident.selectionOfferId?.let { id ->
            offers.getById(id)?.let { it.withdrawn || it.revision != incident.selectionOfferRevision }
        } != false

    private suspend fun closureProjection(incident: IncidentEntity, payload: JSONObject): IncidentEntity? {
        val selectionId = payload.opt("closureSelectionId") as? String ?: return null
        val confirmationId = payload.opt("closureConfirmationId") as? String ?: return null
        if (selectionId.isEmpty()) return if (confirmationId.isEmpty()) clearedSelection(incident) else null
        val dependencies = payload.getJSONArray("closureDependencies")
        val ids = (0 until dependencies.length()).map { dependencies.getString(it) }.toSet()
        val selection = selectionEvent(incident, selectionId) ?: return null
        if (selection.eventId !in ids || selection.logicalVersion != incident.version) return null
        val selected = JSONObject(selection.payloadJson)
        val offerId = selected.optString("offerId")
        val helperKey = selected.optString("helperKey")
        val revision = selected.optLong("offerRevision")
        val history = events.getEventsForEntity(incident.incidentId).filter { it.applied }
        val offered = history.firstOrNull { it.eventType == EventType.INCIDENT_OFFER_UPDATED.name &&
            it.logicalVersion == revision && signer.verify(it, helperKey) &&
            JSONObject(it.payloadJson).let { p -> p.optString("offerId") == offerId && p.optString("helperKey") == helperKey }
        } ?: return null
        if (history.any { it.eventId in ids && it.eventType in OFFER_TYPES &&
                it.logicalVersion > revision && JSONObject(it.payloadJson).optString("offerId") == offerId }) return null
        val confirmation = if (confirmationId.isEmpty()) null else {
            events.getEventById(confirmationId)?.takeIf {
                it.eventId in ids && it.applied && it.entityId == incident.incidentId &&
                    it.eventType == EventType.INCIDENT_LEAD_CONFIRMED.name && it.logicalVersion == incident.version &&
                    signer.verify(it, helperKey) && JSONObject(it.payloadJson).optString("selectionId") == selectionId
            } ?: return null
        }
        return incident.copy(status = if (confirmation == null) IncidentState.AWAITING_HELPER.name else IncidentState.RESPONDING.name,
            selectionId = selectionId, selectionOfferId = offerId, selectionOfferRevision = revision,
            selectedHelperKey = helperKey, primaryResponderName = offered.actorName,
            primaryResponderId = confirmation?.actorId, selectionConfirmedAt = confirmation?.timestamp)
    }

    private suspend fun selectionEvent(incident: IncidentEntity, selectionId: String): DomainEventEntity? {
        if (selectionId.isBlank()) return null
        val reporterKey = incident.reporterSigningKey ?: return null
        return events.getEventsForEntity(incident.incidentId).firstOrNull { event ->
            event.applied && event.eventType == EventType.INCIDENT_LEAD_SELECTED.name &&
                event.actorId == incident.creatorId && signer.verify(event, reporterKey) &&
                runCatching { JSONObject(event.payloadJson).optString("selectionId") == selectionId }.getOrDefault(false)
        }
    }

    private suspend fun selectionSupersededBy(incident: IncidentEntity, selection: DomainEventEntity): DomainEventEntity? {
        val payload = runCatching { JSONObject(selection.payloadJson) }.getOrNull() ?: return null
        return supersedingOfferEvent(incident, payload.optString("offerId"), payload.optString("helperKey"),
            payload.optLong("offerRevision"))
    }

    private suspend fun supersedingOfferEvent(incident: IncidentEntity, offerId: String, helperKey: String,
        selectionRevision: Long): DomainEventEntity? {
        if (offerId.isBlank() || helperKey.isBlank() || selectionRevision < 1) return null
        return events.getEventsForEntity(incident.incidentId).firstOrNull { event ->
            event.applied && event.eventType in setOf(EventType.INCIDENT_OFFER_WITHDRAWN.name,
                EventType.INCIDENT_OFFER_UPDATED.name) &&
                event.logicalVersion > selectionRevision && signer.verify(event, helperKey) &&
                runCatching {
                    val payload = JSONObject(event.payloadJson)
                    payload.optString("offerId") == offerId && payload.optString("helperKey") == helperKey &&
                        payload.optLong("revision") == event.logicalVersion
                }.getOrDefault(false)
        }
    }

    private fun clearedSelection(incident: IncidentEntity) = incident.copy(
        status = IncidentState.OPEN.name, selectionId = null, selectionOfferId = null,
        selectionOfferRevision = null, selectedHelperKey = null, selectionConfirmedAt = null,
        primaryResponderId = null, primaryResponderName = null)

    companion object {
        private val TERMINAL = setOf(IncidentState.RESOLVED.name, IncidentState.CANCELLED.name)
        internal val OFFER_TYPES = setOf(EventType.INCIDENT_OFFER_UPDATED.name, EventType.INCIDENT_OFFER_WITHDRAWN.name)
        private val HELPER_DECISIONS = setOf(EventType.INCIDENT_LEAD_CONFIRMED.name, EventType.INCIDENT_LEAD_DECLINED.name)
        private val REPORTER_TYPES = setOf(EventType.INCIDENT_LEAD_SELECTED.name, EventType.INCIDENT_LEAD_REVOKED.name)
        val TYPES = setOf(EventType.INCIDENT_OFFER_UPDATED.name, EventType.INCIDENT_OFFER_WITHDRAWN.name,
            EventType.INCIDENT_LEAD_SELECTED.name, EventType.INCIDENT_LEAD_CONFIRMED.name,
            EventType.INCIDENT_LEAD_DECLINED.name, EventType.INCIDENT_LEAD_REVOKED.name)
    }
}
