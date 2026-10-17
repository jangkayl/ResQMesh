package com.example.testresqmesh.core.model

/** Mirrors UserEntity 1:1. */
data class MeshUser(
    val userId: String,
    val deviceId: String,
    val displayName: String,
    val publicKey: String?,
    val createdAt: Long
)
