package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.security.input.TextField
import edu.cit.resqmesh.core.security.input.TextSanitizer
import androidx.room.withTransaction
import edu.cit.resqmesh.core.model.EntityType
import edu.cit.resqmesh.core.model.EventType
import edu.cit.resqmesh.core.model.HELP_WORKFLOW_VERSION
import edu.cit.resqmesh.core.model.IncidentState
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.data.local.AppDatabase
import edu.cit.resqmesh.data.local.dao.DomainEventDao
import edu.cit.resqmesh.data.local.dao.IncidentDao
import edu.cit.resqmesh.data.local.dao.IncidentOfferDao
import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import edu.cit.resqmesh.data.local.entity.EventValidationStatus
import edu.cit.resqmesh.data.local.entity.IncidentEntity
import edu.cit.resqmesh.data.local.entity.IncidentOfferEntity
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
    private val selectionHistory = IncidentSelectionHistory(events, offers, signer)
    private val rules = HelpEventRule.all(offers, events, selectionHistory)

    fun observeOffers(incidentId: String): Flow<List<IncidentOfferEntity>> = offers.observeForIncident(incidentId)
    fun observeAllActiveOffers(): Flow<List<IncidentOfferEntity>> = offers.observeAllActiveOffers()

    /** Repair active projections whose selected offer was withdrawn or superseded. */
    internal suspend fun reconcileWithdrawnSelection(incidentId: String) = mutex.withLock {
        reconcileWithdrawnSelectionLocked(incidentId)
    }

    private suspend fun reconcileWithdrawnSelectionLocked(incidentId: String) {
        val incident = incidents.getIncidentById(incidentId) ?: return
        if (incident.workflowVersion != HELP_WORKFLOW_VERSION || incident.status in TERMINAL) return
        val offer = incident.selectionOfferId?.let { offers.getById(it) } ?: return
        if (offer.incidentId != incidentId || offer.helperKey != incident.selectedHelperKey) return
        val selectionRevision = incident.selectionOfferRevision ?: return
        val superseding = selectionHistory.supersedingOfferEvent(incident,
            OfferSelection(offer.offerId, offer.helperKey, selectionRevision))
        if (!offer.withdrawn && offer.revision <= selectionRevision && superseding == null) return
        val repaired = incident.withClearedSelection().copy(updatedAt = maxOf(incident.updatedAt,
            superseding?.timestamp ?: offer.updatedAt))
        inTransaction { incidents.insertOrUpdate(repaired) }
        val marker = if (offer.withdrawn) "WITHDRAWAL_REPAIRED" else "OFFER_SELECTION_REPAIRED"
        AppLogger.d("INCIDENT_HELP", "$marker incident=$incidentId version=${incident.version}")
    }

    suspend fun offerHelp(incidentId: String, note: String): Boolean = mutex.withLock {
        reconcileWithdrawnSelectionLocked(incidentId)
        val clean = TextSanitizer.sanitize(note, TextField.HELP_NOTE)
        if (!isValidHelpNoteLength(clean.length)) return@withLock false
        val incident = incidents.getIncidentById(incidentId) ?: return@withLock false
        if (incident.workflowVersion != HELP_WORKFLOW_VERSION || incident.status in TERMINAL) return@withLock false
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
        signApplyAndPublish(incident, EventType.INCIDENT_OFFER_UPDATED, next, payload, alreadyLocked = true)
    }

    suspend fun withdrawOffer(incidentId: String): Boolean {
        val incident = incidents.getIncidentById(incidentId) ?: return false
        val offer = offers.getByHelper(incidentId, signer.publicKey) ?: return false
        if (offer.withdrawn || incident.status in TERMINAL) return false
        return signApplyAndPublish(incident, EventType.INCIDENT_OFFER_WITHDRAWN, offer.revision + 1,
            JSONObject().put("offerId", offer.offerId).put("helperKey", signer.publicKey)
                .put("revision", offer.revision + 1))
    }

    suspend fun selectLead(incidentId: String, offerId: String): Boolean {
        reconcileWithdrawnSelection(incidentId)
        val incident = incidents.getIncidentById(incidentId) ?: return false
        if (!isLocalReporter(incident) || incident.status != IncidentState.OPEN.name) return false
        val offer = offers.getById(offerId) ?: return false
        if (offer.incidentId != incidentId || offer.withdrawn) return false
        return signApplyAndPublish(incident, EventType.INCIDENT_LEAD_SELECTED, incident.version + 1,
            JSONObject().put("selectionId", UUID.randomUUID().toString())
                .put("offerId", offer.offerId).put("offerRevision", offer.revision)
                .put("helperKey", offer.helperKey).put("helperName", offer.helperName))
    }

    suspend fun confirmLead(incidentId: String): Boolean =
        recordHelperDecision(incidentId, EventType.INCIDENT_LEAD_CONFIRMED)
    suspend fun declineLead(incidentId: String): Boolean =
        recordHelperDecision(incidentId, EventType.INCIDENT_LEAD_DECLINED)

    private suspend fun recordHelperDecision(incidentId: String, type: EventType): Boolean {
        val incident = incidents.getIncidentById(incidentId) ?: return false
        if (incident.status != IncidentState.AWAITING_HELPER.name ||
            incident.selectedHelperKey != signer.publicKey || incident.selectionId == null) return false
        return signApplyAndPublish(incident, type, incident.version,
            JSONObject().put("selectionId", incident.selectionId))
    }

    suspend fun revokeLead(incidentId: String): Boolean {
        val incident = incidents.getIncidentById(incidentId) ?: return false
        if (!isLocalReporter(incident) || incident.selectionId == null || incident.status in TERMINAL) return false
        return signApplyAndPublish(incident, EventType.INCIDENT_LEAD_REVOKED, incident.version + 1,
            JSONObject().put("selectionId", incident.selectionId))
    }

    suspend fun resolve(incidentId: String): Boolean = closeIncident(incidentId, EventType.INCIDENT_RESOLVED)

    suspend fun cancel(incidentId: String): Boolean = closeIncident(incidentId, EventType.INCIDENT_CANCELLED)

    private suspend fun closeIncident(incidentId: String, type: EventType): Boolean = mutex.withLock {
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
        val payload = JSONObject().put("closureProtocol", CLOSURE_PROTOCOL)
            .put("closureDependencies", JSONArray(dependencies))
            .put("closureSelectionId", incident.selectionId ?: "")
            .put("closureConfirmationId", commitment.firstOrNull {
                it.eventType == EventType.INCIDENT_LEAD_CONFIRMED.name &&
                    JSONObject(it.payloadJson).optString("selectionId") == incident.selectionId
            }?.eventId ?: "")
        signApplyAndPublish(incident, type, incident.version + 1, payload, alreadyLocked = true)
    }

    private suspend fun isLocalReporter(incident: IncidentEntity): Boolean =
        incident.workflowVersion == HELP_WORKFLOW_VERSION &&
            IncidentOwnership.isReporter(incident, identity.getOrCreateUser().userId, signer.publicKey)

    private suspend fun signApplyAndPublish(incident: IncidentEntity, type: EventType, revision: Long, payload: JSONObject, alreadyLocked: Boolean = false): Boolean {
        val user = identity.getOrCreateUser()
        val unsigned = DomainEventEntity(
            eventId = UUID.randomUUID().toString(), entityId = incident.incidentId,
            entityType = EntityType.INCIDENT.name, eventType = type.name,
            actorId = user.userId, actorName = user.displayName,
            logicalVersion = revision, timestamp = System.currentTimeMillis(),
            payloadJson = payload.toString()
        )
        val event = unsigned.copy(signature = signer.sign(unsigned))
        val applied = if (alreadyLocked) applyEventLocked(event) else apply(event)
        if (applied) publish(event, type == EventType.INCIDENT_LEAD_SELECTED || type == EventType.INCIDENT_CANCELLED)
        return applied
    }

    suspend fun apply(event: DomainEventEntity): Boolean = mutex.withLock { applyValidated(event) }

    internal suspend fun <T> withReplayLock(block: suspend () -> T): T = mutex.withLock { block() }
    internal suspend fun applyDuringReplay(event: DomainEventEntity): Boolean = applyValidated(event)

    private suspend fun applyValidated(event: DomainEventEntity): Boolean {
        val prior = events.getEventById(event.eventId)
        if (prior != null && (IncidentSyncSnapshot.eventHash(prior) != IncidentSyncSnapshot.eventHash(event) ||
                prior.validationStatus == EventValidationStatus.REJECTED || prior.applied)) return false
        deferredDuringApply = false
        val applied = applyEventLocked(event)
        if (!applied && !deferredDuringApply && events.getEventById(event.eventId)?.applied != true) {
            if (prior == null) events.insertEvent(event.copy(applied = false, validationStatus = EventValidationStatus.REJECTED))
            else events.setValidationStatus(event.eventId, EventValidationStatus.REJECTED)
        }
        return applied
    }

    private suspend fun applyEventLocked(event: DomainEventEntity): Boolean {
        if (event.entityType != EntityType.INCIDENT.name) return false
        val rule = rules[event.eventType] ?: return false
        reconcileWithdrawnSelectionLocked(event.entityId)
        val prior = events.getEventById(event.eventId)
        if (prior?.applied == true) return false
        if (prior != null && IncidentSyncSnapshot.eventHash(prior) != IncidentSyncSnapshot.eventHash(event)) return false
        val incident = incidents.getIncidentById(event.entityId)
        if (incident == null) {
            defer(event, prior)
            return false
        }
        if (incident.workflowVersion != HELP_WORKFLOW_VERSION) return false
        val payload = runCatching { JSONObject(event.payloadJson) }.getOrNull() ?: return false
        val offer = payload.optString("offerId").takeIf { it.isNotBlank() }?.let { offers.getById(it) }
        val context = HelpEventContext(event, incident, payload, offer, payload.optString("helperKey"))
        if (event.eventType == EventType.INCIDENT_OFFER_WITHDRAWN.name && offer == null) {
            parkWithdrawalOfUnknownOffer(context, prior)
            return false
        }
        if (event.eventType in HELPER_DECISIONS && payload.optString("selectionId") != incident.selectionId) {
            parkDecisionForOtherSelection(context, prior)
            return false
        }
        val actorKey = rule.signerKey(context)
        if (actorKey.isNullOrBlank() || !signer.verify(event, actorKey) || !rule.acceptsSigner(context, actorKey)) {
            if (prior != null) events.setValidationStatus(event.eventId, EventValidationStatus.REJECTED)
            return false
        }
        if (incident.status in TERMINAL) {
            retainLateEvent(context, prior, actorKey)
            return false
        }
        return commitOutcome(event, prior, rule.evaluate(context))
    }

    private suspend fun commitOutcome(event: DomainEventEntity, prior: DomainEventEntity?,
        outcome: HelpEventOutcome): Boolean =
        when (outcome) {
            HelpEventOutcome.Rejected -> false
            is HelpEventOutcome.Deferred -> {
                defer(event, prior, verified = true)
                outcome.logMessage?.let { AppLogger.d("INCIDENT_HELP", it) }
                false
            }
            is HelpEventOutcome.Accepted -> {
                inTransaction {
                    outcome.offer?.let { offers.upsert(it) }
                    outcome.incident?.let { incidents.insertOrUpdate(it) }
                    recordAccepted(event, prior)
                }
                outcome.logMessage?.let { AppLogger.d("INCIDENT_HELP", it) }
                true
            }
        }

    private suspend fun defer(event: DomainEventEntity, prior: DomainEventEntity?, verified: Boolean = false) {
        deferredDuringApply = true
        val status = if (verified) EventValidationStatus.ACCEPTED else EventValidationStatus.UNVERIFIED
        if (prior == null) events.insertEvent(event.copy(applied = false, validationStatus = status))
        else events.setValidationStatus(event.eventId, status)
    }

    private suspend fun recordAccepted(event: DomainEventEntity, prior: DomainEventEntity?) {
        val accepted = EventValidationStatus.ACCEPTED
        if (prior == null) events.insertEvent(event.copy(applied = true, validationStatus = accepted))
        else { events.setApplied(event.eventId, true); events.setValidationStatus(event.eventId, accepted) }
    }

    private suspend fun inTransaction(block: suspend () -> Unit) {
        if (database != null) database.withTransaction { block() } else block()
    }

    private suspend fun parkWithdrawalOfUnknownOffer(context: HelpEventContext, prior: DomainEventEntity?) {
        val historicalSelection = selectionHistory.selectionEvent(context.incident,
            context.payload.optString("selectionId"))
        if (historicalSelection != null &&
            selectionHistory.selectionSupersededBy(context.incident, historicalSelection) != null) {
            retainHistoricalDecision(context.event, context.incident, historicalSelection)
            return
        }
        if (!context.event.signature.isNullOrBlank()) defer(context.event, prior)
    }

    /**
     * A decision for a selection this replica no longer holds is history once that selection
     * is known, and waits for it otherwise. Rejecting it would split replica histories forever.
     * Unknown selections wait only for decisions signed by an offering helper; others are rejected,
     * and a rejected event is re-validated if it arrives again.
     */
    private suspend fun parkDecisionForOtherSelection(context: HelpEventContext, prior: DomainEventEntity?) {
        val selectionId = context.payload.optString("selectionId")
        if (selectionId.isBlank()) return
        val historicalSelection = selectionHistory.selectionEvent(context.incident, selectionId)
        if (historicalSelection != null) retainHistoricalDecision(context.event, context.incident, historicalSelection)
        else if (offers.getForIncident(context.incident.incidentId).any { signer.verify(context.event, it.helperKey) })
            defer(context.event, prior)
    }

    /** Valid late helper decisions remain immutable history; they never reopen terminal state. */
    private suspend fun retainLateEvent(context: HelpEventContext, prior: DomainEventEntity?, actorKey: String) {
        val event = context.event
        val incident = context.incident
        val payload = context.payload
        val offer = context.offer
        val frontier = events.getEventsForEntity(event.entityId).filter {
            it.applied && it.validationStatus == EventValidationStatus.ACCEPTED && it.eventType in OFFER_TYPES &&
                runCatching { JSONObject(it.payloadJson).optString("offerId") == payload.optString("offerId") }.getOrDefault(false)
        }.maxOfOrNull { it.logicalVersion } ?: (offer?.revision ?: 0L)
        val byHelper = offers.getByHelper(event.entityId, actorKey)
        val selection = selectionHistory.selectionEvent(incident, payload.optString("selectionId"))
        if (event.eventType in HELPER_DECISIONS && selection != null) {
            retainHistoricalDecision(event, incident, selection)
            return
        }
        val offerId = payload.optString("offerId")
        val fromHelper = event.eventType in OFFER_TYPES && event.actorId != incident.creatorId
        val wellFormed = offerId.isNotBlank() && payload.optLong("revision") == event.logicalVersion
        val noteOk = event.eventType != EventType.INCIDENT_OFFER_UPDATED.name ||
            isValidHelpNoteLength(payload.optString("note").trim().length)
        val ownedByActor = (offer == null || offer.helperKey == actorKey) &&
            (byHelper == null || byHelper.offerId == offerId)
        if (!fromHelper || !wellFormed || !noteOk || !ownedByActor) return
        if (event.logicalVersion > frontier + 1L) defer(event, prior, verified = true)
        else if (event.logicalVersion == frontier + 1L) recordAccepted(event, prior)
    }

    private suspend fun retainHistoricalDecision(event: DomainEventEntity, incident: IncidentEntity,
        selection: DomainEventEntity) {
        if (event.eventType !in HELPER_DECISIONS || event.actorId == incident.creatorId ||
            event.logicalVersion != selection.logicalVersion) return
        val key = JSONObject(selection.payloadJson).optString("helperKey")
        if (key.isBlank() || !signer.verify(event, key)) return
        recordAccepted(event, events.getEventById(event.eventId))
    }

    companion object {
        /** Value of the "closureProtocol" payload field on signed closures. */
        internal const val CLOSURE_PROTOCOL = 1
        private val TERMINAL = setOf(IncidentState.RESOLVED.name, IncidentState.CANCELLED.name)
        internal val OFFER_TYPES = setOf(EventType.INCIDENT_OFFER_UPDATED.name, EventType.INCIDENT_OFFER_WITHDRAWN.name)
        private val HELPER_DECISIONS = setOf(EventType.INCIDENT_LEAD_CONFIRMED.name, EventType.INCIDENT_LEAD_DECLINED.name)
        private val REPORTER_TYPES = setOf(EventType.INCIDENT_LEAD_SELECTED.name, EventType.INCIDENT_LEAD_REVOKED.name)
        val TYPES = setOf(EventType.INCIDENT_OFFER_UPDATED.name, EventType.INCIDENT_OFFER_WITHDRAWN.name,
            EventType.INCIDENT_LEAD_SELECTED.name, EventType.INCIDENT_LEAD_CONFIRMED.name,
            EventType.INCIDENT_LEAD_DECLINED.name, EventType.INCIDENT_LEAD_REVOKED.name)
    }
}
