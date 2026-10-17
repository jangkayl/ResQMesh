package com.example.testresqmesh.data.mapper

import com.example.testresqmesh.core.model.MeshUser
import com.example.testresqmesh.data.local.entity.UserEntity

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
