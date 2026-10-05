package com.example.testresqmesh.data.repository.incident

import com.example.testresqmesh.core.model.EventType
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.data.local.dao.DomainEventDao
import com.example.testresqmesh.data.local.dao.IncidentDao
import com.example.testresqmesh.data.local.dao.IncidentOfferDao
import com.example.testresqmesh.data.local.AppDatabase
import androidx.room.withTransaction
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import org.json.JSONObject
import com.example.testresqmesh.data.repository.IncidentHelpWorkflow
import com.example.testresqmesh.data.repository.IncidentIngestionResult
import com.example.testresqmesh.data.repository.IncidentSyncCoordinator
import com.example.testresqmesh.data.repository.IncidentSyncSnapshot

@OptIn(ExperimentalSerializationApi::class)
internal class IncidentEventIngestor(
    private val incidentDao: IncidentDao,
    private val domainEventDao: DomainEventDao,
    private val offerDao: IncidentOfferDao?,
    private val database: AppDatabase?,
    private val helpWorkflowProvider: () -> IncidentHelpWorkflow?,
    private val syncCoordinatorProvider: () -> IncidentSyncCoordinator?,
    private val broadcast: (DomainEventEntity, Boolean) -> Unit,
    private val legacyProjection: suspend (DomainEventEntity) -> Boolean,
    private val rejectEvent: suspend (DomainEventEntity, String) -> Boolean
) {
    private val helpWorkflow get() = helpWorkflowProvider()
    private val syncCoordinator get() = syncCoordinatorProvider()
    private fun broadcastDomainEvent(event: DomainEventEntity, isP0: Boolean) = broadcast(event, isP0)
    private suspend fun applyLegacyProjection(event: DomainEventEntity) = legacyProjection(event)
    private suspend fun rejectIncoming(event: DomainEventEntity, reason: String) = rejectEvent(event, reason)
    private val projectionMutex = Mutex()
    private var rebuilding = false

    suspend fun applyIncomingEventJson(eventJson: String): Boolean =
        ingestIncomingEventJson(eventJson) == IncidentIngestionResult.APPLIED

    suspend fun ingestIncomingEventJson(eventJson: String): IncidentIngestionResult {
        return try {
            val json = JSONObject(eventJson)
            val event = DomainEventEntity(
                eventId = json.getString("eventId"),
                entityId = json.getString("entityId"),
                entityType = json.getString("entityType"),
                eventType = json.getString("eventType"),
                actorId = json.getString("actorId"),
                actorName = json.optString("actorName", ""),
                logicalVersion = json.getLong("logicalVersion"),
                timestamp = json.getLong("timestamp"),
                payloadJson = json.optString("payloadJson", "{}"),
                signature = json.optString("signature").takeIf { it.isNotBlank() },
                applied = true
            )
            ingestIncomingEvent(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.d("INCIDENT_REPO", "Failed to deserialize domain event")
            IncidentIngestionResult.REJECTED
        }
    }

    suspend fun applyIncomingEvent(event: DomainEventEntity): Boolean =
        ingestIncomingEvent(event) == IncidentIngestionResult.APPLIED

    suspend fun ingestIncomingEvent(event: DomainEventEntity): IncidentIngestionResult = projectionMutex.withLock {
        ingestEventLocked(event)
    }

    private suspend fun ingestEventLocked(event: DomainEventEntity): IncidentIngestionResult {
        var prior = domainEventDao.getEventById(event.eventId)
        // Invalid first arrivals cannot reserve an ID against a later authentic original.
        if (prior?.validationStatus == "REJECTED" &&
            (prior.signature != event.signature || IncidentSyncSnapshot.eventHash(prior) != IncidentSyncSnapshot.eventHash(event))) {
            domainEventDao.deleteRejectedEvent(event.eventId)
            prior = null
        }
        if (prior != null && IncidentSyncSnapshot.eventHash(prior) != IncidentSyncSnapshot.eventHash(event)) {
            AppLogger.d("INCIDENT_SYNC", "REJECTED reason=conflicting-event-id")
            return IncidentIngestionResult.REJECTED
        }
        if (prior?.validationStatus == "REJECTED") return IncidentIngestionResult.REJECTED
        if (prior?.applied == true) return IncidentIngestionResult.DUPLICATE
        val applied = applyIncomingEventProjection(event)
        val stored = domainEventDao.getEventById(event.eventId)
        val result = when {
            applied || stored?.applied == true -> IncidentIngestionResult.APPLIED
            stored != null && stored.validationStatus != "REJECTED" -> IncidentIngestionResult.DEFERRED
            else -> IncidentIngestionResult.REJECTED
        }
        if (result == IncidentIngestionResult.APPLIED) syncCoordinator?.changed()
        if (result == IncidentIngestionResult.DEFERRED)
            AppLogger.d("INCIDENT_SYNC", "DEPENDENCY_PENDING incident=${event.entityId}")
        return result
    }

    private suspend fun applyIncomingEventProjection(event: DomainEventEntity): Boolean {
        val existingIncident = incidentDao.getIncidentById(event.entityId)
        if (existingIncident?.workflowVersion == 2 && event.eventType !in IncidentHelpWorkflow.TYPES &&
            event.eventType !in setOf(EventType.INCIDENT_RESOLVED.name, EventType.INCIDENT_CANCELLED.name)) {
            return rejectIncoming(event, "legacy action is not allowed on a helper workflow")
        }
        if (event.eventType in IncidentHelpWorkflow.TYPES ||
            (event.eventType in setOf(EventType.INCIDENT_RESOLVED.name, EventType.INCIDENT_CANCELLED.name) &&
                existingIncident?.workflowVersion == 2)) {
            return if (rebuilding) helpWorkflow?.applyDuringReplay(event) ?: false else helpWorkflow?.apply(event) ?: false
        }
        return if (database != null) database.withTransaction { applyLegacyProjection(event) }
            else applyLegacyProjection(event)
    }

    suspend fun replayPendingForIncident(incidentId: String) {
        if (incidentId.isBlank()) return
        helpWorkflow?.reconcileWithdrawnSelection(incidentId)
        while (true) {
            var progressed = false
            // Helper revision streams do not share the reporter's logical version. Drain
            // available offer changes before reporter closure, including legacy empty payloads.
            domainEventDao.getUnappliedForIncident(incidentId)
                .sortedBy { if (it.eventType in IncidentHelpWorkflow.OFFER_TYPES) 0 else 1 }
                .forEach { pending ->
                if (applyIncomingEvent(pending)) {
                    progressed = true
                    broadcastDomainEvent(pending, pending.eventType == EventType.INCIDENT_LEAD_SELECTED.name ||
                        pending.eventType == EventType.INCIDENT_CANCELLED.name)
                }
            }
            if (!progressed) return
        }
    }

    suspend fun rebuildProjection(incidentId: String) = projectionMutex.withLock {
        suspend fun rebuild() {
            suspend fun work() {
                val original = domainEventDao.getEventsForEntity(incidentId)
                    .filter { it.validationStatus == "ACCEPTED" }
                if (original.none { it.eventType == EventType.INCIDENT_CREATED.name }) return
                incidentDao.deleteIncident(incidentId)
                offerDao?.deleteForIncident(incidentId)
                domainEventDao.resetIncidentApplication(incidentId)
                rebuilding = true
                try {
                    var pending = original.sortedWith(compareBy<DomainEventEntity> {
                        if (it.eventType == EventType.INCIDENT_CREATED.name) 0 else if (it.eventType in IncidentHelpWorkflow.OFFER_TYPES) 1 else 2
                    }.thenBy { it.logicalVersion }.thenBy { it.eventId })
                    while (pending.isNotEmpty()) {
                        var progressed = false
                        pending.forEach { if (ingestEventLocked(it) == IncidentIngestionResult.APPLIED) progressed = true }
                        pending = pending.filter { domainEventDao.getEventById(it.eventId)?.applied != true }
                        if (!progressed) break
                    }
                    check(incidentDao.getIncidentById(incidentId) != null)
                    check(pending.isEmpty()) { "history replay incomplete" }
                    AppLogger.d("INCIDENT_SYNC", "PROJECTION_REBUILT incident=$incidentId")
                } finally { rebuilding = false }
            }
            if (database != null) database.withTransaction { work() } else work()
        }
        val replayWorkflow = helpWorkflow
        if (replayWorkflow != null) replayWorkflow.withReplayLock { rebuild() } else rebuild()
    }
}
