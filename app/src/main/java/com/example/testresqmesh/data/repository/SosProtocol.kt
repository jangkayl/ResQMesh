package com.example.testresqmesh.data.repository

import com.example.testresqmesh.data.local.entity.DomainEventEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class SosEvent(
    val eventId: String, val sosId: String, val originNodeId: String, val originName: String,
    val signingKey: String, val emergencyType: String, val revision: Long, val ended: Boolean,
    val createdAt: Long, val updatedAt: Long, val latitude: Double? = null, val longitude: Double? = null,
    val accuracyMeters: Float? = null, val locationCapturedAt: Long? = null, val signature: String = ""
) {
    fun signingEvent(): DomainEventEntity = DomainEventEntity(
        eventId = eventId, entityId = sosId, entityType = "SOS", eventType = "SOS_STATE",
        actorId = originNodeId, actorName = originName, logicalVersion = revision,
        timestamp = updatedAt, payloadJson = SosProtocol.json.encodeToString(copy(signature = "")),
        signature = signature
    )
}

object SosProtocol {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun prefix(key: String) = "SOS-${hash(key).take(24)}-"
    fun valid(e: SosEvent): Boolean = e.sosId.startsWith(prefix(e.signingKey)) &&
        e.sosId.length in 65..100 && e.eventId.length in 1..100 && e.originNodeId.length in 1..100 &&
        e.signingKey.length in 1..1024 && e.originName.length in 1..160 &&
        e.emergencyType.length in 1..80 && e.revision > 0 && e.createdAt > 0 && e.updatedAt >= e.createdAt &&
        (e.revision != 1L || !e.ended) && ((e.latitude == null) == (e.longitude == null)) &&
        (e.latitude == null || (e.latitude.isFinite() && e.latitude in -90.0..90.0)) &&
        (e.longitude == null || (e.longitude.isFinite() && e.longitude in -180.0..180.0)) &&
        (e.accuracyMeters == null || (e.accuracyMeters.isFinite() && e.accuracyMeters >= 0))
}

enum class SosIngestion { APPLIED, DUPLICATE, STALE, REJECTED }

fun com.example.testresqmesh.data.local.entity.SosAlertEntity.asMessage() = com.example.testresqmesh.core.model.ChatMessage(
    id = sosId, senderName = originName, text = "$emergencyType emergency", imageBase64 = null, audioBase64 = null,
    locationLat = latitude, locationLng = longitude, isMine = false, timestamp = createdAt,
    isSOS = true, conversationKind = "SOS", sosId = sosId, senderNodeId = originNodeId)

/** Terminal state cannot be undone, even by a delayed higher-revision active snapshot. */
internal fun sosTransition(current: com.example.testresqmesh.data.local.entity.SosAlertEntity?, e: SosEvent): SosIngestion {
    if (current == null) return SosIngestion.APPLIED
    if (current.signingKey != e.signingKey || current.originNodeId != e.originNodeId || current.createdAt != e.createdAt)
        return SosIngestion.REJECTED
    if (e.revision <= current.revision || (current.ended && !e.ended)) return SosIngestion.STALE
    return SosIngestion.APPLIED
}
