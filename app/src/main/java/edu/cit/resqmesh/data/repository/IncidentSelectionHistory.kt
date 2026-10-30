package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.model.EventType
import edu.cit.resqmesh.core.model.IncidentState
import edu.cit.resqmesh.data.local.dao.DomainEventDao
import edu.cit.resqmesh.data.local.dao.IncidentOfferDao
import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import edu.cit.resqmesh.data.local.entity.IncidentEntity
import org.json.JSONObject

/** The helper offer revision a reporter selected. */
internal data class OfferSelection(val offerId: String, val helperKey: String, val revision: Long)

internal fun IncidentEntity.withClearedSelection() = copy(
    status = IncidentState.OPEN.name, selectionId = null, selectionOfferId = null,
    selectionOfferRevision = null, selectedHelperKey = null, selectionConfirmedAt = null,
    primaryResponderId = null, primaryResponderName = null)

/** Read-only queries over one incident's signed selection history. */
internal class IncidentSelectionHistory(
    private val events: DomainEventDao,
    private val offers: IncidentOfferDao,
    private val signer: IncidentEventSigning
) {
    suspend fun selectedOfferIsWithdrawn(incident: IncidentEntity): Boolean =
        incident.selectionOfferId?.let { id ->
            offers.getById(id)?.let { it.withdrawn || it.revision != incident.selectionOfferRevision }
        } != false

    suspend fun closureProjection(incident: IncidentEntity, payload: JSONObject): IncidentEntity? {
        val selectionId = payload.opt("closureSelectionId") as? String ?: return null
        val confirmationId = payload.opt("closureConfirmationId") as? String ?: return null
        if (selectionId.isEmpty()) return if (confirmationId.isEmpty()) incident.withClearedSelection() else null
        val dependencies = payload.getJSONArray("closureDependencies")
        val ids = (0 until dependencies.length()).map { dependencies.getString(it) }.toSet()
        val selection = selectionEvent(incident, selectionId) ?: return null
        if (selection.eventId !in ids || selection.logicalVersion != incident.version) return null
        val selected = JSONObject(selection.payloadJson)
        val observed = OfferSelection(selected.optString("offerId"), selected.optString("helperKey"),
            selected.optLong("offerRevision"))
        val history = events.getEventsForEntity(incident.incidentId).filter { it.applied }
        val offered = selectedOfferEvent(history, observed) ?: return null
        if (hasNewerObservedRevision(history, ids, observed)) return null
        val confirmation = if (confirmationId.isEmpty()) null else {
            closureConfirmation(incident, confirmationId, ids, selectionId, observed.helperKey) ?: return null
        }
        return incident.copy(status = if (confirmation == null) IncidentState.AWAITING_HELPER.name else IncidentState.RESPONDING.name,
            selectionId = selectionId, selectionOfferId = observed.offerId, selectionOfferRevision = observed.revision,
            selectedHelperKey = observed.helperKey, primaryResponderName = offered.actorName,
            primaryResponderId = confirmation?.actorId, selectionConfirmedAt = confirmation?.timestamp)
    }

    private fun selectedOfferEvent(history: List<DomainEventEntity>, selection: OfferSelection) =
        history.firstOrNull { it.eventType == EventType.INCIDENT_OFFER_UPDATED.name &&
            it.logicalVersion == selection.revision && signer.verify(it, selection.helperKey) &&
            JSONObject(it.payloadJson).let { p ->
                p.optString("offerId") == selection.offerId && p.optString("helperKey") == selection.helperKey
            }
        }

    private fun hasNewerObservedRevision(history: List<DomainEventEntity>, observedIds: Set<String>,
        selection: OfferSelection) =
        history.any { it.eventId in observedIds && it.eventType in IncidentHelpWorkflow.OFFER_TYPES &&
            it.logicalVersion > selection.revision &&
            JSONObject(it.payloadJson).optString("offerId") == selection.offerId }

    private suspend fun closureConfirmation(incident: IncidentEntity, confirmationId: String,
        observedIds: Set<String>, selectionId: String, helperKey: String): DomainEventEntity? =
        events.getEventById(confirmationId)?.takeIf {
            it.eventId in observedIds && it.applied && it.entityId == incident.incidentId &&
                it.eventType == EventType.INCIDENT_LEAD_CONFIRMED.name && it.logicalVersion == incident.version &&
                signer.verify(it, helperKey) && JSONObject(it.payloadJson).optString("selectionId") == selectionId
        }

    suspend fun selectionEvent(incident: IncidentEntity, selectionId: String): DomainEventEntity? {
        if (selectionId.isBlank()) return null
        val reporterKey = incident.reporterSigningKey ?: return null
        return events.getEventsForEntity(incident.incidentId).firstOrNull { event ->
            event.applied && event.eventType == EventType.INCIDENT_LEAD_SELECTED.name &&
                event.actorId == incident.creatorId && signer.verify(event, reporterKey) &&
                runCatching { JSONObject(event.payloadJson).optString("selectionId") == selectionId }.getOrDefault(false)
        }
    }

    suspend fun selectionSupersededBy(incident: IncidentEntity, selection: DomainEventEntity): DomainEventEntity? {
        val payload = runCatching { JSONObject(selection.payloadJson) }.getOrNull() ?: return null
        return supersedingOfferEvent(incident, OfferSelection(payload.optString("offerId"),
            payload.optString("helperKey"), payload.optLong("offerRevision")))
    }

    suspend fun supersedingOfferEvent(incident: IncidentEntity, selection: OfferSelection): DomainEventEntity? {
        if (selection.offerId.isBlank() || selection.helperKey.isBlank() || selection.revision < 1) return null
        return events.getEventsForEntity(incident.incidentId).firstOrNull { event ->
            event.applied && event.eventType in IncidentHelpWorkflow.OFFER_TYPES &&
                event.logicalVersion > selection.revision && signer.verify(event, selection.helperKey) &&
                runCatching {
                    val payload = JSONObject(event.payloadJson)
                    payload.optString("offerId") == selection.offerId &&
                        payload.optString("helperKey") == selection.helperKey &&
                        payload.optLong("revision") == event.logicalVersion
                }.getOrDefault(false)
        }
    }
}
