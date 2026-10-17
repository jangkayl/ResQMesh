package com.example.testresqmesh.core.model

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
