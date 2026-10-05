package com.example.testresqmesh.data.repository.incident

import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.data.local.dao.DomainEventDao
import com.example.testresqmesh.data.local.dao.IncidentDao
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

import com.example.testresqmesh.data.repository.incident.LegacyIncidentSyncConstants.SYNC_PROTOCOL
import com.example.testresqmesh.data.repository.incident.LegacyIncidentSyncConstants.MAX_SYNC_INCIDENTS
import com.example.testresqmesh.data.repository.incident.LegacyIncidentSyncConstants.MAX_SYNC_EVENTS

@OptIn(ExperimentalSerializationApi::class)
internal class LegacyIncidentSync(
    private val incidentDao: IncidentDao,
    private val domainEventDao: DomainEventDao,
    private val networkGateway: MeshNetworkGateway,
    private val repositoryScope: CoroutineScope,
    private val applyJson: suspend (String) -> Boolean,
    private val replay: suspend (String) -> Unit,
    private val reconcile: (String) -> Unit
) {
    private suspend fun applyIncomingEventJson(json: String) = applyJson(json)
    private suspend fun replayPendingForIncident(id: String) = replay(id)
    private fun startReconciliationWithPeer(endpoint: String) = reconcile(endpoint)

    fun startLegacyReconciliationWithPeer(endpointId: String) {
        repositoryScope.launch {
            if (!networkGateway.hasReadyEndpoint(endpointId)) return@launch
            requestEventPage(endpointId, 0)
            val summaries = incidentDao.getRecentIncidentsForSync(MAX_SYNC_INCIDENTS)
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
            dispatchLegacyPayload(endpointId, bytes)
        }
    }

    suspend fun handleSyncRequest(endpointId: String, text: String) {
        try {
            val json = JSONObject(text)
            if (json.optString("type") == "INCIDENT_EVENT_PAGE") {
                val offset = json.optInt("offset").coerceAtLeast(0)
                val page = domainEventDao.getSyncPage(MAX_SYNC_EVENTS + 1, offset)
                val events = page.take(MAX_SYNC_EVENTS)
                val response = MeshPayload(
                    id = UUID.randomUUID().toString(), type = "EVENT_SYNC_RESP",
                    senderName = networkGateway.myDeviceName,
                    senderNodeId = networkGateway.myNodeId,
                    text = JSONObject().put("type", "INCIDENT_EVENT_PAGE")
                        .put("nextOffset", offset + events.size)
                        .put("hasMore", page.size > MAX_SYNC_EVENTS)
                        .put("events", JSONArray().apply { events.forEach { put(eventToJson(it)) } })
                        .toString()
                )
                dispatchLegacyPayload(endpointId, ProtoBuf.encodeToByteArray(response))
                return
            }
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
            incidentDao.getRecentIncidentsForSync(MAX_SYNC_INCIDENTS).forEach { incident ->
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
                        e.signature?.let { put("signature", it) }
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
                dispatchLegacyPayload(endpointId, bytes)
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
            eventsArray.let {
                for (i in 0 until it.length()) replayPendingForIncident(it.getJSONObject(i).optString("entityId"))
            }
            if (response.optString("type") == "INCIDENT_EVENT_PAGE") {
                if (response.optBoolean("hasMore", false) && networkGateway.hasReadyEndpoint(endpointId))
                    requestEventPage(endpointId, response.optInt("nextOffset"))
                return
            }
            if (response.optBoolean("hasMore", false) && networkGateway.hasReadyEndpoint(endpointId)) {
                startReconciliationWithPeer(endpointId)
            }
        } catch (e: Exception) {
            AppLogger.d("INCIDENT_REPO", "Failed handling sync response: ${e.message}")
        }
    }

    private fun requestEventPage(endpointId: String, offset: Int) {
        val request = MeshPayload(
            id = UUID.randomUUID().toString(), type = "EVENT_SYNC_REQ",
            senderName = networkGateway.myDeviceName,
            senderNodeId = networkGateway.myNodeId,
            text = JSONObject().put("type", "INCIDENT_EVENT_PAGE")
                .put("offset", offset).toString()
        )
        dispatchLegacyPayload(endpointId, ProtoBuf.encodeToByteArray(request))
    }

    private fun dispatchLegacyPayload(endpointId: String, bytes: ByteArray) {
        repositoryScope.launch {
            repeat(4) { attempt ->
                if (!networkGateway.hasReadyEndpoint(endpointId)) return@launch
                val result = networkGateway.sendDirectPayload(endpointId, bytes)
                AppLogger.d("INCIDENT_SYNC", "LEGACY_SEND endpoint=$endpointId accepted=${result.accepted} bytes=${bytes.size}")
                if (result.accepted) return@launch
                if (attempt < 3) delay(longArrayOf(2_000, 4_000, 8_000)[attempt])
            }
        }
    }
}
