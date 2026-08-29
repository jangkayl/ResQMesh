package com.example.testresqmesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val msgId: String,
    val senderName: String,
    val targetName: String?,
    val text: String?,
    val imageBase64: String?,
    val audioBase64: String?,
    val locationLat: Double?,
    val locationLng: Double?,
    val timestamp: Long,
    val isSOS: Boolean,
    val isMine: Boolean,
    val deliveredTo: String, // Comma-separated
    val seenBy: String,      // Comma-separated
    val outboundRoute: String // Comma-separated
) {
    fun toChatMessage(): com.example.testresqmesh.core.model.ChatMessage {
        return com.example.testresqmesh.core.model.ChatMessage(
            id = msgId,
            senderName = senderName,
            text = text ?: "",
            imageBase64 = imageBase64,
            audioBase64 = audioBase64,
            locationLat = locationLat,
            locationLng = locationLng,
            isMine = isMine,
            isPrivate = targetName != null,
            timestamp = timestamp,
            isHopped = outboundRoute.contains(","), // Simple heuristic
            deliveredTo = if (deliveredTo.isEmpty()) emptyList() else deliveredTo.split(","),
            seenBy = if (seenBy.isEmpty()) emptyList() else seenBy.split(","),
            outboundRoute = if (outboundRoute.isEmpty()) emptyList() else outboundRoute.split(","),
            isSOS = isSOS
        )
    }
}

fun com.example.testresqmesh.core.model.ChatMessage.toMessageEntity(targetName: String?): MessageEntity {
    return MessageEntity(
        msgId = this.id,
        senderName = this.senderName,
        targetName = targetName,
        text = this.text,
        imageBase64 = this.imageBase64,
        audioBase64 = this.audioBase64,
        locationLat = this.locationLat,
        locationLng = this.locationLng,
        timestamp = this.timestamp,
        isSOS = this.isSOS,
        isMine = this.isMine,
        deliveredTo = this.deliveredTo.joinToString(","),
        seenBy = this.seenBy.joinToString(","),
        outboundRoute = this.outboundRoute.joinToString(",")
    )
}
