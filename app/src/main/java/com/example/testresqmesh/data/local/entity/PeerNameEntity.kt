package com.example.testresqmesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peer_names")
data class PeerNameEntity(
    @PrimaryKey val nodeId: String,
    val fullName: String
)
