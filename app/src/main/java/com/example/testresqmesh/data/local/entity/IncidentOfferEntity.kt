package com.example.testresqmesh.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "incident_offers", indices = [Index(value = ["incidentId", "helperKey"], unique = true)])
data class IncidentOfferEntity(
    @PrimaryKey val offerId: String,
    val incidentId: String,
    val helperKey: String,
    val helperNodeId: String,
    val helperName: String,
    val note: String,
    val revision: Long,
    val withdrawn: Boolean,
    val updatedAt: Long
)
