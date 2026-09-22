package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.DomainEvent
import com.example.testresqmesh.core.model.EntityType
import com.example.testresqmesh.core.model.EventType
import com.example.testresqmesh.core.model.IncidentState
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.TerminalLogCategory
import com.example.testresqmesh.core.utils.TerminalLogLevel
import com.example.testresqmesh.data.local.dao.DomainEventDao
import com.example.testresqmesh.data.local.dao.IncidentDao
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
class IncidentRepository(
    private val incidentDao: IncidentDao,
    private val domainEventDao: DomainEventDao,
    private val identityManager: IdentityProvider,
    private val networkGateway: MeshNetworkGateway,
    private val repositoryScope: CoroutineScope,
    private val readyPeerEvents: MeshReadyPeerEvents? = null
) {
    init {
        networkGateway.onDomainEvent = { endpointId, payload ->
            repositoryScope.launch {
                if (applyIncomingEventJson(payload.text)) {
                    forwardAcceptedDomainEvent(payload, endpointId)
                }
            }
        }
        networkGateway.onEventSyncRequest = { endpointId, payload ->
            repositoryScope.launch {
                handleSyncRequest(endpointId, payload.text)
            }
        }
        networkGateway.onEventSyncResponse = { endpointId, payload ->
            repositoryScope.launch {
                handleSyncResponse(endpointId, payload.text)
            }
        }
        readyPeerEvents?.let { readyEvents ->
            repositoryScope.launch {
                readyEvents.events.collect { peer ->
                    if (networkGateway.hasReadyEndpoint(peer.endpointId)) {
                        startReconciliationWithPeer(peer.endpointId)
                    }
                }
            }
        }
    }

    fun getAllIncidents(): Flow<List<IncidentEntity>> = incidentDao.getAllIncidents()
    fun getActiveIncidents(): Flow<List<IncidentEntity>> = incidentDao.getActiveIncidents()
    fun observeIncidentById(id: String): Flow<IncidentEntity?> = incidentDao.observeIncidentById(id)
    fun observeEventsForIncident(id: String): Flow<List<DomainEventEntity>> = domainEventDao.observeEventsForEntity(id)

    suspend fun createIncident(
        incidentType: String,
        severity: String,
        description: String,
        areaDescription: String,
        latitude: Double? = null,
        longitude: Double? = null,
        locationCapturedAt: Long? = null,
        locationAccuracyMeters: Float? = null
    ): IncidentEntity {
        val user = identityManager.getOrCreateUser()
        val incidentId = "INC-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
        val now = System.currentTimeMillis()

        val incident = IncidentEntity(
            incidentId = incidentId,
            creatorId = user.userId,
            creatorName = user.displayName,
            incidentType = incidentType,
            severity = severity,
            description = description,
            areaDescription = areaDescription,
            latitude = latitude,
            longitude = longitude,
            locationCapturedAt = locationCapturedAt,
            locationAccuracyMeters = locationAccuracyMeters,
            status = IncidentState.OPEN.name,
            primaryResponderId = null,
            primaryResponderName = null,
            version = 1,
            createdAt = now,
            updatedAt = now
        )

        val payloadJson = JSONObject().apply {
            put("incidentType", incidentType)
            put("severity", severity)
            put("description", description)
            put("areaDescription", areaDescription)
            latitude?.let { put("latitude", it) }
            longitude?.let { put("longitude", it) }
            locationCapturedAt?.let { put("locationCapturedAt", it) }
            locationAccuracyMeters?.let { put("locationAccuracyMeters", it) }
        }.toString()

        val event = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_CREATED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = 1,
            timestamp = now,
            payloadJson = payloadJson,
            applied = true
        )

        incidentDao.insertOrUpdate(incident)
        domainEventDao.insertEvent(event)

        AppLogger.event(
            category = TerminalLogCategory.SYSTEM,
            event = "INCIDENT_CREATED",
            message = "Created new SOS Incident $incidentId by ${user.displayName}",
            level = TerminalLogLevel.INFO,
            tag = "INCIDENT_REPO"
        )

        broadcastDomainEvent(event, isP0 = true)
        return incident
    }

    suspend fun acknowledgeIncident(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_ACKNOWLEDGED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val newVersion = current.version + 1
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

        val updated = current.copy(
            status = IncidentState.ACKNOWLEDGED.name,
            version = newVersion,
            updatedAt = now
        )

        val event = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_ACKNOWLEDGED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = newVersion,
            timestamp = now,
            payloadJson = "{}",
            applied = true
        )

        incidentDao.insertOrUpdate(updated)
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = false)
        return true
    }

    suspend fun assignIncident(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_ASSIGNED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val newVersion = current.version + 1
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

        val updated = current.copy(
            status = IncidentState.ASSIGNED.name,
            primaryResponderId = user.userId,
            primaryResponderName = user.displayName,
            version = newVersion,
            updatedAt = now
        )

        val payloadJson = JSONObject().apply {
            put("responderId", user.userId)
            put("responderName", user.displayName)
        }.toString()

        val event = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_ASSIGNED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = newVersion,
            timestamp = now,
            payloadJson = payloadJson,
            applied = true
        )

        incidentDao.insertOrUpdate(updated)
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = true)
        return true
    }

    suspend fun startResponding(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_RESPONSE_STARTED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val newVersion = current.version + 1
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

        val updated = current.copy(
            status = IncidentState.RESPONDING.name,
            version = newVersion,
            updatedAt = now
        )

        val event = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_RESPONSE_STARTED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = newVersion,
            timestamp = now,
            payloadJson = "{}",
            applied = true
        )

        incidentDao.insertOrUpdate(updated)
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = false)
        return true
    }

    suspend fun resolveIncident(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_RESOLVED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val newVersion = current.version + 1
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

        val updated = current.copy(
            status = IncidentState.RESOLVED.name,
            version = newVersion,
            updatedAt = now
        )

        val event = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_RESOLVED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = newVersion,
            timestamp = now,
            payloadJson = "{}",
            applied = true
        )

        incidentDao.insertOrUpdate(updated)
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = false)
        return true
    }

    suspend fun cancelIncident(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_CANCELLED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val newVersion = current.version + 1
        val eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

        val updated = current.copy(
            status = IncidentState.CANCELLED.name,
            version = newVersion,
            updatedAt = now
        )

        val event = DomainEventEntity(
            eventId = eventId,
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_CANCELLED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = newVersion,
            timestamp = now,
            payloadJson = "{}",
            applied = true
        )

        incidentDao.insertOrUpdate(updated)
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = true)
        return true
    }

    suspend fun releaseAssignment(incidentId: String): Boolean {
        val current = incidentDao.getIncidentById(incidentId) ?: return false
        val user = identityManager.getOrCreateUser()
        if (!IncidentPolicy.mayPerform(current, EventType.INCIDENT_ASSIGNMENT_RELEASED.name, user.userId)) return false
        val now = System.currentTimeMillis()
        val event = DomainEventEntity(
            eventId = "EVT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}",
            entityId = incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_ASSIGNMENT_RELEASED.name,
            actorId = user.userId,
            actorName = user.displayName,
            logicalVersion = current.version + 1,
            timestamp = now,
            payloadJson = "{}",
            applied = true
        )
        incidentDao.insertOrUpdate(current.copy(
            status = IncidentState.ACKNOWLEDGED.name,
            primaryResponderId = null,
            primaryResponderName = null,
            version = event.logicalVersion,
            updatedAt = now
        ))
        domainEventDao.insertEvent(event)
        broadcastDomainEvent(event, isP0 = false)
        return true
    }

    suspend fun applyIncomingEventJson(eventJson: String): Boolean {
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
                applied = true
            )
            applyIncomingEvent(event)
        } catch (e: Exception) {
            AppLogger.d("INCIDENT_REPO", "Failed to deserialize domain event: ${e.message}")
            false
        }
    }

    suspend fun applyIncomingEvent(event: DomainEventEntity): Boolean {
        // Deduplication: Has this exact business event been recorded already?
        if (domainEventDao.hasEvent(event.eventId)) {
            AppLogger.d("INCIDENT_REPO", "Deduplication: Event ${event.eventId} already processed, skipping.")
            return false
        }

        if (event.entityType != EntityType.INCIDENT.name) {
            domainEventDao.insertEvent(event)
            return true
        }

        val current = incidentDao.getIncidentById(event.entityId)
        val isCompetingAssignment = event.eventType == EventType.INCIDENT_ASSIGNED.name &&
            current?.status == IncidentState.ASSIGNED.name && event.logicalVersion == current.version
        if (event.eventType == EventType.INCIDENT_CREATED.name && event.logicalVersion != 1L) {
            domainEventDao.insertEvent(event.copy(applied = false))
            return false
        }
        if (event.eventType != EventType.INCIDENT_CREATED.name && !isCompetingAssignment) {
            if (!IncidentPolicy.mayPerform(current, event.eventType, event.actorId) ||
                event.logicalVersion != (current?.version ?: 0L) + 1L
            ) {
                domainEventDao.insertEvent(event.copy(applied = false))
                return false
            }
        }
        val applied = when (event.eventType) {
            EventType.INCIDENT_CREATED.name -> {
                if (current != null) {
                    AppLogger.d("INCIDENT_REPO", "Incident ${event.entityId} already exists, ignoring creation.")
                    false
                } else {
                    val p = JSONObject(event.payloadJson)
                    val newIncident = IncidentEntity(
                        incidentId = event.entityId,
                        creatorId = event.actorId,
                        creatorName = event.actorName,
                        incidentType = p.optString("incidentType", "Emergency"),
                        severity = p.optString("severity", "Critical"),
                        description = p.optString("description", ""),
                        areaDescription = p.optString("areaDescription", ""),
                        latitude = p.optionalDouble("latitude"),
                        longitude = p.optionalDouble("longitude"),
                        locationCapturedAt = p.optionalLong("locationCapturedAt"),
                        locationAccuracyMeters = p.optionalFloat("locationAccuracyMeters"),
                        status = IncidentState.OPEN.name,
                        primaryResponderId = null,
                        primaryResponderName = null,
                        version = event.logicalVersion,
                        createdAt = event.timestamp,
                        updatedAt = event.timestamp
                    )
                    incidentDao.insertOrUpdate(newIncident)
                    true
                }
            }
            EventType.INCIDENT_ACKNOWLEDGED.name -> {
                if (current != null) {
                    incidentDao.insertOrUpdate(
                        current.copy(
                            status = IncidentState.ACKNOWLEDGED.name,
                            version = event.logicalVersion,
                            updatedAt = event.timestamp
                        )
                    )
                    true
                } else false
            }
            EventType.INCIDENT_ASSIGNED.name -> {
                if (current != null) {
                    val p = JSONObject(event.payloadJson)
                    val newResponderId = p.optString("responderId", event.actorId)
                    val newResponderName = p.optString("responderName", event.actorName)
                    if (newResponderId != event.actorId) return rejectIncoming(event, "assignment responder differs from actor")

                    // Conflict resolution if already assigned
                    if (current.status == IncidentState.ASSIGNED.name) {
                        val existingAssignmentEvent = domainEventDao.getEventsForEntity(event.entityId)
                            .firstOrNull {
                                it.eventType == EventType.INCIDENT_ASSIGNED.name &&
                                    it.logicalVersion == current.version && it.applied
                            }
                        // Deterministic conflict winner: earliest timestamp, then event ID.
                        val isEarlier = event.timestamp < current.updatedAt ||
                                (event.timestamp == current.updatedAt &&
                                    event.eventId < (existingAssignmentEvent?.eventId ?: "~"))
                        if (isEarlier) {
                            incidentDao.insertOrUpdate(
                                current.copy(
                                    primaryResponderId = newResponderId,
                                    primaryResponderName = newResponderName,
                                    version = event.logicalVersion,
                                    updatedAt = event.timestamp
                                )
                            )
                            true
                        } else {
                            AppLogger.d("INCIDENT_REPO", "Assignment conflict: keeping existing responder ${current.primaryResponderName}")
                            false
                        }
                    } else if (IncidentPolicy.mayPerform(current, event.eventType, event.actorId)) {
                        incidentDao.insertOrUpdate(
                            current.copy(
                                status = IncidentState.ASSIGNED.name,
                                primaryResponderId = newResponderId,
                                primaryResponderName = newResponderName,
                                version = event.logicalVersion,
                                updatedAt = event.timestamp
                            )
                        )
                        true
                    } else false
                } else false
            }
            EventType.INCIDENT_RESPONSE_STARTED.name -> {
                if (current != null) {
                    incidentDao.insertOrUpdate(
                        current.copy(
                            status = IncidentState.RESPONDING.name,
                            version = event.logicalVersion,
                            updatedAt = event.timestamp
                        )
                    )
                    true
                } else false
            }
            EventType.INCIDENT_RESOLVED.name -> {
                if (current != null) {
                    incidentDao.insertOrUpdate(
                        current.copy(
                            status = IncidentState.RESOLVED.name,
                            version = event.logicalVersion,
                            updatedAt = event.timestamp
                        )
                    )
                    true
                } else false
            }
            EventType.INCIDENT_CANCELLED.name -> {
                if (current != null) {
                    incidentDao.insertOrUpdate(
                        current.copy(
                            status = IncidentState.CANCELLED.name,
                            version = event.logicalVersion,
                            updatedAt = event.timestamp
                        )
                    )
                    true
                } else false
            }
            EventType.INCIDENT_ASSIGNMENT_RELEASED.name -> {
                if (current != null) {
                    incidentDao.insertOrUpdate(
                        current.copy(
                            status = IncidentState.ACKNOWLEDGED.name,
                            primaryResponderId = null,
                            primaryResponderName = null,
                            version = event.logicalVersion,
                            updatedAt = event.timestamp
                        )
                    )
                    true
                } else false
            }
            else -> false
        }

        domainEventDao.insertEvent(event.copy(applied = applied))
        return applied
    }

    private suspend fun rejectIncoming(event: DomainEventEntity, reason: String): Boolean {
        AppLogger.d("INCIDENT_REPO", "Rejected incident event ${event.eventId}: $reason")
        domainEventDao.insertEvent(event.copy(applied = false))
        return false
    }

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
                networkGateway.broadcastPriorityPayload(bytes)
            } else {
                networkGateway.broadcastPayload(bytes)
            }
        }
    }

    private fun JSONObject.optionalDouble(name: String): Double? =
        if (has(name) && !isNull(name)) getDouble(name) else null

    private fun JSONObject.optionalLong(name: String): Long? =
        if (has(name) && !isNull(name)) getLong(name) else null

    private fun JSONObject.optionalFloat(name: String): Float? =
        if (has(name) && !isNull(name)) getDouble(name).toFloat() else null

    private fun forwardAcceptedDomainEvent(payload: MeshPayload, sourceEndpointId: String) {
        val canRelay = if (payload.ttl > 0) payload.ttl > 1 else payload.relayHopCount < 6
        if (!canRelay) return
        val updated = payload.copy(
            routePath = payload.routePath + networkGateway.myDeviceName,
            relayHopCount = payload.relayHopCount + 1,
            ttl = if (payload.ttl > 0) payload.ttl - 1 else 0
        )
        networkGateway.broadcastPayload(ProtoBuf.encodeToByteArray(updated), sourceEndpointId)
    }

    /**
     * Reconnection Synchronization: sends recent event IDs to a newly connected peer.
     */
    fun startReconciliationWithPeer(endpointId: String) {
        repositoryScope.launch {
            if (!networkGateway.hasReadyEndpoint(endpointId)) return@launch
            val summaries = incidentDao.getActiveIncidentsForSync(MAX_SYNC_INCIDENTS)
            val json = JSONObject().apply {
                put("type", "SYNC_SUMMARY")
                put("protocol", SYNC_PROTOCOL)
                put("incidents", JSONArray().apply {
                    summaries.forEach { incident -> put(JSONObject().apply {
                        put("incidentId", incident.incidentId)
                        put("version", incident.version)
                    }) }
                })
            }.toString()

            val payload = MeshPayload(
                id = UUID.randomUUID().toString(),
                type = "EVENT_SYNC_REQ",
                senderName = networkGateway.myDeviceName,
                senderNodeId = networkGateway.myNodeId,
                text = json
            )
            val bytes = ProtoBuf.encodeToByteArray(payload)
            networkGateway.sendDirectPayload(endpointId, bytes)
        }
    }

    suspend fun handleSyncRequest(endpointId: String, text: String) {
        try {
            val json = JSONObject(text)
            if (json.optInt("protocol", SYNC_PROTOCOL) != SYNC_PROTOCOL) return
            val peerVersions = mutableMapOf<String, Long>()
            val summaries = json.optJSONArray("incidents")
            if (summaries != null) {
                for (i in 0 until summaries.length()) {
                    val summary = summaries.getJSONObject(i)
                    peerVersions[summary.getString("incidentId")] = summary.optLong("version", 0L)
                }
            }
            val eventsToSend = mutableListOf<DomainEventEntity>()
            incidentDao.getActiveIncidentsForSync(MAX_SYNC_INCIDENTS).forEach { incident ->
                val knownVersion = peerVersions[incident.incidentId] ?: 0L
                if (knownVersion < incident.version && eventsToSend.size < MAX_SYNC_EVENTS) {
                    eventsToSend += domainEventDao.getEventsAfterVersion(
                        incident.incidentId,
                        knownVersion,
                        MAX_SYNC_EVENTS - eventsToSend.size
                    )
                }
            }
            if (eventsToSend.isNotEmpty()) {
                val eventsArray = JSONArray()
                eventsToSend.forEach { e ->
                    eventsArray.put(JSONObject().apply {
                        put("eventId", e.eventId)
                        put("entityId", e.entityId)
                        put("entityType", e.entityType)
                        put("eventType", e.eventType)
                        put("actorId", e.actorId)
                        put("actorName", e.actorName)
                        put("logicalVersion", e.logicalVersion)
                        put("timestamp", e.timestamp)
                        put("payloadJson", e.payloadJson)
                    })
                }

                val respPayload = MeshPayload(
                    id = UUID.randomUUID().toString(),
                    type = "EVENT_SYNC_RESP",
                    senderName = networkGateway.myDeviceName,
                    senderNodeId = networkGateway.myNodeId,
                    text = JSONObject().apply {
                        put("events", eventsArray)
                        put("hasMore", eventsToSend.size >= MAX_SYNC_EVENTS)
                    }.toString()
                )
                val bytes = ProtoBuf.encodeToByteArray(respPayload)
                networkGateway.sendDirectPayload(endpointId, bytes)
            }
        } catch (e: Exception) {
            AppLogger.d("INCIDENT_REPO", "Failed handling sync request: ${e.message}")
        }
    }

    suspend fun handleSyncResponse(endpointId: String, text: String) {
        try {
            val response = JSONObject(text)
            val eventsArray = response.optJSONArray("events") ?: JSONArray()
            for (i in 0 until eventsArray.length()) {
                val item = eventsArray.getJSONObject(i)
                applyIncomingEventJson(item.toString())
            }
            if (response.optBoolean("hasMore", false) && networkGateway.hasReadyEndpoint(endpointId)) {
                startReconciliationWithPeer(endpointId)
            }
        } catch (e: Exception) {
            AppLogger.d("INCIDENT_REPO", "Failed handling sync response: ${e.message}")
        }
    }

    private companion object {
        const val SYNC_PROTOCOL = 1
        const val MAX_SYNC_INCIDENTS = 24
        const val MAX_SYNC_EVENTS = 24
    }
}
