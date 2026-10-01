package com.example.testresqmesh.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val incidentId: String,
    val creatorId: String,
    val creatorName: String,
    val incidentType: String,
    val severity: String,
    val description: String,
    val areaDescription: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationCapturedAt: Long? = null,
    val locationAccuracyMeters: Float? = null,
    val status: String,
    val primaryResponderId: String?,
    val primaryResponderName: String?,
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "1") val workflowVersion: Int = 1,
    val reporterSigningKey: String? = null,
    val selectionId: String? = null,
    val selectionOfferId: String? = null,
    val selectionOfferRevision: Long? = null,
    val selectedHelperKey: String? = null,
    val selectionConfirmedAt: Long? = null,
    @ColumnInfo(defaultValue = "''") val title: String = ""
)
