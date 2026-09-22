package com.example.testresqmesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val deviceId: String,
    val displayName: String,
    val publicKey: String?,
    val createdAt: Long
)
