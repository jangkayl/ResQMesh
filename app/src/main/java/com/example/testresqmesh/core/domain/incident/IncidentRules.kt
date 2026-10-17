package com.example.testresqmesh.core.domain.incident

import com.example.testresqmesh.core.model.EventType
import com.example.testresqmesh.core.model.IncidentState

/**
 * Pure incident authorization and transition rules over primitive fields. A null [status]
 * means no incident exists yet. Data-layer wrappers convert entities at the call site.
 */
object IncidentRules {
    @Suppress("CyclomaticComplexMethod")
    fun mayPerform(
        status: String?,
        creatorId: String?,
        primaryResponderId: String?,
        eventType: String,
        actorId: String
    ): Boolean {
        val exists = status != null
        return when (eventType) {
            EventType.INCIDENT_CREATED.name -> !exists
            EventType.INCIDENT_ACKNOWLEDGED.name ->
                status == IncidentState.OPEN.name && creatorId != actorId
            EventType.INCIDENT_ASSIGNED.name ->
                exists && status in setOf(IncidentState.OPEN.name, IncidentState.ACKNOWLEDGED.name) &&
                    creatorId != actorId
            EventType.INCIDENT_RESPONSE_STARTED.name ->
                status == IncidentState.ASSIGNED.name &&
                    primaryResponderId == actorId && creatorId != actorId
            EventType.INCIDENT_RESOLVED.name ->
                exists && status in setOf(IncidentState.ASSIGNED.name, IncidentState.RESPONDING.name) &&
                    primaryResponderId == actorId && creatorId != actorId
            EventType.INCIDENT_CANCELLED.name ->
                exists && status !in setOf(IncidentState.RESOLVED.name, IncidentState.CANCELLED.name) &&
                    creatorId == actorId
            EventType.INCIDENT_ASSIGNMENT_RELEASED.name ->
                exists && status in setOf(IncidentState.ASSIGNED.name, IncidentState.RESPONDING.name) &&
                    primaryResponderId == actorId && creatorId != actorId
            else -> false
        }
    }

    fun nextState(eventType: String): String? = when (eventType) {
        EventType.INCIDENT_CREATED.name -> IncidentState.OPEN.name
        EventType.INCIDENT_ACKNOWLEDGED.name -> IncidentState.ACKNOWLEDGED.name
        EventType.INCIDENT_ASSIGNED.name -> IncidentState.ASSIGNED.name
        EventType.INCIDENT_RESPONSE_STARTED.name -> IncidentState.RESPONDING.name
        EventType.INCIDENT_RESOLVED.name -> IncidentState.RESOLVED.name
        EventType.INCIDENT_CANCELLED.name -> IncidentState.CANCELLED.name
        EventType.INCIDENT_ASSIGNMENT_RELEASED.name -> IncidentState.ACKNOWLEDGED.name
        else -> null
    }

    fun isReporter(
        creatorId: String,
        workflowVersion: Int,
        reporterSigningKey: String?,
        userId: String?,
        signingKey: String?
    ): Boolean =
        userId != null && userId == creatorId &&
            (workflowVersion != 2 || !signingKey.isNullOrBlank() && signingKey == reporterSigningKey)
}
