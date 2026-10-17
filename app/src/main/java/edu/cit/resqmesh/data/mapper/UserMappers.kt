package edu.cit.resqmesh.data.mapper

import edu.cit.resqmesh.core.model.MeshUser
import edu.cit.resqmesh.data.local.entity.UserEntity

fun UserEntity.toModel(): MeshUser = MeshUser(
    userId = userId,
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    createdAt = createdAt
)

fun MeshUser.toEntity(): UserEntity = UserEntity(
    userId = userId,
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    createdAt = createdAt
)
