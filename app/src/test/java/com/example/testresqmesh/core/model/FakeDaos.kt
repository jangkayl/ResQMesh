package com.example.testresqmesh.core.model

import com.example.testresqmesh.data.local.dao.DomainEventDao
import com.example.testresqmesh.data.local.dao.IncidentDao
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class IncidentEntityFakeDao : IncidentDao {
    private val store = mutableMapOf<String, IncidentEntity>()
    private val flow = MutableStateFlow<Map<String, IncidentEntity>>(emptyMap())

    override fun getAllIncidents(): Flow<List<IncidentEntity>> =
        flow.map { it.values.toList().sortedByDescending { it.createdAt } }

    override fun getActiveIncidents(): Flow<List<IncidentEntity>> =
        flow.map { it.values.filter { it.status != "RESOLVED" && it.status != "CANCELLED" }.sortedByDescending { it.createdAt } }

    override suspend fun getRecentIncidentsForSync(limit: Int): List<IncidentEntity> =
        store.values.sortedByDescending { it.updatedAt }.take(limit)

    override suspend fun getSyncIncidents(): List<IncidentEntity> = store.values.sortedBy { it.incidentId }

    override suspend fun getIncidentById(incidentId: String): IncidentEntity? = store[incidentId]

    override fun observeIncidentById(incidentId: String): Flow<IncidentEntity?> =
        flow.map { it[incidentId] }

    override suspend fun insertOrUpdate(incident: IncidentEntity): Long {
        store[incident.incidentId] = incident
        flow.value = store.toMap()
        return 1L
    }

    override suspend fun deleteIncident(incidentId: String): Int {
        val removed = store.remove(incidentId)
        flow.value = store.toMap()
        return if (removed != null) 1 else 0
    }
}

class DomainEventEntityFakeDao : DomainEventDao {
    private val store = mutableMapOf<String, DomainEventEntity>()
    private val flow = MutableStateFlow<Map<String, DomainEventEntity>>(emptyMap())

    override suspend fun getEventById(eventId: String): DomainEventEntity? = store[eventId]

    override suspend fun hasEvent(eventId: String): Boolean = store.containsKey(eventId)

    override fun observeEventsForEntity(entityId: String): Flow<List<DomainEventEntity>> =
        flow.map { it.values.filter { it.entityId == entityId }.sortedBy { it.logicalVersion } }

    override suspend fun getEventsForEntity(entityId: String): List<DomainEventEntity> =
        store.values.filter { it.entityId == entityId }.sortedBy { it.logicalVersion }

    override suspend fun getRecentEventIds(limit: Int): List<String> =
        store.values.sortedByDescending { it.timestamp }.take(limit).map { it.eventId }

    override suspend fun getEventsByIds(eventIds: List<String>): List<DomainEventEntity> =
        eventIds.mapNotNull { store[it] }

    override suspend fun getEventsAfterVersion(entityId: String, version: Long, limit: Int): List<DomainEventEntity> =
        store.values.filter { it.entityId == entityId && it.logicalVersion > version }
            .sortedWith(compareBy<DomainEventEntity> { it.logicalVersion }.thenBy { it.timestamp })
            .take(limit)

    override suspend fun insertEvent(event: DomainEventEntity): Long {
        if (store.containsKey(event.eventId)) return -1L
        store[event.eventId] = event
        flow.value = store.toMap()
        return 1L
    }

    override suspend fun setApplied(eventId: String, applied: Boolean): Int {
        store[eventId]?.let { store[eventId] = it.copy(applied = applied) }
        flow.value = store.toMap()
        return if (eventId in store) 1 else 0
    }

    override suspend fun setValidationStatus(eventId: String, status: String): Int {
        store[eventId]?.let { store[eventId] = it.copy(validationStatus = status) }
        flow.value = store.toMap()
        return if (eventId in store) 1 else 0
    }
    override suspend fun deleteRejectedEvent(eventId: String): Int {
        if (store[eventId]?.validationStatus != "REJECTED") return 0
        store.remove(eventId); flow.value = store.toMap(); return 1
    }

    override suspend fun getIncidentHistory(): List<DomainEventEntity> = store.values.filter { it.entityType == "INCIDENT" }
    override fun observeIncidentHistory(): Flow<List<DomainEventEntity>> = flow.map { it.values.filter { e -> e.entityType == "INCIDENT" } }
    override suspend fun resetIncidentApplication(incidentId: String): Int {
        val ids = store.values.filter { it.entityId == incidentId && it.validationStatus == "ACCEPTED" }.map { it.eventId }
        ids.forEach { setApplied(it, false) }
        return ids.size
    }

    override suspend fun getSyncPage(limit: Int, offset: Int): List<DomainEventEntity> =
        store.values.filter { it.applied }
            .drop(offset).take(limit)

    override suspend fun getUnappliedForIncident(incidentId: String): List<DomainEventEntity> =
        store.values.filter { it.entityId == incidentId && !it.applied && it.validationStatus != "REJECTED" }
            .sortedWith(compareBy<DomainEventEntity> { it.timestamp }.thenBy { it.eventId })
}
