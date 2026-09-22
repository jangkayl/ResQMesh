package com.example.testresqmesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "domain_events")
data class DomainEventEntity(
    @PrimaryKey val eventId: String,
    val entityId: String,
    val entityType: String,
    val eventType: String,
    val actorId: String,
    val actorName: String,
    val logicalVersion: Long,
    val timestamp: Long,
    val payloadJson: String,
    val signature: String? = null,
    val applied: Boolean = true
)
