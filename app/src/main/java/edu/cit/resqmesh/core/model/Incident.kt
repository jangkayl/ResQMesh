package edu.cit.resqmesh.core.model

import kotlinx.serialization.Serializable

/** Incidents at this workflow version use signed reporter/helper coordination. */
const val HELP_WORKFLOW_VERSION = 2

@Serializable
enum class IncidentState {
    OPEN,
    ACKNOWLEDGED,
    AWAITING_HELPER,
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
    val locationAccuracyMeters: Float? = null,
    val title: String = ""
)

data class IncidentLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val capturedAt: Long,
    val accuracyMeters: Float?
)

/** Mirrors IncidentEntity 1:1; status stays a String so old rows never fail enum decoding. */
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
    val status: String,
    val primaryResponderId: String?,
    val primaryResponderName: String?,
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val workflowVersion: Int,
    val reporterSigningKey: String?,
    val selectionId: String?,
    val selectionOfferId: String?,
    val selectionOfferRevision: Long?,
    val selectedHelperKey: String?,
    val selectionConfirmedAt: Long?,
    val title: String
)

/** Mirrors IncidentOfferEntity 1:1. */
data class IncidentOffer(
    val offerId: String,
    val incidentId: String,
    val helperKey: String,
    val helperNodeId: String,
    val helperName: String,
    val note: String,
    val revision: Long,
    val withdrawn: Boolean,
    val updatedAt: Long
)
