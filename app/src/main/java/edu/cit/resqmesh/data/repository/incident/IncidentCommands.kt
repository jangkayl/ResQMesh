package edu.cit.resqmesh.data.repository.incident

import edu.cit.resqmesh.core.security.input.TextField
import edu.cit.resqmesh.core.security.input.TextSanitizer
import edu.cit.resqmesh.core.model.EntityType
import edu.cit.resqmesh.core.model.EventType
import edu.cit.resqmesh.core.model.HELP_WORKFLOW_VERSION
import edu.cit.resqmesh.core.model.IncidentState
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.TerminalLogCategory
import edu.cit.resqmesh.core.logging.TerminalLogLevel
import edu.cit.resqmesh.data.local.dao.DomainEventDao
import edu.cit.resqmesh.data.local.dao.IncidentDao
import edu.cit.resqmesh.data.local.AppDatabase
import androidx.room.withTransaction
import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import edu.cit.resqmesh.data.local.entity.IncidentEntity
import edu.cit.resqmesh.data.local.entity.UserEntity
import kotlinx.serialization.ExperimentalSerializationApi
import org.json.JSONObject
import java.util.UUID
import edu.cit.resqmesh.data.repository.IdentityProvider
import edu.cit.resqmesh.data.repository.IncidentEventSigning
import edu.cit.resqmesh.data.repository.IncidentHelpWorkflow
import edu.cit.resqmesh.data.repository.IncidentPolicy

/** What a reporter supplies for a new incident. */
internal data class IncidentDraft(
    val incidentType: String,
    val severity: String,
    val description: String,
    val areaDescription: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationCapturedAt: Long? = null,
    val locationAccuracyMeters: Float? = null,
    val title: String = ""
) {
    fun sanitized(): IncidentDraft = copy(
        title = sanitizedWithin(title, TextField.INCIDENT_TITLE, "Incident title"),
        description = sanitizedWithin(description, TextField.INCIDENT_DESCRIPTION, "Incident description"),
        areaDescription = sanitizedWithin(areaDescription, TextField.INCIDENT_AREA, "Incident area")
    )

    private fun sanitizedWithin(value: String, field: TextField, label: String): String {
        val clean = TextSanitizer.sanitize(value, field)
        require(clean.length <= field.maxLength) { "$label cannot exceed ${field.maxLength} characters" }
        return clean
    }
}

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

    suspend fun createIncident(draft: IncidentDraft): IncidentEntity {
        val clean = draft.sanitized()
        val user = identityManager.getOrCreateUser()
        val incidentId = shortId("INC")
        val eventId = shortId("EVT")
        val now = System.currentTimeMillis()
        val incident = newIncident(clean, incidentId, user, now)
        val unsignedEvent = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_CREATED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = FIRST_VERSION,
            timestamp = now,
            payloadJson = creationPayloadJson(clean),
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

    private fun newIncident(draft: IncidentDraft, incidentId: String, user: UserEntity, now: Long) = IncidentEntity(
        incidentId = incidentId,
        creatorId = user.userId,
        creatorName = user.displayName,
        incidentType = draft.incidentType,
        severity = draft.severity,
        description = draft.description,
        areaDescription = draft.areaDescription,
        latitude = draft.latitude,
        longitude = draft.longitude,
        locationCapturedAt = draft.locationCapturedAt,
        locationAccuracyMeters = draft.locationAccuracyMeters,
        status = IncidentState.OPEN.name,
        primaryResponderId = null,
        primaryResponderName = null,
        version = FIRST_VERSION,
        createdAt = now,
        updatedAt = now,
        workflowVersion = if (helpWorkflow != null) HELP_WORKFLOW_VERSION else LEGACY_WORKFLOW_VERSION,
        reporterSigningKey = eventSigning?.publicKey,
        title = draft.title
    )

    private fun creationPayloadJson(draft: IncidentDraft): String = JSONObject().apply {
        if (draft.title.isNotBlank()) {
            put("title", draft.title)
        }
        put("incidentType", draft.incidentType)
        put("severity", draft.severity)
        put("description", draft.description)
        put("areaDescription", draft.areaDescription)
        if (helpWorkflow != null) {
            put("workflowVersion", HELP_WORKFLOW_VERSION)
            put("reporterSigningKey", eventSigning?.publicKey)
        }
        draft.latitude?.let { put("latitude", it) }
        draft.longitude?.let { put("longitude", it) }
        draft.locationCapturedAt?.let { put("locationCapturedAt", it) }
        draft.locationAccuracyMeters?.let { put("locationAccuracyMeters", it) }
    }.toString()

    suspend fun acknowledgeIncident(incidentId: String): Boolean =
        recordTransition(incidentId, EventType.INCIDENT_ACKNOWLEDGED, isP0 = false) { current, _ ->
            current.copy(status = IncidentState.ACKNOWLEDGED.name)
        }

    suspend fun assignIncident(incidentId: String): Boolean =
        recordTransition(incidentId, EventType.INCIDENT_ASSIGNED, isP0 = true, payloadJson = { user ->
            JSONObject().apply {
                put("responderId", user.userId)
                put("responderName", user.displayName)
            }.toString()
        }) { current, user ->
            current.copy(
                status = IncidentState.ASSIGNED.name,
                primaryResponderId = user.userId,
                primaryResponderName = user.displayName
            )
        }

    suspend fun startResponding(incidentId: String): Boolean =
        recordTransition(incidentId, EventType.INCIDENT_RESPONSE_STARTED, isP0 = false) { current, _ ->
            current.copy(status = IncidentState.RESPONDING.name)
        }

    suspend fun resolveIncident(incidentId: String): Boolean =
        recordTransition(incidentId, EventType.INCIDENT_RESOLVED, isP0 = false) { current, _ ->
            current.copy(status = IncidentState.RESOLVED.name)
        }

    suspend fun cancelIncident(incidentId: String): Boolean =
        recordTransition(incidentId, EventType.INCIDENT_CANCELLED, isP0 = true) { current, _ ->
            current.copy(status = IncidentState.CANCELLED.name)
        }

    suspend fun releaseAssignment(incidentId: String): Boolean =
        recordTransition(incidentId, EventType.INCIDENT_ASSIGNMENT_RELEASED, isP0 = false) { current, _ ->
            current.copy(
                status = IncidentState.ACKNOWLEDGED.name,
                primaryResponderId = null,
                primaryResponderName = null
            )
        }

    private suspend fun recordTransition(
        incidentId: String,
        type: EventType,
        isP0: Boolean,
        payloadJson: (UserEntity) -> String = { EMPTY_PAYLOAD },
        update: (IncidentEntity, UserEntity) -> IncidentEntity
    ): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, type.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val newVersion = current.version + 1
        val event = DomainEventEntity(
            eventId = shortId("EVT"),
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = type.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = newVersion,
            timestamp = now,
            payloadJson = payloadJson(user),
            applied = true
        )
        incidentDao.insertOrUpdate(update(current, user).copy(version = newVersion, updatedAt = now))
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0)
        return true
    }

    private fun shortId(prefix: String) =
        "$prefix-${UUID.randomUUID().toString().replace("-", "").take(SHORT_ID_LENGTH).uppercase()}"

    private companion object {
        const val SHORT_ID_LENGTH = 8
        const val FIRST_VERSION = 1L
        const val LEGACY_WORKFLOW_VERSION = 1
        const val EMPTY_PAYLOAD = "{}"
    }
}
