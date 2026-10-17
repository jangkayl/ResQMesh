package edu.cit.resqmesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey val macAddress: String,
    val nodeName: String,
    val publicKey: String?,
    val lastSeenTimestamp: Long,
    val isBlocked: Boolean
)