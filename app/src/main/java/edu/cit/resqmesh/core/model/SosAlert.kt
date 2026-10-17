package edu.cit.resqmesh.core.model

/** Mirrors SosAlertEntity 1:1; transmission stays a String as stored. */
data class SosAlert(
    val sosId: String,
    val originNodeId: String,
    val originName: String,
    val signingKey: String,
    val emergencyType: String,
    val revision: Long,
    val ended: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val locationCapturedAt: Long?,
    val locallySilenced: Boolean,
    val eventJson: String,
    val transmission: String
)

/** One retained SOS revision decoded for the audit history; the UI shows only these fields. */
data class SosActivity(
    val eventId: String,
    val revision: Long,
    val ended: Boolean,
    val updatedAt: Long,
    val latitude: Double?,
    val accuracyMeters: Float?
)

/** Chat-list/map representation of an SOS alert. */
fun SosAlert.toChatMessage() = ChatMessage(
    id = sosId, senderName = originName, text = "$emergencyType emergency", imageBase64 = null, audioBase64 = null,
    locationLat = latitude, locationLng = longitude, isMine = false, timestamp = createdAt,
    isSOS = true, conversationKind = "SOS", sosId = sosId, senderNodeId = originNodeId)
