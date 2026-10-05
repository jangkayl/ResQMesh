package com.example.testresqmesh.data.repository.incident

import com.example.testresqmesh.core.model.EntityType
import com.example.testresqmesh.core.model.EventType
import com.example.testresqmesh.core.model.IncidentState
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.TerminalLogCategory
import com.example.testresqmesh.core.utils.TerminalLogLevel
import com.example.testresqmesh.data.local.dao.DomainEventDao
import com.example.testresqmesh.data.local.dao.IncidentDao
import com.example.testresqmesh.data.local.AppDatabase
import androidx.room.withTransaction
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import kotlinx.serialization.ExperimentalSerializationApi
import org.json.JSONObject
import java.util.UUID
import com.example.testresqmesh.data.repository.IdentityProvider
import com.example.testresqmesh.data.repository.IncidentEventSigning
import com.example.testresqmesh.data.repository.IncidentHelpWorkflow
import com.example.testresqmesh.data.repository.IncidentPolicy

@OptIn(ExperimentalSerializationApi::class)
internal class IncidentCommands(
    private val incidentDao: IncidentDao,
    private val domainEventDao: DomainEventDao,
    private val identityManager: IdentityProvider,
    private val database: AppDatabase?,
    private val eventSigning: IncidentEventSigning?,
    private val helpWorkflowProvider: () -> IncidentHelpWorkflow?,
    private val broadcast: (DomainEventEntity, Boolean) -> Unit
) {
    private val helpWorkflow get() = helpWorkflowProvider()
    private fun broadcastDomainEvent(event: DomainEventEntity, isP0: Boolean) = broadcast(event, isP0)

    suspend fun createIncident(
        incidentType: String,
        severity: String,
        description: String,
        areaDescription: String,
        latitude: Double? = null,
        longitude: Double? = null,
        locationCapturedAt: Long? = null,
        locationAccuracyMeters: Float? = null,
        title: String = ""
    ): IncidentEntity {
        val cleanTitle = title.trim()
        require(cleanTitle.length <= 80) { "Incident title cannot exceed 80 characters" }
        val user = identityManager.getOrCreateUser()
        val incidentId = "INC-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
        val now = System.currentTimeMillis()

        val incident = IncidentEntity(
            incidentId = incidentId,
            creatorId = user.userId,
            creatorName = user.displayName,
            incidentType = incidentType,
            severity = severity,
            description = description,
            areaDescription = areaDescription,
            latitude = latitude,
            longitude = longitude,
            locationCapturedAt = locationCapturedAt,
            locationAccuracyMeters = locationAccuracyMeters,
            status = IncidentState.OPEN.name,
            primaryResponderId = null,
            primaryResponderName = null,
            version = 1,
            createdAt = now,
            updatedAt = now,
            workflowVersion = if (helpWorkflow != null) 2 else 1,
            reporterSigningKey = eventSigning?.publicKey,
            title = cleanTitle
        )

        val payloadJson = JSONObject().apply {
            if (cleanTitle.isNotBlank()) {
                put("title", cleanTitle)
            }
            put("incidentType", incidentType)
            put("severity", severity)
            put("description", description)
            put("areaDescription", areaDescription)
            if (helpWorkflow != null) {
                put("workflowVersion", 2)
                put("reporterSigningKey", eventSigning?.publicKey)
            }
            latitude?.let { put("latitude", it) }
            longitude?.let { put("longitude", it) }
            locationCapturedAt?.let { put("locationCapturedAt", it) }
            locationAccuracyMeters?.let { put("locationAccuracyMeters", it) }
        }.toString()

        val unsignedEvent = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_CREATED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = 1,
            timestamp = now,
            payloadJson = payloadJson,
            applied = true
        )
        val event = if (helpWorkflow != null) unsignedEvent.copy(signature = eventSigning?.sign(unsignedEvent))
            else unsignedEvent

        suspend fun persist() {
            incidentDao.insertOrUpdate(incident)
            domainEventDao.insertEvent(event)
        }
        if (database != null) database.withTransaction { persist() } else persist()

        AppLogger.event(
            category = TerminalLogCategory.SYSTEM,
            event = "INCIDENT_CREATED",
            message = "Created new SOS Incident $incidentId by ${user.displayName}",
            level = TerminalLogLevel.INFO,
            tag = "INCIDENT_REPO"
        )

        broadcastDomainEvent(event, isP0 = true)
        return incident
    }

    suspend fun acknowledgeIncident(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_ACKNOWLEDGED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val newVersion = current.version + 1
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

        val updated = current.copy(
            status = IncidentState.ACKNOWLEDGED.name,
            version = newVersion,
            updatedAt = now
        )

        val event = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_ACKNOWLEDGED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = newVersion,
            timestamp = now,
            payloadJson = "{}",
            applied = true
        )

        incidentDao.insertOrUpdate(updated)
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = false)
        return true
    }

    suspend fun assignIncident(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_ASSIGNED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val newVersion = current.version + 1
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

        val updated = current.copy(
            status = IncidentState.ASSIGNED.name,
            primaryResponderId = user.userId,
            primaryResponderName = user.displayName,
            version = newVersion,
            updatedAt = now
        )

        val payloadJson = JSONObject().apply {
            put("responderId", user.userId)
            put("responderName", user.displayName)
        }.toString()

        val event = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_ASSIGNED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = newVersion,
            timestamp = now,
            payloadJson = payloadJson,
            applied = true
        )

        incidentDao.insertOrUpdate(updated)
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = true)
        return true
    }

    suspend fun startResponding(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_RESPONSE_STARTED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val newVersion = current.version + 1
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

        val updated = current.copy(
            status = IncidentState.RESPONDING.name,
            version = newVersion,
            updatedAt = now
        )

        val event = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_RESPONSE_STARTED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = newVersion,
            timestamp = now,
            payloadJson = "{}",
            applied = true
        )

        incidentDao.insertOrUpdate(updated)
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = false)
        return true
    }

    suspend fun resolveIncident(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_RESOLVED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val newVersion = current.version + 1
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

        val updated = current.copy(
            status = IncidentState.RESOLVED.name,
            version = newVersion,
            updatedAt = now
        )

        val event = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_RESOLVED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = newVersion,
            timestamp = now,
            payloadJson = "{}",
            applied = true
        )

        incidentDao.insertOrUpdate(updated)
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = false)
        return true
    }

    suspend fun cancelIncident(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_CANCELLED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val newVersion = current.version + 1
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

        val updated = current.copy(
            status = IncidentState.CANCELLED.name,
            version = newVersion,
            updatedAt = now
        )

        val event = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_CANCELLED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = newVersion,
            timestamp = now,
            payloadJson = "{}",
            applied = true
        )

        incidentDao.insertOrUpdate(updated)
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = true)
        return true
    }

    suspend fun releaseAssignment(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_ASSIGNMENT_RELEASED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val event = DomainEventEntity(
            eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}",
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_ASSIGNMENT_RELEASED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = current.version + 1,
            timestamp = now,
            payloadJson = "{}",
            applied = true
        )
        incidentDao.insertOrUpdate(current.copy(
            status = IncidentState.ACKNOWLEDGED.name,
            primaryResponderId = null,
            primaryResponderName = null,
            version = event.logicalVersion,
            updatedAt = now
        ))
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = false)
        return true
    }
}
