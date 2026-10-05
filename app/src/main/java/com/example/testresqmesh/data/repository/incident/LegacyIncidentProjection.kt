package com.example.testresqmesh.data.repository.incident

import com.example.testresqmesh.core.model.EntityType
import com.example.testresqmesh.core.model.EventType
import com.example.testresqmesh.core.model.IncidentState
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.data.local.dao.DomainEventDao
import com.example.testresqmesh.data.local.dao.IncidentDao
import com.example.testresqmesh.data.local.AppDatabase
import androidx.room.withTransaction
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import kotlinx.serialization.ExperimentalSerializationApi
import org.json.JSONObject
import com.example.testresqmesh.data.repository.IncidentEventSigning
import com.example.testresqmesh.data.repository.IncidentPolicy

@OptIn(ExperimentalSerializationApi::class)
internal class LegacyIncidentProjection(
    private val incidentDao: IncidentDao,
    private val domainEventDao: DomainEventDao,
    private val database: AppDatabase?,
    private val eventSigning: IncidentEventSigning?
) {


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
            domainEventDao.insertEvent(event.copy(applied = false, validationStatus = "REJECTED"))
            return false
        }
        if (event.eventType != EventType.INCIDENT_CREATED.name && !isCompetingAssignment) {
            if (current == null || event.logicalVersion > current.version + 1L) {
                domainEventDao.insertEvent(event.copy(applied = false, validationStatus = "UNVERIFIED"))
                return false
            }
            if (!IncidentPolicy.mayPerform(current, event.eventType, event.actorId) || event.logicalVersion != current.version + 1L)
                return rejectIncoming(event, "invalid legacy authority or version")
        }
        val applied = when (event.eventType) {
            EventType.INCIDENT_CREATED.name -> {
                if (current != null) {
                    AppLogger.d("INCIDENT_REPO", "Incident ${event.entityId} already exists, ignoring creation.")
                    false
                } else {
                    val p = JSONObject(event.payloadJson)
                    val workflowVersion = p.optInt("workflowVersion", 1)
                    val reporterKey = p.optString("reporterSigningKey").takeIf { it.isNotBlank() }
                    if (workflowVersion == 2 && (reporterKey == null ||
                            eventSigning?.verify(event, reporterKey) != true)) {
                        return rejectIncoming(event, "invalid reporter identity")
                    }
                    val incomingTitle = if (p.has("title")) {
                        val raw = p.opt("title")
                        if (raw == null || raw == JSONObject.NULL) {
                            ""
                        } else if (raw !is String) {
                            return rejectIncoming(event, "title must be a text string")
                        } else {
                            val trimmed = raw.trim()
                            if (trimmed.length > 80) {
                                return rejectIncoming(event, "title cannot exceed 80 characters")
                            }
                            trimmed
                        }
                    } else {
                        ""
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
                    if (workflowVersion == 2 && database != null) {
                        database.withTransaction {
                            incidentDao.insertOrUpdate(newIncident)
                            domainEventDao.insertEvent(event.copy(applied = true))
                            domainEventDao.setApplied(event.eventId, true)
                            domainEventDao.setValidationStatus(event.eventId, "ACCEPTED")
                        }
                        return true
                    }
                    incidentDao.insertOrUpdate(newIncident)
                    true
                }
            }
            EventType.INCIDENT_ACKNOWLEDGED.name -> {
                if (current != null) {
                    incidentDao.insertOrUpdate(
                        current.copy(
                            status = IncidentState.ACKNOWLEDGED.name,
                            version = event.logicalVersion,
                            updatedAt = event.timestamp
                        )
                    )
                    true
                } else false
            }
            EventType.INCIDENT_ASSIGNED.name -> {
                if (current != null) {
                    val p = JSONObject(event.payloadJson)
                    val newResponderId = p.optString("responderId", event.actorId)
                    val newResponderName = p.optString("responderName", event.actorName)
                    if (newResponderId != event.actorId) return rejectIncoming(event, "assignment responder differs from actor")

                    // Conflict resolution if already assigned
                    if (current.status == IncidentState.ASSIGNED.name) {
                        val existingAssignmentEvent = domainEventDao.getEventsForEntity(event.entityId)
                            .firstOrNull {
                                it.eventType == EventType.INCIDENT_ASSIGNED.name &&
                                    it.logicalVersion == current.version && it.applied
                            }
                        // Deterministic conflict winner: earliest timestamp, then event ID.
                        val isEarlier = event.timestamp < current.updatedAt ||
                                (event.timestamp == current.updatedAt &&
                                    event.eventId < (existingAssignmentEvent?.eventId ?: "~"))
                        if (isEarlier) {
                            incidentDao.insertOrUpdate(
                                current.copy(
                                    primaryResponderId = newResponderId,
                                    primaryResponderName = newResponderName,
                                    version = event.logicalVersion,
                                    updatedAt = event.timestamp
                                )
                            )
                            true
                        } else {
                            AppLogger.d("INCIDENT_REPO", "Assignment conflict: keeping existing responder ${current.primaryResponderName}")
                            false
                        }
                    } else if (IncidentPolicy.mayPerform(current, event.eventType, event.actorId)) {
                        incidentDao.insertOrUpdate(
                            current.copy(
                                status = IncidentState.ASSIGNED.name,
                                primaryResponderId = newResponderId,
                                primaryResponderName = newResponderName,
                                version = event.logicalVersion,
                                updatedAt = event.timestamp
                            )
                        )
                        true
                    } else false
                } else false
            }
            EventType.INCIDENT_RESPONSE_STARTED.name -> {
                if (current != null) {
                    incidentDao.insertOrUpdate(
                        current.copy(
                            status = IncidentState.RESPONDING.name,
                            version = event.logicalVersion,
                            updatedAt = event.timestamp
                        )
                    )
                    true
                } else false
            }
            EventType.INCIDENT_RESOLVED.name -> {
                if (current != null) {
                    incidentDao.insertOrUpdate(
                        current.copy(
                            status = IncidentState.RESOLVED.name,
                            version = event.logicalVersion,
                            updatedAt = event.timestamp
                        )
                    )
                    true
                } else false
            }
            EventType.INCIDENT_CANCELLED.name -> {
                if (current != null) {
                    incidentDao.insertOrUpdate(
                        current.copy(
                            status = IncidentState.CANCELLED.name,
                            version = event.logicalVersion,
                            updatedAt = event.timestamp
                        )
                    )
                    true
                } else false
            }
            EventType.INCIDENT_ASSIGNMENT_RELEASED.name -> {
                if (current != null) {
                    incidentDao.insertOrUpdate(
                        current.copy(
                            status = IncidentState.ACKNOWLEDGED.name,
                            primaryResponderId = null,
                            primaryResponderName = null,
                            version = event.logicalVersion,
                            updatedAt = event.timestamp
                        )
                    )
                    true
                } else false
            }
            else -> false
        }

        domainEventDao.insertEvent(event.copy(applied = applied))
        domainEventDao.setApplied(event.eventId, applied)
        domainEventDao.setValidationStatus(event.eventId, if (applied) "ACCEPTED" else "REJECTED")
        return applied
    }

    suspend fun rejectIncoming(event: DomainEventEntity, reason: String): Boolean {
        AppLogger.d("INCIDENT_REPO", "Rejected incident event ${event.eventId}: $reason")
        domainEventDao.insertEvent(event.copy(applied = false, validationStatus = "REJECTED"))
        domainEventDao.setValidationStatus(event.eventId, "REJECTED")
        return false
    }
}
