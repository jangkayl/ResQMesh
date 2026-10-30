package edu.cit.resqmesh.data.repository.incident

import edu.cit.resqmesh.core.model.EntityType
import edu.cit.resqmesh.core.model.EventType
import edu.cit.resqmesh.core.model.HELP_WORKFLOW_VERSION
import edu.cit.resqmesh.core.model.IncidentState
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.security.input.TextField
import edu.cit.resqmesh.data.local.dao.DomainEventDao
import edu.cit.resqmesh.data.local.dao.IncidentDao
import edu.cit.resqmesh.data.local.AppDatabase
import androidx.room.withTransaction
import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import edu.cit.resqmesh.data.local.entity.EventValidationStatus
import edu.cit.resqmesh.data.local.entity.IncidentEntity
import kotlinx.serialization.ExperimentalSerializationApi
import org.json.JSONObject
import edu.cit.resqmesh.data.repository.IncidentEventSigning
import edu.cit.resqmesh.data.repository.IncidentPolicy

@OptIn(ExperimentalSerializationApi::class)
internal class LegacyIncidentProjection(
    private val incidentDao: IncidentDao,
    private val domainEventDao: DomainEventDao,
    private val database: AppDatabase?,
    private val eventSigning: IncidentEventSigning?
) {
    /** How a projected event ended: settled already, or still to be recorded by the common tail. */
    private sealed interface Projection {
        data class Settled(val applied: Boolean) : Projection
        data class Record(val applied: Boolean) : Projection
    }

    suspend fun applyLegacyProjection(event: DomainEventEntity): Boolean {
        // Deduplication: Has this exact business event been recorded already?
        if (domainEventDao.getEventById(event.eventId)?.applied == true) {
            AppLogger.d("INCIDENT_REPO", "Deduplication: Event ${event.eventId} already processed, skipping.")
            return false
        }

        if (event.entityType != EntityType.INCIDENT.name) {
            return rejectIncoming(event, "not an incident event")
        }

        val current = incidentDao.getIncidentById(event.entityId)
        val isCompetingAssignment = event.eventType == EventType.INCIDENT_ASSIGNED.name &&
            current?.status == IncidentState.ASSIGNED.name && event.logicalVersion == current.version
        if (event.eventType == EventType.INCIDENT_CREATED.name && event.logicalVersion != 1L) {
            domainEventDao.insertEvent(event.copy(applied = false, validationStatus = EventValidationStatus.REJECTED))
            return false
        }
        if (event.eventType != EventType.INCIDENT_CREATED.name && !isCompetingAssignment) {
            if (current == null || event.logicalVersion > current.version + 1L) {
                domainEventDao.insertEvent(event.copy(applied = false, validationStatus = EventValidationStatus.UNVERIFIED))
                return false
            }
            if (!IncidentPolicy.mayPerform(current, event.eventType, event.actorId) || event.logicalVersion != current.version + 1L)
                return rejectIncoming(event, "invalid legacy authority or version")
        }
        return when (val projection = project(event, current)) {
            is Projection.Settled -> projection.applied
            is Projection.Record -> recordOutcome(event, projection.applied)
        }
    }

    private suspend fun recordOutcome(event: DomainEventEntity, applied: Boolean): Boolean {
        domainEventDao.insertEvent(event.copy(applied = applied))
        domainEventDao.setApplied(event.eventId, applied)
        domainEventDao.setValidationStatus(event.eventId,
            if (applied) EventValidationStatus.ACCEPTED else EventValidationStatus.REJECTED)
        return applied
    }

    private suspend fun project(event: DomainEventEntity, current: IncidentEntity?): Projection {
        if (event.eventType == EventType.INCIDENT_CREATED.name) return projectCreation(event, current)
        if (current == null) return Projection.Record(false)
        if (event.eventType == EventType.INCIDENT_ASSIGNED.name) return projectAssignment(event, current)
        if (event.eventType == EventType.INCIDENT_ASSIGNMENT_RELEASED.name) {
            incidentDao.insertOrUpdate(current.copy(
                status = IncidentState.ACKNOWLEDGED.name,
                primaryResponderId = null,
                primaryResponderName = null,
                version = event.logicalVersion,
                updatedAt = event.timestamp
            ))
            return Projection.Record(true)
        }
        val status = STATUS_TRANSITIONS[event.eventType] ?: return Projection.Record(false)
        incidentDao.insertOrUpdate(current.copy(
            status = status.name,
            version = event.logicalVersion,
            updatedAt = event.timestamp
        ))
        return Projection.Record(true)
    }

    private suspend fun projectCreation(event: DomainEventEntity, current: IncidentEntity?): Projection {
        if (current != null) {
            AppLogger.d("INCIDENT_REPO", "Incident ${event.entityId} already exists, ignoring creation.")
            return Projection.Record(false)
        }
        val p = JSONObject(event.payloadJson)
        val workflowVersion = p.optInt("workflowVersion", 1)
        val reporterKey = p.optString("reporterSigningKey").takeIf { it.isNotBlank() }
        if (workflowVersion == HELP_WORKFLOW_VERSION && (reporterKey == null ||
                eventSigning?.verify(event, reporterKey) != true)) {
            return Projection.Settled(rejectIncoming(event, "invalid reporter identity"))
        }
        val raw = if (p.has("title")) p.opt("title") else null
        if (raw != null && raw != JSONObject.NULL && raw !is String) {
            return Projection.Settled(rejectIncoming(event, "title must be a text string"))
        }
        val incomingTitle = (raw as? String)?.trim().orEmpty()
        if (incomingTitle.length > TextField.INCIDENT_TITLE.maxLength) {
            return Projection.Settled(rejectIncoming(event, "title cannot exceed 80 characters"))
        }
        val newIncident = IncidentEntity(
            incidentId = event.entityId,
            creatorId = event.actorId,
            creatorName = event.actorName,
            incidentType = p.optString("incidentType", "Emergency"),
            severity = p.optString("severity", "Critical"),
            description = p.optString("description", ""),
            areaDescription = p.optString("areaDescription", ""),
            latitude = p.optionalDouble("latitude"),
            longitude = p.optionalDouble("longitude"),
            locationCapturedAt = p.optionalLong("locationCapturedAt"),
            locationAccuracyMeters = p.optionalFloat("locationAccuracyMeters"),
            status = IncidentState.OPEN.name,
            primaryResponderId = null,
            primaryResponderName = null,
            version = event.logicalVersion,
            createdAt = event.timestamp,
            updatedAt = event.timestamp,
            workflowVersion = workflowVersion,
            reporterSigningKey = reporterKey,
            title = incomingTitle
        )
        if (workflowVersion == HELP_WORKFLOW_VERSION && database != null) {
            database.withTransaction {
                incidentDao.insertOrUpdate(newIncident)
                domainEventDao.insertEvent(event.copy(applied = true))
                domainEventDao.setApplied(event.eventId, true)
                domainEventDao.setValidationStatus(event.eventId, EventValidationStatus.ACCEPTED)
            }
            return Projection.Settled(true)
        }
        incidentDao.insertOrUpdate(newIncident)
        return Projection.Record(true)
    }

    private suspend fun projectAssignment(event: DomainEventEntity, current: IncidentEntity): Projection {
        val p = JSONObject(event.payloadJson)
        val newResponderId = p.optString("responderId", event.actorId)
        val newResponderName = p.optString("responderName", event.actorName)
        if (newResponderId != event.actorId) {
            return Projection.Settled(rejectIncoming(event, "assignment responder differs from actor"))
        }
        val assigned = current.copy(
            primaryResponderId = newResponderId,
            primaryResponderName = newResponderName,
            version = event.logicalVersion,
            updatedAt = event.timestamp
        )
        if (current.status == IncidentState.ASSIGNED.name) {
            if (!winsAssignmentConflict(event, current)) {
                AppLogger.d("INCIDENT_REPO", "Assignment conflict: keeping existing responder ${current.primaryResponderName}")
                return Projection.Record(false)
            }
            incidentDao.insertOrUpdate(assigned)
            return Projection.Record(true)
        }
        if (!IncidentPolicy.mayPerform(current, event.eventType, event.actorId)) return Projection.Record(false)
        incidentDao.insertOrUpdate(assigned.copy(status = IncidentState.ASSIGNED.name))
        return Projection.Record(true)
    }

    /** Deterministic conflict winner: earliest timestamp, then event ID. */
    private suspend fun winsAssignmentConflict(event: DomainEventEntity, current: IncidentEntity): Boolean {
        val existingAssignmentEvent = domainEventDao.getEventsForEntity(event.entityId)
            .firstOrNull {
                it.eventType == EventType.INCIDENT_ASSIGNED.name &&
                    it.logicalVersion == current.version && it.applied
            }
        return event.timestamp < current.updatedAt ||
            (event.timestamp == current.updatedAt &&
                event.eventId < (existingAssignmentEvent?.eventId ?: "~"))
    }

    suspend fun rejectIncoming(event: DomainEventEntity, reason: String): Boolean {
        AppLogger.d("INCIDENT_REPO", "Rejected incident event ${event.eventId}: $reason")
        domainEventDao.insertEvent(event.copy(applied = false, validationStatus = EventValidationStatus.REJECTED))
        domainEventDao.setValidationStatus(event.eventId, EventValidationStatus.REJECTED)
        return false
    }

    private companion object {
        val STATUS_TRANSITIONS = mapOf(
            EventType.INCIDENT_ACKNOWLEDGED.name to IncidentState.ACKNOWLEDGED,
            EventType.INCIDENT_RESPONSE_STARTED.name to IncidentState.RESPONDING,
            EventType.INCIDENT_RESOLVED.name to IncidentState.RESOLVED,
            EventType.INCIDENT_CANCELLED.name to IncidentState.CANCELLED
        )
    }
}
