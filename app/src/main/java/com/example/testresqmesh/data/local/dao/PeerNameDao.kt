package com.example.testresqmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.testresqmesh.data.local.entity.PeerNameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerNameDao {
    @Query("SELECT * FROM peer_names")
    fun observeNames(): Flow<List<PeerNameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(name: PeerNameEntity): Long
}
