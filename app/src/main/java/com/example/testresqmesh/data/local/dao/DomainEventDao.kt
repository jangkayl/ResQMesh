package com.example.testresqmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainEventDao {
    @Query("SELECT * FROM domain_events WHERE eventId = :eventId LIMIT 1")
    suspend fun getEventById(eventId: String): DomainEventEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM domain_events WHERE eventId = :eventId)")
    suspend fun hasEvent(eventId: String): Boolean

    @Query("SELECT * FROM domain_events WHERE entityId = :entityId ORDER BY logicalVersion ASC, timestamp ASC")
    fun observeEventsForEntity(entityId: String): Flow<List<DomainEventEntity>>

    @Query("SELECT * FROM domain_events WHERE entityId = :entityId ORDER BY logicalVersion ASC, timestamp ASC")
    suspend fun getEventsForEntity(entityId: String): List<DomainEventEntity>

    @Query("SELECT eventId FROM domain_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEventIds(limit: Int): List<String>

    @Query("SELECT * FROM domain_events WHERE eventId IN (:eventIds)")
    suspend fun getEventsByIds(eventIds: List<String>): List<DomainEventEntity>

    @Query("SELECT * FROM domain_events WHERE entityId = :entityId AND logicalVersion > :version ORDER BY logicalVersion ASC, timestamp ASC LIMIT :limit")
    suspend fun getEventsAfterVersion(entityId: String, version: Long, limit: Int): List<DomainEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: DomainEventEntity): Long

    @Query("UPDATE domain_events SET applied = :applied WHERE eventId = :eventId")
    suspend fun setApplied(eventId: String, applied: Boolean): Int

    @Query("UPDATE domain_events SET validationStatus = :status WHERE eventId = :eventId")
    suspend fun setValidationStatus(eventId: String, status: String): Int

    @Query("DELETE FROM domain_events WHERE eventId = :eventId AND validationStatus = 'REJECTED'")
    suspend fun deleteRejectedEvent(eventId: String): Int

    @Query("SELECT * FROM domain_events WHERE entityType = 'INCIDENT' ORDER BY entityId, eventId")
    suspend fun getIncidentHistory(): List<DomainEventEntity>

    @Query("SELECT * FROM domain_events WHERE entityType = 'INCIDENT' ORDER BY entityId, eventId")
    fun observeIncidentHistory(): Flow<List<DomainEventEntity>>

    @Query("UPDATE domain_events SET applied = 0 WHERE entityId = :incidentId AND validationStatus = 'ACCEPTED'")
    suspend fun resetIncidentApplication(incidentId: String): Int

    @Query("SELECT * FROM domain_events WHERE applied = 1 ORDER BY rowid ASC LIMIT :limit OFFSET :offset")
    suspend fun getSyncPage(limit: Int, offset: Int): List<DomainEventEntity>

    @Query("SELECT * FROM domain_events WHERE entityId = :incidentId AND applied = 0 AND validationStatus != 'REJECTED' ORDER BY timestamp ASC, eventId ASC")
    suspend fun getUnappliedForIncident(incidentId: String): List<DomainEventEntity>

}
