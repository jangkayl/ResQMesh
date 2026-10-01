package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** Hop-by-hop anti-entropy. The event log, rather than an ephemeral send queue, owns recovery. */
@OptIn(ExperimentalSerializationApi::class)
class IncidentSyncCoordinator(
    private val gateway: MeshNetworkGateway,
    private val peers: MeshReadyPeerEvents,
    private val store: IncidentSyncStore,
    private val scope: CoroutineScope,
    private val legacyFallback: (String) -> Unit,
    private val jitter: () -> Long = { Random.nextLong(-3_000, 3_001) },
    private val log: (String) -> Unit = { AppLogger.d("INCIDENT_SYNC", it) },
) {
    private data class Link(val id: String, val endpoint: String, val established: Long)
    private data class Session(val link: Link, val id: String = UUID.randomUUID().toString(), var job: Job? = null)
    private data class Waiting(val endpoint: String, val session: String, val response: CompletableDeferred<JSONObject>)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val waiting = ConcurrentHashMap<String, Waiting>()
    private var changeJob: Job? = null
    private var rotation = 0

    init {
        scope.launch {
            peers.peers.collectLatest { refreshSessions() }
        }
        scope.launch {
            while (isActive) {
                delay(30_000 + jitter().coerceIn(-3_000, 3_000))
                triggerAll()
            }
        }
    }

    @Synchronized
    private fun refreshSessions() {
        val current = peers.peers.value
        val links = current.filter { gateway.hasReadyEndpoint(it.endpointId) && !gateway.isDeviceBlocked(it.name) }
            .associate { it.nodeId to link(it) }
        sessions.entries.toList().forEach { (id, session) ->
            if (links[id] != session.link) {
                sessions.remove(id, session)
                session.job?.cancel()
                log("CANCEL peer=$id reason=link-replaced-or-offline")
            }
        }
        current.filter { links.containsKey(it.nodeId) }.forEach { peer ->
            val key = links.getValue(peer.nodeId)
            if (sessions.putIfAbsent(peer.nodeId, Session(key)) == null) trigger(peer.nodeId)
        }
    }

    private fun link(peer: ConnectedDevice) = Link(peer.nodeId, peer.endpointId, gateway.linkEstablishedAt(peer.endpointId))

    @Synchronized
    fun changed() {
        changeJob?.cancel()
        changeJob = scope.launch { delay(500); triggerAll() }
    }

    fun triggerEndpoint(endpoint: String) {
        sessions.values.firstOrNull { it.link.endpoint == endpoint }?.let { trigger(it.link.id) }
    }

    private fun triggerAll() {
        refreshSessions()
        val ids = sessions.keys.sorted()
        if (ids.isEmpty()) return
        val start = rotation++ % ids.size
        (ids.drop(start) + ids.take(start)).forEach(::trigger)
    }

    @Synchronized
    private fun trigger(id: String) {
        val session = sessions[id] ?: return
        if (session.job?.isActive == true) return
        session.job = scope.launch {
            log("START peer=$id")
            try {
                reconcile(session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                log("PENDING peer=$id reason=repair-incomplete")
            }
        }
    }

    private fun current(session: Session): Boolean = sessions[session.link.id] === session &&
        peers.peers.value.any { link(it) == session.link && !gateway.isDeviceBlocked(it.name) } &&
        gateway.hasReadyEndpoint(session.link.endpoint)

    private suspend fun reconcile(session: Session) {
        // Bounded passes handle snapshot churn without keeping a busy peer in an infinite loop.
        repeat(4) { pass ->
            check(current(session))
            val local = store.snapshot()
            val remote = request(session, "DIGEST", local.digest())
            if (local.matches(remote)) {
                val completed = request(session, "COMPLETE", local.digest())
                if (completed.optBoolean("complete") && store.snapshot().matches(completed)) {
                    log("COMPLETE peer=${session.link.id} snapshot=${local.token.take(12)}")
                    return
                }
            } else {
                log("MISMATCH peer=${session.link.id} pass=$pass")
                repair(session, local, remote.getString("snapshot"))
            }
            delay(longArrayOf(2_000, 4_000, 8_000, 8_000)[pass])
        }
        log("PENDING peer=${session.link.id} reason=changed-or-dependencies")
    }

    private suspend fun repair(session: Session, local: IncidentSyncSnapshot, token: String) {
        val remoteEntries = mutableMapOf<String, IncidentSyncEntry>()
        var cursor = ""
        do {
            val response = request(session, "CATALOG", JSONObject().put("snapshot", token).put("after", cursor))
            val items = response.getJSONArray("items")
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val entry = IncidentSyncEntry(item.getString("id"), item.getString("history"), item.getString("state"))
                remoteEntries[entry.id] = entry
            }
            val next = response.optString("next")
            check(next.isEmpty() || next > cursor)
            cursor = next
        } while (cursor.isNotEmpty())
        val localEntries = local.entries.associateBy { it.id }
        for (id in (remoteEntries.keys + localEntries.keys).sorted()) {
            val mine = localEntries[id]
            val theirs = remoteEntries[id]
            if (mine == theirs) { store.replay(id); continue }
            if (mine != null && theirs != null && mine.history == theirs.history) {
                store.rebuild(id)
                request(session, "REBUILD", JSONObject().put("incident", id))
                continue
            }
            val remoteEvents = mutableMapOf<String, String>()
            cursor = ""
            if (theirs != null) do {
                val response = request(session, "INVENTORY", JSONObject().put("snapshot", token)
                    .put("incident", id).put("after", cursor))
                val items = response.getJSONArray("items")
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    remoteEvents[item.getString("id")] = item.getString("hash")
                }
                val next = response.optString("next")
                check(next.isEmpty() || next > cursor)
                cursor = next
            } while (cursor.isNotEmpty())
            val mineEvents = local.events.filter { it.entityId == id }.associateBy { it.eventId }
            check(remoteEvents.all { (eventId, hash) -> mineEvents[eventId]?.let {
                IncidentSyncSnapshot.eventHash(it) == hash
            } ?: true }) { "conflicting event ID" }
            val missing = (remoteEvents.keys - mineEvents.keys).sorted().toMutableList()
            while (missing.isNotEmpty()) {
                val response = request(session, "GET_EVENTS", JSONObject().put("snapshot", token)
                    .put("ids", JSONArray(missing.take(PAGE_COUNT))))
                val batch = response.getJSONArray("events")
                check(batch.length() > 0)
                for (i in 0 until batch.length()) {
                    val event = batch.getJSONObject(i)
                    check(missing.remove(event.getString("eventId")))
                    store.ingest(event)
                }
                store.replay(id)
            }
            val outgoing = mineEvents.values.filter { it.eventId !in remoteEvents }.sortedBy { it.eventId }.toMutableList()
            while (outgoing.isNotEmpty()) {
                val batch = page(outgoing.map(IncidentSyncSnapshot::eventJson))
                request(session, "PUT_EVENTS", JSONObject().put("events", JSONArray(batch)))
                outgoing.subList(0, batch.size).clear()
            }
            store.replay(id)
        }
    }

    private suspend fun request(session: Session, operation: String, body: JSONObject): JSONObject {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JSONObject>()
        waiting[requestId] = Waiting(session.link.endpoint, session.id, deferred)
        try {
            repeat(4) { attempt ->
                check(current(session))
                val json = JSONObject(body.toString()).put("protocol", 2).put("op", operation)
                    .put("session", session.id).put("request", requestId)
                val accepted = send(session.link.endpoint, "EVENT_SYNC_REQ", json, operation in CONTROL)
                if (accepted) {
                    val response = withTimeoutOrNull(30_000) { deferred.await() }
                    if (response != null) {
                        check(current(session))
                        if (response.optString("error") == "STALE") return staleSnapshot(session)
                        check(!response.has("error"))
                        return response
                    }
                    if (operation == "DIGEST" && attempt == 0) {
                        log("LEGACY_FALLBACK peer=${session.link.id} guarantees=limited")
                        legacyFallback(session.link.endpoint)
                    }
                }
                log("RETRY peer=${session.link.id} op=$operation attempt=${attempt + 1} reason=${if (accepted) "timeout" else "rejected"}")
                if (attempt < 3) delay(longArrayOf(2_000, 4_000, 8_000)[attempt])
            }
            error("retry budget exhausted")
        } finally {
            waiting.remove(requestId)
            deferred.cancel()
        }
    }

    private suspend fun staleSnapshot(session: Session): Nothing {
        // Restart from a fresh digest on the next scheduled pass; never advance a stale cursor.
        log("STALE peer=${session.link.id}")
        changed()
        error("snapshot changed")
    }

    fun response(endpoint: String, payload: MeshPayload) {
        val json = runCatching { JSONObject(payload.text) }.getOrNull() ?: return
        if (json.optInt("protocol") != 2) return
        val peer = peers.peers.value.firstOrNull { it.endpointId == endpoint } ?: return
        if (payload.senderNodeId != peer.nodeId) return
        waiting[json.optString("request")]?.let {
            if (it.endpoint == endpoint && it.session == json.optString("session")) it.response.complete(json)
        }
    }

    suspend fun receive(endpoint: String, payload: MeshPayload) {
        val peer = peers.peers.value.firstOrNull { it.endpointId == endpoint } ?: return
        if (!gateway.hasReadyEndpoint(endpoint) || gateway.isDeviceBlocked(peer.name) || payload.senderNodeId != peer.nodeId) return
        val receivedLink = link(peer)
        val incoming = JSONObject(payload.text)
        if (incoming.optInt("protocol") != 2 || incoming.optString("session").isBlank() || incoming.optString("request").isBlank()) return
        val operation = incoming.optString("op")
        val result = try {
            val snapshot = store.snapshot()
            if (operation in SNAPSHOT_OPERATIONS && incoming.optString("snapshot") != snapshot.token) {
                JSONObject().put("error", "STALE")
            } else when (operation) {
                "DIGEST" -> snapshot.digest()
                "COMPLETE" -> snapshot.digest().put("complete", snapshot.matches(incoming))
                "CATALOG" -> {
                    val items = snapshot.entries.filter { it.id > incoming.optString("after") }
                    val selected = page(items.map { it.json() })
                    JSONObject().put("items", JSONArray(selected)).put("next",
                        if (selected.size < items.size) selected.last().getString("id") else "")
                }
                "INVENTORY" -> {
                    val items = snapshot.events.filter { it.entityId == incoming.getString("incident") && it.eventId > incoming.optString("after") }
                    val selected = page(items.map { JSONObject().put("id", it.eventId).put("hash", IncidentSyncSnapshot.eventHash(it)) })
                    JSONObject().put("items", JSONArray(selected)).put("next",
                        if (selected.size < items.size) selected.last().getString("id") else "")
                }
                "GET_EVENTS" -> {
                    val ids = strings(incoming.getJSONArray("ids"))
                    check(ids.size <= PAGE_COUNT)
                    JSONObject().put("events", JSONArray(page(snapshot.events.filter { it.eventId in ids }
                        .map(IncidentSyncSnapshot::eventJson))))
                }
                "PUT_EVENTS" -> {
                    val events = incoming.getJSONArray("events")
                    check(events.length() in 1..PAGE_COUNT)
                    val results = JSONArray()
                    val ids = mutableSetOf<String>()
                    for (i in 0 until events.length()) {
                        val event = events.getJSONObject(i)
                        ids += event.getString("entityId")
                        results.put(JSONObject().put("id", event.getString("eventId")).put("result", store.ingest(event).name))
                    }
                    ids.forEach { store.replay(it) }
                    changed()
                    JSONObject().put("results", results)
                }
                "REBUILD" -> { store.rebuild(incoming.getString("incident")); store.snapshot().digest() }
                else -> JSONObject().put("error", "UNSUPPORTED")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            JSONObject().put("error", "INVALID")
        }
        result.put("protocol", 2).put("session", incoming.getString("session"))
            .put("request", incoming.getString("request")).put("op", operation)
        if (!peers.peers.value.any { link(it) == receivedLink } || !gateway.hasReadyEndpoint(endpoint)) return
        if (!send(endpoint, "EVENT_SYNC_RESP", result, operation in CONTROL)) {
            log("RESPONSE_REJECTED peer=${peer.nodeId} op=$operation")
        }
    }

    private fun send(endpoint: String, type: String, json: JSONObject, priority: Boolean): Boolean {
        val bytes = ProtoBuf.encodeToByteArray(MeshPayload(id = UUID.randomUUID().toString(), type = type,
            senderName = gateway.myDeviceName, senderNodeId = gateway.myNodeId, text = json.toString()))
        val result = if (priority) gateway.sendPriorityPayload(endpoint, bytes) else gateway.sendDirectPayload(endpoint, bytes)
        log("SEND endpoint=$endpoint type=$type bytes=${bytes.size} accepted=${result.accepted}")
        return result.accepted
    }

    companion object {
        const val PAGE_COUNT = 24
        const val PAGE_BYTES = 4 * 1024
        private val CONTROL = setOf("DIGEST", "COMPLETE")
        private val SNAPSHOT_OPERATIONS = setOf("CATALOG", "INVENTORY", "GET_EVENTS")
        private fun strings(items: JSONArray) = (0 until items.length()).map { items.getString(it) }
        internal fun page(items: List<JSONObject>): List<JSONObject> {
            val selected = mutableListOf<JSONObject>()
            var bytes = 512 // Leave envelope space; one large supported event is allowed alone.
            for (item in items.take(PAGE_COUNT)) {
                val size = item.toString().toByteArray(Charsets.UTF_8).size + 1
                if (selected.isNotEmpty() && bytes + size > PAGE_BYTES) break
                selected += item
                bytes += size
            }
            return selected
        }
    }
}
