package com.example.testresqmesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sos_alerts")
data class SosAlertEntity(
    @PrimaryKey val sosId: String,
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

@Entity(tableName = "sos_events")
data class SosEventEntity(@PrimaryKey val eventId: String, val sosId: String, val revision: Long,
    val eventJson: String, val pending: Boolean)

@Entity(tableName = "conversation_state")
data class ConversationStateEntity(@PrimaryKey val conversationId: String, val draft: String = "",
    val lastReadAt: Long = 0L)
