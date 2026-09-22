package com.example.testresqmesh.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class IncidentState {
    OPEN,
    ACKNOWLEDGED,
    ASSIGNED,
    RESPONDING,
    RESOLVED,
    CANCELLED
}

@Serializable
data class IncidentPayload(
    val incidentType: String = "",
    val severity: String = "",
    val description: String = "",
    val areaDescription: String = "",
    val responderId: String? = null,
    val responderName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationCapturedAt: Long? = null,
    val locationAccuracyMeters: Float? = null
)

data class IncidentLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val capturedAt: Long,
    val accuracyMeters: Float?
)

data class Incident(
    val incidentId: String,
    val creatorId: String,
    val creatorName: String,
    val incidentType: String,
    val severity: String,
    val description: String,
    val areaDescription: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationCapturedAt: Long?,
    val locationAccuracyMeters: Float?,
    val status: IncidentState,
    val primaryResponderId: String?,
    val primaryResponderName: String?,
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long
)
