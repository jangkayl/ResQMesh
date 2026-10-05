package com.example.testresqmesh.data.repository

import com.example.testresqmesh.data.repository.incident.IncidentCommands
import com.example.testresqmesh.data.repository.incident.IncidentEventIngestor
import com.example.testresqmesh.data.repository.incident.LegacyIncidentProjection
import com.example.testresqmesh.data.repository.incident.LegacyIncidentSync
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.data.local.dao.DomainEventDao
import com.example.testresqmesh.data.local.dao.IncidentDao
import com.example.testresqmesh.data.local.dao.IncidentOfferDao
import com.example.testresqmesh.data.local.AppDatabase
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import androidx.room.withTransaction
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.json.JSONObject
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
class IncidentRepository(
    private val incidentDao: IncidentDao,
    private val domainEventDao: DomainEventDao,
    private val identityManager: IdentityProvider,
    private val networkGateway: MeshNetworkGateway,
    private val repositoryScope: CoroutineScope,
    private val readyPeerEvents: MeshReadyPeerEvents? = null,
    private val offerDao: IncidentOfferDao? = null,
    private val database: AppDatabase? = null,
    private val eventSigning: IncidentEventSigning? = null
) {
    private val ingestor: IncidentEventIngestor = createEventIngestor()
    private val helpWorkflow = if (offerDao != null && eventSigning != null)
        IncidentHelpWorkflow(database, incidentDao, offerDao, domainEventDao, identityManager,
            eventSigning, ::broadcastDomainEvent) else null
    private val syncStore = object : IncidentSyncStore {
        override suspend fun snapshot(): IncidentSyncSnapshot {
            suspend fun read(): IncidentSyncSnapshot {
                val incidents = incidentDao.getSyncIncidents()
                val offers = incidents.flatMap { offerDao?.getForIncident(it.incidentId).orEmpty() }
                return IncidentSyncSnapshot(domainEventDao.getIncidentHistory(), incidents, offers)
            }
            return if (database != null) database.withTransaction { read() } else read()
        }
        override suspend fun ingest(json: JSONObject): IncidentIngestionResult = ingestIncomingEventJson(json.toString())
        override suspend fun replay(incidentId: String) = replayPendingForIncident(incidentId)
        override suspend fun rebuild(incidentId: String) = rebuildProjection(incidentId)
    }
    private val commands: IncidentCommands = createCommands()
    private val projection: LegacyIncidentProjection = createLegacyIncidentProjection()
    private val legacySync: LegacyIncidentSync = createLegacyIncidentSync()

    private val syncCoordinator = readyPeerEvents?.let {
        IncidentSyncCoordinator(networkGateway, it, syncStore, repositoryScope, ::startLegacyReconciliationWithPeer)
    }

    init {
        helpWorkflow?.let { workflow ->
            repositoryScope.launch {
                incidentDao.getAllIncidents().collect { incidents ->
                    incidents.filter { it.workflowVersion == 2 && it.selectionId != null }.forEach {
                        workflow.reconcileWithdrawnSelection(it.incidentId)
                    }
                }
            }
        }
        networkGateway.onDomainEvent = { endpointId, payload ->
            repositoryScope.launch {
                val result = ingestIncomingEventJson(payload.text)
                if (result == IncidentIngestionResult.APPLIED) {
                    forwardAcceptedDomainEvent(payload, endpointId)
                }
                val incidentId = runCatching { JSONObject(payload.text).optString("entityId") }.getOrDefault("")
                replayPendingForIncident(incidentId)
            }
        }
        networkGateway.onEventSyncRequest = { endpointId, payload ->
            repositoryScope.launch {
                if (runCatching { JSONObject(payload.text).optInt("protocol") == 2 }.getOrDefault(false))
                    syncCoordinator?.receive(endpointId, payload)
                else handleSyncRequest(endpointId, payload.text)
            }
        }
        networkGateway.onEventSyncResponse = { endpointId, payload ->
            repositoryScope.launch {
                if (runCatching { JSONObject(payload.text).optInt("protocol") == 2 }.getOrDefault(false))
                    syncCoordinator?.response(endpointId, payload)
                else handleSyncResponse(endpointId, payload.text)
            }
        }
        repositoryScope.launch {
            domainEventDao.getIncidentHistory().filter { !it.applied && it.validationStatus != "REJECTED" }
                .map { it.entityId }.distinct().forEach { replayPendingForIncident(it) }
        }
        if (syncCoordinator != null) {
            repositoryScope.launch { domainEventDao.observeIncidentHistory().collect { syncCoordinator.changed() } }
            repositoryScope.launch { incidentDao.getAllIncidents().collect { syncCoordinator.changed() } }
            offerDao?.let { dao -> repositoryScope.launch {
                dao.observeAllActiveOffers().collect { syncCoordinator.changed() }
            } }
        }
    }

    fun getAllIncidents(): Flow<List<IncidentEntity>> = incidentDao.getAllIncidents()
    internal suspend fun syncSnapshot(): IncidentSyncSnapshot = syncStore.snapshot()
    fun getActiveIncidents(): Flow<List<IncidentEntity>> = incidentDao.getActiveIncidents()
    fun observeIncidentById(id: String): Flow<IncidentEntity?> = incidentDao.observeIncidentById(id)
    fun observeEventsForIncident(id: String): Flow<List<DomainEventEntity>> = domainEventDao.observeEventsForEntity(id)
    fun observeOffersForIncident(id: String): Flow<List<IncidentOfferEntity>>? = helpWorkflow?.observeOffers(id)
    fun observeAllActiveOffers(): Flow<List<IncidentOfferEntity>>? = helpWorkflow?.observeAllActiveOffers()
    fun localIncidentSigningKey(): String? = runCatching { eventSigning?.publicKey }.getOrNull()
    suspend fun offerHelp(id: String, note: String): Boolean = helpWorkflow?.offerHelp(id, note) ?: false
    suspend fun withdrawOffer(id: String): Boolean = helpWorkflow?.withdrawOffer(id) ?: false
    suspend fun selectLead(id: String, offerId: String): Boolean = helpWorkflow?.selectLead(id, offerId) ?: false
    suspend fun confirmLead(id: String): Boolean = helpWorkflow?.confirmLead(id) ?: false
    suspend fun declineLead(id: String): Boolean = helpWorkflow?.declineLead(id) ?: false
    suspend fun revokeLead(id: String): Boolean = helpWorkflow?.revokeLead(id) ?: false
    suspend fun resolveHelp(id: String): Boolean = helpWorkflow?.resolve(id) ?: false
    suspend fun cancelHelp(id: String): Boolean = helpWorkflow?.cancel(id) ?: false

    suspend fun createIncident(
        incidentType: String,
        severity: String,
        description: String,
        areaDescription: String,
        latitude: Double? = null,
        longitude: Double? = null,
        locationCapturedAt: Long? = null,
        locationAccuracyMeters: Float? = null,
        title: String = ""
    ): IncidentEntity = commands.createIncident(incidentType, severity, description, areaDescription, latitude, longitude, locationCapturedAt, locationAccuracyMeters, title)

    suspend fun acknowledgeIncident(incidentId: String): Boolean = commands.acknowledgeIncident(incidentId)

    suspend fun assignIncident(incidentId: String): Boolean = commands.assignIncident(incidentId)

    suspend fun startResponding(incidentId: String): Boolean = commands.startResponding(incidentId)

    suspend fun resolveIncident(incidentId: String): Boolean = commands.resolveIncident(incidentId)

    suspend fun cancelIncident(incidentId: String): Boolean = commands.cancelIncident(incidentId)

    suspend fun releaseAssignment(incidentId: String): Boolean = commands.releaseAssignment(incidentId)

    suspend fun applyIncomingEventJson(eventJson: String): Boolean = ingestor.applyIncomingEventJson(eventJson)

    suspend fun ingestIncomingEventJson(eventJson: String): IncidentIngestionResult = ingestor.ingestIncomingEventJson(eventJson)

    suspend fun applyIncomingEvent(event: DomainEventEntity): Boolean = ingestor.applyIncomingEvent(event)

    suspend fun ingestIncomingEvent(event: DomainEventEntity): IncidentIngestionResult = ingestor.ingestIncomingEvent(event)





    private suspend fun applyLegacyProjection(event: DomainEventEntity): Boolean = projection.applyLegacyProjection(event)

    private suspend fun rejectIncoming(event: DomainEventEntity, reason: String): Boolean = projection.rejectIncoming(event, reason)

    private fun broadcastDomainEvent(event: DomainEventEntity, isP0: Boolean) {
        repositoryScope.launch {
            val eventJson = JSONObject().apply {
                put("eventId", event.eventId)
                put("entityId", event.entityId)
                put("entityType", event.entityType)
                put("eventType", event.eventType)
                put("actorId", event.actorId)
                put("actorName", event.actorName)
                put("logicalVersion", event.logicalVersion)
                put("timestamp", event.timestamp)
                put("payloadJson", event.payloadJson)
                event.signature?.let { put("signature", it) }
            }.toString()

            val payload = MeshPayload(
                id = UUID.randomUUID().toString(),
                type = "DOMAIN_EVENT",
                senderName = networkGateway.myDeviceName,
                senderNodeId = networkGateway.myNodeId,
                text = eventJson,
                isSOS = isP0,
                ttl = networkGateway.currentMeshTtl()
            )

            val bytes = ProtoBuf.encodeToByteArray(payload)
            if (isP0) {
                val result = networkGateway.broadcastPriorityPayload(bytes)
                AppLogger.d("INCIDENT_SYNC", "BROADCAST event=${event.eventId} accepted=${result.acceptedCount} targets=${result.neighbors.size}")
            } else {
                val result = networkGateway.broadcastPayload(bytes)
                AppLogger.d("INCIDENT_SYNC", "BROADCAST event=${event.eventId} accepted=${result.acceptedCount} targets=${result.neighbors.size}")
            }
            syncCoordinator?.changed()
        }
    }







    private fun forwardAcceptedDomainEvent(payload: MeshPayload, sourceEndpointId: String) {
        val canRelay = if (payload.ttl > 0) payload.ttl > 1 else payload.relayHopCount < 6
        if (!canRelay) return
        val updated = payload.copy(
            routePath = payload.routePath + networkGateway.myDeviceName,
            relayHopCount = payload.relayHopCount + 1,
            ttl = if (payload.ttl > 0) payload.ttl - 1 else 0
        )
        val result = networkGateway.broadcastPayload(ProtoBuf.encodeToByteArray(updated), sourceEndpointId)
        AppLogger.d("INCIDENT_SYNC", "RELAY accepted=${result.acceptedCount} targets=${result.neighbors.size}")
    }

    /**
     * Reconnection Synchronization: sends recent event IDs to a newly connected peer.
     */
    fun startReconciliationWithPeer(endpointId: String) {
        if (syncCoordinator != null) syncCoordinator.triggerEndpoint(endpointId)
        else startLegacyReconciliationWithPeer(endpointId)
    }

    private fun startLegacyReconciliationWithPeer(endpointId: String) = legacySync.startLegacyReconciliationWithPeer(endpointId)

    suspend fun handleSyncRequest(endpointId: String, text: String) = legacySync.handleSyncRequest(endpointId, text)

    suspend fun handleSyncResponse(endpointId: String, text: String) = legacySync.handleSyncResponse(endpointId, text)









    private suspend fun replayPendingForIncident(incidentId: String) = ingestor.replayPendingForIncident(incidentId)

    private suspend fun rebuildProjection(incidentId: String) = ingestor.rebuildProjection(incidentId)

    private fun createCommands() = IncidentCommands(
        incidentDao,
        domainEventDao,
        identityManager,
        database,
        eventSigning,
        { helpWorkflow },
        ::broadcastDomainEvent
    )

    private fun createEventIngestor() = IncidentEventIngestor(
        incidentDao,
        domainEventDao,
        offerDao,
        database,
        { helpWorkflow },
        { syncCoordinator },
        ::broadcastDomainEvent,
        ::applyLegacyProjection,
        ::rejectIncoming
    )

    private fun createLegacyIncidentProjection() = LegacyIncidentProjection(
        incidentDao,
        domainEventDao,
        database,
        eventSigning
    )

    private fun createLegacyIncidentSync() = LegacyIncidentSync(
        incidentDao,
        domainEventDao,
        networkGateway,
        repositoryScope,
        ::applyIncomingEventJson,
        ::replayPendingForIncident,
        ::startReconciliationWithPeer
    )
}
