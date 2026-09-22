package com.example.testresqmesh.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class EntityType {
    INCIDENT,
    RESOURCE_REQUEST,
    SAFETY_STATUS
}

@Serializable
enum class EventType {
    INCIDENT_CREATED,
    INCIDENT_ACKNOWLEDGED,
    INCIDENT_ASSIGNED,
    INCIDENT_ASSIGNMENT_RELEASED,
    INCIDENT_RESPONSE_STARTED,
    INCIDENT_RESOLVED,
    INCIDENT_CANCELLED,

    RESOURCE_REQUEST_CREATED,
    RESOURCE_REQUEST_CLAIMED,
    RESOURCE_IN_TRANSIT,
    RESOURCE_DELIVERED,
    RESOURCE_CANCELLED,

    SAFETY_STATUS_CHANGED
}

@Serializable
data class DomainEvent(
    val eventId: String,
    val entityId: String,
    val entityType: EntityType,
    val eventType: EventType,
    val actorId: String,
    val actorName: String = "",
    val logicalVersion: Long,
    val timestamp: Long,
    val payloadJson: String = "{}",
    val signature: String? = null
)
