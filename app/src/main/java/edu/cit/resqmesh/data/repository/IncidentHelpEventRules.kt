package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.model.EntityType
import edu.cit.resqmesh.core.model.EventType
import edu.cit.resqmesh.core.model.IncidentState
import edu.cit.resqmesh.core.security.input.TextField
import edu.cit.resqmesh.data.local.dao.DomainEventDao
import edu.cit.resqmesh.data.local.dao.IncidentOfferDao
import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import edu.cit.resqmesh.data.local.entity.IncidentEntity
import edu.cit.resqmesh.data.local.entity.IncidentOfferEntity
import org.json.JSONObject

/** One signed help event together with the replica state it is judged against. */
internal class HelpEventContext(
    val event: DomainEventEntity,
    val incident: IncidentEntity,
    val payload: JSONObject,
    val offer: IncidentOfferEntity?,
    val helperKey: String
)

internal sealed interface HelpEventOutcome {
    data object Rejected : HelpEventOutcome
    /** Verified, but a prerequisite has not reached this replica yet. */
    data class Deferred(val logMessage: String? = null) : HelpEventOutcome
    data class Accepted(
        val incident: IncidentEntity? = null,
        val offer: IncidentOfferEntity? = null,
        val logMessage: String? = null
    ) : HelpEventOutcome
}

/** Authorizes and evaluates one event type of the version-2 help workflow. */
internal sealed class HelpEventRule {
    /** The only key allowed to have signed this event. */
    abstract fun signerKey(context: HelpEventContext): String?
    open fun acceptsSigner(context: HelpEventContext, key: String): Boolean = true
    abstract suspend fun evaluate(context: HelpEventContext): HelpEventOutcome

    companion object {
        fun all(offers: IncidentOfferDao, events: DomainEventDao, history: IncidentSelectionHistory):
            Map<String, HelpEventRule> = mapOf(
            EventType.INCIDENT_OFFER_UPDATED.name to OfferUpdatedRule(offers),
            EventType.INCIDENT_OFFER_WITHDRAWN.name to OfferWithdrawnRule,
            EventType.INCIDENT_LEAD_SELECTED.name to LeadSelectedRule(history),
            EventType.INCIDENT_LEAD_CONFIRMED.name to LeadConfirmedRule(history),
            EventType.INCIDENT_LEAD_DECLINED.name to LeadDeclinedRule(history),
            EventType.INCIDENT_LEAD_REVOKED.name to LeadRevokedRule(history),
            EventType.INCIDENT_RESOLVED.name to IncidentResolvedRule(events, history),
            EventType.INCIDENT_CANCELLED.name to IncidentCancelledRule(events, history)
        )
    }
}

internal fun isValidHelpNoteLength(length: Int) = length in 1..TextField.HELP_NOTE.maxLength

private fun clearedAt(incident: IncidentEntity, timestamp: Long) =
    incident.withClearedSelection().copy(updatedAt = maxOf(incident.updatedAt, timestamp))

private class OfferUpdatedRule(private val offers: IncidentOfferDao) : HelpEventRule() {
    override fun signerKey(context: HelpEventContext) = context.helperKey

    override suspend fun evaluate(context: HelpEventContext): HelpEventOutcome {
        val event = context.event
        val incident = context.incident
        val offer = context.offer
        val helperKey = context.helperKey
        val offerId = context.payload.optString("offerId")
        val note = context.payload.optString("note").trim()
        val revision = context.payload.optLong("revision")
        val byHelper = offers.getByHelper(event.entityId, helperKey)
        val malformed = offerId.isBlank() || !isValidHelpNoteLength(note.length) || revision < 1
        val foreignOffer = (offer != null && offer.helperKey != helperKey) ||
            (byHelper != null && byHelper.offerId != offerId)
        val versionMismatch = event.logicalVersion != revision || event.actorId == incident.creatorId
        if (malformed || foreignOffer || versionMismatch) return HelpEventOutcome.Rejected
        val expected = (byHelper?.revision ?: 0L) + 1L
        if (revision > expected) return HelpEventOutcome.Deferred()
        if (revision != expected) return HelpEventOutcome.Rejected
        val updatedOffer = IncidentOfferEntity(offerId, event.entityId, helperKey,
            context.payload.optString("helperNodeId"), event.actorName,
            note, revision, false, event.timestamp)
        val clearsSelection = incident.selectionOfferId == offerId && incident.selectedHelperKey == helperKey &&
            revision > (incident.selectionOfferRevision ?: 0L)
        if (!clearsSelection) return HelpEventOutcome.Accepted(offer = updatedOffer)
        return HelpEventOutcome.Accepted(clearedAt(incident, event.timestamp), updatedOffer,
            "OFFER_REVISION_CLEARED_SELECTION incident=${event.entityId} version=${incident.version}")
    }
}

private object OfferWithdrawnRule : HelpEventRule() {
    override fun signerKey(context: HelpEventContext) = context.offer?.helperKey
    override fun acceptsSigner(context: HelpEventContext, key: String) = context.helperKey == key

    override suspend fun evaluate(context: HelpEventContext): HelpEventOutcome {
        val event = context.event
        val incident = context.incident
        val offer = context.offer
        if (offer == null || offer.incidentId != event.entityId || offer.withdrawn ||
            context.payload.optLong("revision") != event.logicalVersion) return HelpEventOutcome.Rejected
        if (event.logicalVersion > offer.revision + 1) return HelpEventOutcome.Deferred()
        if (event.logicalVersion != offer.revision + 1) return HelpEventOutcome.Rejected
        val updatedOffer = offer.copy(revision = event.logicalVersion, withdrawn = true, updatedAt = event.timestamp)
        val clearsSelection = incident.selectionOfferId == offer.offerId &&
            incident.selectedHelperKey == offer.helperKey
        if (!clearsSelection) return HelpEventOutcome.Accepted(offer = updatedOffer)
        return HelpEventOutcome.Accepted(clearedAt(incident, event.timestamp), updatedOffer,
            "WITHDRAWAL_CLEARED_SELECTION incident=${event.entityId} version=${incident.version}")
    }
}

private class LeadSelectedRule(private val history: IncidentSelectionHistory) : HelpEventRule() {
    override fun signerKey(context: HelpEventContext) = context.incident.reporterSigningKey

    override suspend fun evaluate(context: HelpEventContext): HelpEventOutcome {
        val event = context.event
        val incident = context.incident
        val payload = context.payload
        val offer = context.offer
        val offerRevision = payload.optLong("offerRevision")
        if (event.actorId != incident.creatorId || payload.optString("selectionId").isBlank())
            return HelpEventOutcome.Rejected
        if (event.logicalVersion > incident.version + 1 ||
            (event.logicalVersion > incident.version && incident.status != IncidentState.OPEN.name) ||
            (event.logicalVersion == incident.version + 1 && (offer == null || offer.revision < offerRevision)))
            return HelpEventOutcome.Deferred()
        if (incident.status != IncidentState.OPEN.name ||
            event.logicalVersion != incident.version + 1 || offer == null ||
            offer.incidentId != event.entityId || offerRevision < 1 ||
            offer.helperKey != context.helperKey) return HelpEventOutcome.Rejected
        val superseded = history.supersedingOfferEvent(incident,
            OfferSelection(offer.offerId, offer.helperKey, offerRevision)) != null
        if (superseded) {
            // Consume the reporter's version even when a newer offer revision reached this replica first.
            return HelpEventOutcome.Accepted(
                incident.withClearedSelection().copy(version = event.logicalVersion,
                    updatedAt = maxOf(incident.updatedAt, event.timestamp)),
                logMessage = "SUPERSEDED_SELECTION_SKIPPED incident=${event.entityId} version=${event.logicalVersion}")
        }
        if (offer.withdrawn || offer.revision != offerRevision) return HelpEventOutcome.Rejected
        return HelpEventOutcome.Accepted(incident.copy(status = IncidentState.AWAITING_HELPER.name,
            version = event.logicalVersion, updatedAt = event.timestamp,
            selectionId = payload.getString("selectionId"), selectionOfferId = offer.offerId,
            selectionOfferRevision = offer.revision, selectedHelperKey = offer.helperKey,
            primaryResponderId = null, primaryResponderName = offer.helperName,
            selectionConfirmedAt = null))
    }
}

private abstract class HelperDecisionRule(private val history: IncidentSelectionHistory) : HelpEventRule() {
    protected abstract fun decide(context: HelpEventContext): IncidentEntity

    override fun signerKey(context: HelpEventContext) = context.incident.selectedHelperKey

    override suspend fun evaluate(context: HelpEventContext): HelpEventOutcome {
        val incident = context.incident
        if (incident.status != IncidentState.AWAITING_HELPER.name ||
            incident.selectionId != context.payload.optString("selectionId") ||
            context.event.logicalVersion != incident.version ||
            history.selectedOfferIsWithdrawn(incident)) return HelpEventOutcome.Rejected
        return HelpEventOutcome.Accepted(decide(context))
    }
}

private class LeadConfirmedRule(history: IncidentSelectionHistory) : HelperDecisionRule(history) {
    override fun decide(context: HelpEventContext) = context.incident.copy(
        status = IncidentState.RESPONDING.name, selectionConfirmedAt = context.event.timestamp,
        primaryResponderId = context.event.actorId, updatedAt = context.event.timestamp)
}

private class LeadDeclinedRule(history: IncidentSelectionHistory) : HelperDecisionRule(history) {
    override fun decide(context: HelpEventContext) = context.incident.withClearedSelection()
}

private class LeadRevokedRule(private val history: IncidentSelectionHistory) : HelpEventRule() {
    override fun signerKey(context: HelpEventContext) = context.incident.reporterSigningKey

    override suspend fun evaluate(context: HelpEventContext): HelpEventOutcome {
        val event = context.event
        val incident = context.incident
        if (event.actorId != incident.creatorId) return HelpEventOutcome.Rejected
        if (event.logicalVersion > incident.version + 1) return HelpEventOutcome.Deferred()
        if (event.logicalVersion != incident.version + 1) return HelpEventOutcome.Rejected
        val target = context.payload.optString("selectionId")
        if (incident.selectionId == target && target.isNotBlank()) {
            return HelpEventOutcome.Accepted(incident.withClearedSelection()
                .copy(version = event.logicalVersion, updatedAt = event.timestamp))
        }
        // A reporter can revoke after offer invalidation or a concurrent helper decline;
        // the revocation still consumes its version, and any newer selection is retained.
        history.selectionEvent(incident, target) ?: return HelpEventOutcome.Rejected
        return HelpEventOutcome.Accepted(incident.copy(version = event.logicalVersion,
            updatedAt = maxOf(incident.updatedAt, event.timestamp)))
    }
}

private abstract class IncidentClosureRule(
    private val events: DomainEventDao,
    private val history: IncidentSelectionHistory
) : HelpEventRule() {
    protected abstract val closedStatus: IncidentState
    protected open fun canClose(incident: IncidentEntity): Boolean = true

    override fun signerKey(context: HelpEventContext) = context.incident.reporterSigningKey

    override suspend fun evaluate(context: HelpEventContext): HelpEventOutcome {
        val event = context.event
        val incident = context.incident
        val payload = context.payload
        if (event.actorId != incident.creatorId) return HelpEventOutcome.Rejected
        var closingIncident = incident
        if (payload.has("closureProtocol") || payload.has("closureDependencies")) {
            unmetClosureDependency(context)?.let { return it }
            // Freeze only the helper history that the reporter observed. A newer
            // offline offer revision may already have cleared this replica's selection.
            closingIncident = history.closureProjection(incident, payload) ?: return HelpEventOutcome.Rejected
        }
        if (event.logicalVersion > incident.version + 1) return HelpEventOutcome.Deferred()
        if (event.logicalVersion != incident.version + 1 || !canClose(closingIncident))
            return HelpEventOutcome.Rejected
        return HelpEventOutcome.Accepted(closingIncident.copy(status = closedStatus.name,
            version = event.logicalVersion, updatedAt = event.timestamp))
    }

    /** Null when every dependency the reporter observed has been applied on this replica. */
    private suspend fun unmetClosureDependency(context: HelpEventContext): HelpEventOutcome? {
        val event = context.event
        if (context.payload.optInt("closureProtocol") != IncidentHelpWorkflow.CLOSURE_PROTOCOL)
            return HelpEventOutcome.Rejected
        val dependencies = context.payload.optJSONArray("closureDependencies") ?: return HelpEventOutcome.Rejected
        for (i in 0 until dependencies.length()) {
            val id = dependencies.opt(i) as? String ?: return HelpEventOutcome.Rejected
            if (id.isBlank() || id == event.eventId) return HelpEventOutcome.Rejected
            val prerequisite = events.getEventById(id)
            if (prerequisite != null && (prerequisite.entityId != event.entityId ||
                    prerequisite.entityType != EntityType.INCIDENT.name ||
                    prerequisite.eventType !in IncidentHelpWorkflow.TYPES)) return HelpEventOutcome.Rejected
            if (prerequisite?.applied != true) {
                return HelpEventOutcome.Deferred("CLOSURE_WAITING_DEPENDENCIES incident=${event.entityId}")
            }
        }
        return null
    }
}

private class IncidentResolvedRule(events: DomainEventDao, history: IncidentSelectionHistory) :
    IncidentClosureRule(events, history) {
    override val closedStatus = IncidentState.RESOLVED
    override fun canClose(incident: IncidentEntity) = incident.status == IncidentState.RESPONDING.name
}

private class IncidentCancelledRule(events: DomainEventDao, history: IncidentSelectionHistory) :
    IncidentClosureRule(events, history) {
    override val closedStatus = IncidentState.CANCELLED
}
