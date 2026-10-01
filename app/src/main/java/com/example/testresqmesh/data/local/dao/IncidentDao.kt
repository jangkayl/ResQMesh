package com.example.testresqmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.testresqmesh.data.local.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incidents ORDER BY createdAt DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE status != 'RESOLVED' AND status != 'CANCELLED' ORDER BY createdAt DESC")
    fun getActiveIncidents(): Flow<List<IncidentEntity>>

    // Terminal incidents must be advertised too, or a reconnecting peer can retain stale OPEN state.
    @Query("SELECT * FROM incidents ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentIncidentsForSync(limit: Int): List<IncidentEntity>

    @Query("SELECT * FROM incidents ORDER BY incidentId")
    suspend fun getSyncIncidents(): List<IncidentEntity>

    @Query("SELECT * FROM incidents WHERE incidentId = :incidentId LIMIT 1")
    suspend fun getIncidentById(incidentId: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE incidentId = :incidentId LIMIT 1")
    fun observeIncidentById(incidentId: String): Flow<IncidentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(incident: IncidentEntity): Long

    @Query("DELETE FROM incidents WHERE incidentId = :incidentId")
    suspend fun deleteIncident(incidentId: String): Int
}
