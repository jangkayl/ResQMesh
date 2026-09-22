package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.EventType
import com.example.testresqmesh.core.model.IncidentState
import com.example.testresqmesh.data.local.entity.IncidentEntity

/**
 * The single authorization and transition authority for incident events.  UI affordances may
 * mirror these rules, but callers must always use this policy before mutating Room state.
 */
object IncidentPolicy {
    fun mayPerform(current: IncidentEntity?, eventType: String, actorId: String): Boolean {
        return when (eventType) {
            EventType.INCIDENT_CREATED.name -> current == null
            EventType.INCIDENT_ACKNOWLEDGED.name ->
                current?.status == IncidentState.OPEN.name && current.creatorId != actorId
            EventType.INCIDENT_ASSIGNED.name -> current?.let {
                it.status in setOf(IncidentState.OPEN.name, IncidentState.ACKNOWLEDGED.name) &&
                    it.creatorId != actorId
            } ?: false
            EventType.INCIDENT_RESPONSE_STARTED.name ->
                current?.status == IncidentState.ASSIGNED.name &&
                    current.primaryResponderId == actorId && current.creatorId != actorId
            EventType.INCIDENT_RESOLVED.name -> current?.let {
                it.status in setOf(IncidentState.ASSIGNED.name, IncidentState.RESPONDING.name) &&
                    it.primaryResponderId == actorId && it.creatorId != actorId
            } ?: false
            EventType.INCIDENT_CANCELLED.name ->
                current != null && current.status !in setOf(IncidentState.RESOLVED.name, IncidentState.CANCELLED.name) &&
                    current.creatorId == actorId
            EventType.INCIDENT_ASSIGNMENT_RELEASED.name -> current?.let {
                it.status in setOf(IncidentState.ASSIGNED.name, IncidentState.RESPONDING.name) &&
                    it.primaryResponderId == actorId && it.creatorId != actorId
            } ?: false
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

}
