package com.example.testresqmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.testresqmesh.data.local.entity.NodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes")
    fun getAllNodes(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE macAddress = :macAddress")
    suspend fun getNodeByMac(macAddress: String): NodeEntity?

    @Query("SELECT * FROM nodes WHERE nodeName = :nodeName")
    suspend fun getNodeByName(nodeName: String): NodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNode(node: NodeEntity): Long

    @Query("UPDATE nodes SET isBlocked = :isBlocked WHERE nodeName = :nodeName")
    suspend fun setBlockedStatus(nodeName: String, isBlocked: Boolean): Int
}
