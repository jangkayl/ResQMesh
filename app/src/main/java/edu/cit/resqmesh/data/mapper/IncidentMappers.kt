package edu.cit.resqmesh.data.mapper

import edu.cit.resqmesh.core.model.DomainEvent
import edu.cit.resqmesh.core.model.Incident
import edu.cit.resqmesh.core.model.IncidentOffer
import edu.cit.resqmesh.core.security.input.TextField
import edu.cit.resqmesh.core.security.input.TextSanitizer
import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import edu.cit.resqmesh.data.local.entity.IncidentEntity
import edu.cit.resqmesh.data.local.entity.IncidentOfferEntity

fun IncidentEntity.toModel(): Incident = Incident(
    incidentId = incidentId,
    creatorId = creatorId,
    creatorName = creatorName,
    incidentType = incidentType,
    severity = severity,
    description = description,
    areaDescription = areaDescription,
    latitude = latitude,
    longitude = longitude,
    locationCapturedAt = locationCapturedAt,
    locationAccuracyMeters = locationAccuracyMeters,
    status = status,
    primaryResponderId = primaryResponderId,
    primaryResponderName = primaryResponderName,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt,
    workflowVersion = workflowVersion,
    reporterSigningKey = reporterSigningKey,
    selectionId = selectionId,
    selectionOfferId = selectionOfferId,
    selectionOfferRevision = selectionOfferRevision,
    selectedHelperKey = selectedHelperKey,
    selectionConfirmedAt = selectionConfirmedAt,
    title = title
)

/**
 * UI copy of a stored incident with peer-supplied text cleaned. Stored columns stay raw because
 * IncidentSyncSnapshot hashes them; the result must never be written back, signed, hashed or relayed.
 */
fun IncidentEntity.toDisplayModel(): Incident = toModel().let {
    it.copy(
        creatorName = TextSanitizer.inboundPeerName(it.creatorName),
        primaryResponderName = it.primaryResponderName?.let(TextSanitizer::inboundPeerName),
        title = TextSanitizer.inbound(it.title, TextField.INCIDENT_TITLE),
        description = TextSanitizer.inbound(it.description, TextField.INCIDENT_DESCRIPTION),
        areaDescription = TextSanitizer.inbound(it.areaDescription, TextField.INCIDENT_AREA)
    )
}

fun Incident.toEntity(): IncidentEntity = IncidentEntity(
    incidentId = incidentId,
    creatorId = creatorId,
    creatorName = creatorName,
    incidentType = incidentType,
    severity = severity,
    description = description,
    areaDescription = areaDescription,
    latitude = latitude,
    longitude = longitude,
    locationCapturedAt = locationCapturedAt,
    locationAccuracyMeters = locationAccuracyMeters,
    status = status,
    primaryResponderId = primaryResponderId,
    primaryResponderName = primaryResponderName,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt,
    workflowVersion = workflowVersion,
    reporterSigningKey = reporterSigningKey,
    selectionId = selectionId,
    selectionOfferId = selectionOfferId,
    selectionOfferRevision = selectionOfferRevision,
    selectedHelperKey = selectedHelperKey,
    selectionConfirmedAt = selectionConfirmedAt,
    title = title
)

fun IncidentOfferEntity.toModel(): IncidentOffer = IncidentOffer(
    offerId = offerId,
    incidentId = incidentId,
    helperKey = helperKey,
    helperNodeId = helperNodeId,
    helperName = helperName,
    note = note,
    revision = revision,
    withdrawn = withdrawn,
    updatedAt = updatedAt
)

/** UI copy of a stored offer with peer-supplied text cleaned; never persist, sign, hash or relay it. */
fun IncidentOfferEntity.toDisplayModel(): IncidentOffer = toModel().let {
    it.copy(
        helperName = TextSanitizer.inboundPeerName(it.helperName),
        note = TextSanitizer.inbound(it.note, TextField.HELP_NOTE)
    )
}

fun IncidentOffer.toEntity(): IncidentOfferEntity = IncidentOfferEntity(
    offerId = offerId,
    incidentId = incidentId,
    helperKey = helperKey,
    helperNodeId = helperNodeId,
    helperName = helperName,
    note = note,
    revision = revision,
    withdrawn = withdrawn,
    updatedAt = updatedAt
)

fun DomainEventEntity.toModel(): DomainEvent = DomainEvent(
    eventId = eventId,
    entityId = entityId,
    entityType = entityType,
    eventType = eventType,
    actorId = actorId,
    actorName = actorName,
    logicalVersion = logicalVersion,
    timestamp = timestamp,
    payloadJson = payloadJson,
    signature = signature,
    applied = applied,
    validationStatus = validationStatus
)

/** UI copy of a stored event with the actor name cleaned; never persist, verify, hash or relay it. */
fun DomainEventEntity.toDisplayModel(): DomainEvent =
    toModel().let { it.copy(actorName = TextSanitizer.inboundPeerName(it.actorName)) }

fun DomainEvent.toEntity(): DomainEventEntity = DomainEventEntity(
    eventId = eventId,
    entityId = entityId,
    entityType = entityType,
    eventType = eventType,
    actorId = actorId,
    actorName = actorName,
    logicalVersion = logicalVersion,
    timestamp = timestamp,
    payloadJson = payloadJson,
    signature = signature,
    applied = applied,
    validationStatus = validationStatus
)
