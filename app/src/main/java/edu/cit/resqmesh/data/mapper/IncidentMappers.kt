package edu.cit.resqmesh.data.mapper

import edu.cit.resqmesh.core.model.DomainEvent
import edu.cit.resqmesh.core.model.Incident
import edu.cit.resqmesh.core.model.IncidentOffer
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
