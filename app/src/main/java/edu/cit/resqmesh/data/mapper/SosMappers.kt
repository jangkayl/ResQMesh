package edu.cit.resqmesh.data.mapper

import edu.cit.resqmesh.core.model.SosAlert
import edu.cit.resqmesh.data.local.entity.SosAlertEntity

fun SosAlertEntity.toModel(): SosAlert = SosAlert(
    sosId = sosId,
    originNodeId = originNodeId,
    originName = originName,
    signingKey = signingKey,
    emergencyType = emergencyType,
    revision = revision,
    ended = ended,
    createdAt = createdAt,
    updatedAt = updatedAt,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    locationCapturedAt = locationCapturedAt,
    locallySilenced = locallySilenced,
    eventJson = eventJson,
    transmission = transmission
)

fun SosAlert.toEntity(): SosAlertEntity = SosAlertEntity(
    sosId = sosId,
    originNodeId = originNodeId,
    originName = originName,
    signingKey = signingKey,
    emergencyType = emergencyType,
    revision = revision,
    ended = ended,
    createdAt = createdAt,
    updatedAt = updatedAt,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    locationCapturedAt = locationCapturedAt,
    locallySilenced = locallySilenced,
    eventJson = eventJson,
    transmission = transmission
)
