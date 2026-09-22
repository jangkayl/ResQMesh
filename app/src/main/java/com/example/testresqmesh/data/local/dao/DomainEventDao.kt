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
}
