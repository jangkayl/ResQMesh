package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.ratelimit.SingleFlightGate
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.data.local.entity.DomainEventEntity
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
    private val jitter: () -> Long = { Random.nextLong(-RESYNC_JITTER_MS, RESYNC_JITTER_MS + 1) },
    private val log: (String) -> Unit = { AppLogger.d("INCIDENT_SYNC", it) },
) {
    private data class Link(val id: String, val endpoint: String, val established: Long)
    private data class Session(val link: Link, val id: String = UUID.randomUUID().toString(), var job: Job? = null) {
        // Chosen afresh for each pass and sent with every request of that pass.
        @Volatile var scope: IncidentSyncScope = IncidentSyncScope.current()
    }
    private data class Waiting(val endpoint: String, val session: String, val response: CompletableDeferred<JSONObject>)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val waiting = ConcurrentHashMap<String, Waiting>()
    private val rebuildGate = SingleFlightGate("INCIDENT_REBUILD")
    private var changeJob: Job? = null
    private var rotation = 0

    init {
        scope.launch {
            peers.peers.collectLatest { refreshSessions() }
        }
        // A fast same-endpoint reconnect can leave the READY list equal, so the state flow stays
        // silent; each READY notification replaces the stale-generation session and reconciles.
        scope.launch {
            peers.events.collect { peer ->
                refreshSessions()
                reconcilePeer(peer.nodeId)
            }
        }
        scope.launch {
            while (isActive) {
                delay(RESYNC_INTERVAL_MS + jitter().coerceIn(-RESYNC_JITTER_MS, RESYNC_JITTER_MS))
                reconcileAllPeers()
            }
        }
    }

    @Synchronized
    private fun refreshSessions() {
        val current = peers.peers.value
        val links = current.filter { gateway.hasReadyEndpoint(it.endpointId) && !gateway.isDeviceBlocked(it.name) }
            .associate { it.nodeId to linkOf(it) }
        sessions.entries.toList().forEach { (id, session) ->
            if (links[id] != session.link) {
                sessions.remove(id, session)
                session.job?.cancel()
                log("CANCEL peer=$id reason=link-replaced-or-offline")
            }
        }
        current.filter { links.containsKey(it.nodeId) }.forEach { peer ->
            val key = links.getValue(peer.nodeId)
            if (sessions.putIfAbsent(peer.nodeId, Session(key)) == null) reconcilePeer(peer.nodeId)
        }
    }

    private fun linkOf(peer: ConnectedDevice) = Link(peer.nodeId, peer.endpointId, gateway.linkEstablishedAt(peer.endpointId))

    @Synchronized
    fun changed() {
        changeJob?.cancel()
        changeJob = scope.launch { delay(CHANGE_DEBOUNCE_MS); reconcileAllPeers() }
    }

    fun triggerEndpoint(endpoint: String) {
        sessions.values.firstOrNull { it.link.endpoint == endpoint }?.let { reconcilePeer(it.link.id) }
    }

    private fun reconcileAllPeers() {
        refreshSessions()
        val ids = sessions.keys.sorted()
        if (ids.isEmpty()) return
        val start = rotation++ % ids.size
        (ids.drop(start) + ids.take(start)).forEach(::reconcilePeer)
    }

    @Synchronized
    private fun reconcilePeer(id: String) {
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

    private fun isCurrent(session: Session): Boolean = sessions[session.link.id] === session &&
        peers.peers.value.any { linkOf(it) == session.link && !gateway.isDeviceBlocked(it.name) } &&
        gateway.hasReadyEndpoint(session.link.endpoint)

    private suspend fun reconcile(session: Session) {
        // Bounded passes handle snapshot churn without keeping a busy peer in an infinite loop.
        repeat(PASS_BACKOFF_MS.size) { pass ->
            check(isCurrent(session))
            session.scope = IncidentSyncScope.current()
            val local = store.snapshot(session.scope)
            val remote = request(session, "DIGEST", local.digest())
            if (local.matches(remote)) {
                val completed = request(session, "COMPLETE", local.digest())
                if (completed.optBoolean("complete") && store.snapshot(session.scope).matches(completed)) {
                    log("COMPLETE peer=${session.link.id} snapshot=${local.token.take(12)}")
                    return
                }
            } else {
                log("MISMATCH peer=${session.link.id} pass=$pass")
                repair(session, local, remote.getString("snapshot"))
            }
            delay(PASS_BACKOFF_MS[pass])
        }
        log("PENDING peer=${session.link.id} reason=changed-or-dependencies")
    }

    private suspend fun repair(session: Session, local: IncidentSyncSnapshot, token: String) {
        val remoteEntries = fetchCatalog(session, token)
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
            val remoteEvents = if (theirs != null) fetchInventory(session, token, id) else emptyMap()
            val mineEvents = local.events.filter { it.entityId == id }.associateBy { it.eventId }
            check(remoteEvents.all { (eventId, hash) -> mineEvents[eventId]?.let {
                IncidentSyncSnapshot.eventHash(it) == hash
            } ?: true }) { "conflicting event ID" }
            pullMissingEvents(session, token, id, (remoteEvents.keys - mineEvents.keys).sorted())
            pushOutgoingEvents(session, mineEvents.values.filter { it.eventId !in remoteEvents })
            store.replay(id)
        }
    }

    private suspend fun fetchCatalog(session: Session, token: String): Map<String, IncidentSyncEntry> {
        val entries = mutableMapOf<String, IncidentSyncEntry>()
        forEachPagedItem(session, "CATALOG", JSONObject().put("snapshot", token)) { item ->
            val entry = IncidentSyncEntry(item.getString("id"), item.getString("history"), item.getString("state"))
            entries[entry.id] = entry
        }
        return entries
    }

    private suspend fun fetchInventory(session: Session, token: String, incidentId: String): Map<String, String> {
        val hashes = mutableMapOf<String, String>()
        forEachPagedItem(session, "INVENTORY", JSONObject().put("snapshot", token).put("incident", incidentId)) { item ->
            hashes[item.getString("id")] = item.getString("hash")
        }
        return hashes
    }

    private suspend inline fun forEachPagedItem(session: Session, operation: String, query: JSONObject,
        onItem: (JSONObject) -> Unit) {
        var cursor = ""
        do {
            val response = request(session, operation, query.put("after", cursor))
            val items = response.getJSONArray("items")
            for (i in 0 until items.length()) onItem(items.getJSONObject(i))
            val next = response.optString("next")
            check(next.isEmpty() || next > cursor)
            cursor = next
        } while (cursor.isNotEmpty())
    }

    private suspend fun pullMissingEvents(session: Session, token: String, incidentId: String, ids: List<String>) {
        val missing = ids.toMutableList()
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
            store.replay(incidentId)
        }
    }

    private suspend fun pushOutgoingEvents(session: Session, events: List<DomainEventEntity>) {
        val outgoing = events.sortedBy { it.eventId }.toMutableList()
        while (outgoing.isNotEmpty()) {
            val batch = page(outgoing.map(IncidentSyncSnapshot::eventJson))
            request(session, "PUT_EVENTS", JSONObject().put("events", JSONArray(batch)))
            outgoing.subList(0, batch.size).clear()
        }
    }

    private suspend fun request(session: Session, operation: String, body: JSONObject): JSONObject {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JSONObject>()
        waiting[requestId] = Waiting(session.link.endpoint, session.id, deferred)
        try {
            repeat(MAX_REQUEST_ATTEMPTS) { attempt ->
                check(isCurrent(session))
                val json = session.scope.writeTo(JSONObject(body.toString()))
                    .put("protocol", PROTOCOL).put("op", operation)
                    .put("session", session.id).put("request", requestId)
                val accepted = send(session.link.endpoint, "EVENT_SYNC_REQ", json, operation in CONTROL)
                if (accepted) {
                    val response = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { deferred.await() }
                    if (response != null) {
                        check(isCurrent(session))
                        if (response.optString("error") == "STALE") return failOnStaleSnapshot(session)
                        check(!response.has("error"))
                        return response
                    }
                    if (operation == "DIGEST" && attempt == 0) {
                        log("LEGACY_FALLBACK peer=${session.link.id} guarantees=limited")
                        legacyFallback(session.link.endpoint)
                    }
                }
                log("RETRY peer=${session.link.id} op=$operation attempt=${attempt + 1} reason=${if (accepted) "timeout" else "rejected"}")
                if (attempt < MAX_REQUEST_ATTEMPTS - 1) delay(PASS_BACKOFF_MS[attempt])
            }
            error("retry budget exhausted")
        } finally {
            waiting.remove(requestId)
            deferred.cancel()
        }
    }

    private suspend fun failOnStaleSnapshot(session: Session): Nothing {
        // Restart from a fresh digest on the next scheduled pass; never advance a stale cursor.
        log("STALE peer=${session.link.id}")
        changed()
        error("snapshot changed")
    }

    fun response(endpoint: String, payload: MeshPayload) {
        val json = runCatching { JSONObject(payload.text) }.getOrNull() ?: return
        if (json.optInt("protocol") != PROTOCOL) return
        val peer = peers.peers.value.firstOrNull { it.endpointId == endpoint } ?: return
        if (payload.senderNodeId != peer.nodeId) return
        waiting[json.optString("request")]?.let {
            if (it.endpoint == endpoint && it.session == json.optString("session")) it.response.complete(json)
        }
    }

    suspend fun receive(endpoint: String, payload: MeshPayload) {
        val peer = peers.peers.value.firstOrNull { it.endpointId == endpoint } ?: return
        if (!gateway.hasReadyEndpoint(endpoint) || gateway.isDeviceBlocked(peer.name) || payload.senderNodeId != peer.nodeId) return
        val receivedLink = linkOf(peer)
        val incoming = JSONObject(payload.text)
        if (incoming.optInt("protocol") != PROTOCOL || incoming.optString("session").isBlank() ||
            incoming.optString("request").isBlank()) return
        val operation = incoming.optString("op")
        val result = try {
            answer(operation, incoming)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            JSONObject().put("error", "INVALID")
        }
        result.put("protocol", PROTOCOL).put("session", incoming.getString("session"))
            .put("request", incoming.getString("request")).put("op", operation)
        if (!peers.peers.value.any { linkOf(it) == receivedLink } || !gateway.hasReadyEndpoint(endpoint)) return
        if (!send(endpoint, "EVENT_SYNC_RESP", result, operation in CONTROL)) {
            log("RESPONSE_REJECTED peer=${peer.nodeId} op=$operation")
        }
    }

    private suspend fun answer(operation: String, incoming: JSONObject): JSONObject {
        // The requester's scope, so both phones hash the same incident set.
        val scope = IncidentSyncScope.from(incoming) ?: error("missing scope")
        val snapshot = store.snapshot(scope)
        if (operation in SNAPSHOT_OPERATIONS && incoming.optString("snapshot") != snapshot.token) {
            return JSONObject().put("error", "STALE")
        }
        return when (operation) {
            "DIGEST" -> snapshot.digest()
            "COMPLETE" -> snapshot.digest().put("complete", snapshot.matches(incoming))
            "CATALOG" -> pagedItems(snapshot.entries.filter { it.id > incoming.optString("after") }.map { it.json() })
            "INVENTORY" -> pagedItems(snapshot.events
                .filter { it.entityId == incoming.getString("incident") && it.eventId > incoming.optString("after") }
                .map { JSONObject().put("id", it.eventId).put("hash", IncidentSyncSnapshot.eventHash(it)) })
            "GET_EVENTS" -> {
                val ids = stringList(incoming.getJSONArray("ids"))
                check(ids.size <= PAGE_COUNT)
                JSONObject().put("events", JSONArray(page(snapshot.events.filter { it.eventId in ids }
                    .map(IncidentSyncSnapshot::eventJson))))
            }
            "PUT_EVENTS" -> ingestEvents(incoming.getJSONArray("events"))
            "REBUILD" -> {
                // A rebuild already running or queued for this incident covers a repeat request.
                val incident = incoming.getString("incident")
                rebuildGate.run(incident) { store.rebuild(incident) }
                store.snapshot(scope).digest()
            }
            else -> JSONObject().put("error", "UNSUPPORTED")
        }
    }

    private fun pagedItems(items: List<JSONObject>): JSONObject {
        val selected = page(items)
        return JSONObject().put("items", JSONArray(selected)).put("next",
            if (selected.size < items.size) selected.last().getString("id") else "")
    }

    private suspend fun ingestEvents(events: JSONArray): JSONObject {
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
        return JSONObject().put("results", results)
    }

    private fun send(endpoint: String, type: String, json: JSONObject, priority: Boolean): Boolean {
        val bytes = ProtoBuf.encodeToByteArray(MeshPayload(id = UUID.randomUUID().toString(), type = type,
            senderName = gateway.myDeviceName, senderNodeId = gateway.myNodeId, text = json.toString()))
        val result = if (priority) gateway.sendPriorityPayload(endpoint, bytes) else gateway.sendDirectPayload(endpoint, bytes)
        log("SEND endpoint=$endpoint type=$type bytes=${bytes.size} accepted=${result.accepted}")
        return result.accepted
    }

    companion object {
        /** 3 adds the requester-chosen sync scope; older builds fall back to legacy sync. */
        const val PROTOCOL = 3
        const val PAGE_COUNT = 24
        const val PAGE_BYTES = 4 * 1024
        private const val RESYNC_INTERVAL_MS = 30_000L
        private const val RESYNC_JITTER_MS = 3_000L
        private const val CHANGE_DEBOUNCE_MS = 500L
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val MAX_REQUEST_ATTEMPTS = 4
        /** Leaves envelope space on each page. */
        private const val PAGE_ENVELOPE_BYTES = 512
        /** One entry per bounded repair pass; request retries use its leading delays. */
        private val PASS_BACKOFF_MS = longArrayOf(2_000, 4_000, 8_000, 8_000)
        private val CONTROL = setOf("DIGEST", "COMPLETE")
        private val SNAPSHOT_OPERATIONS = setOf("CATALOG", "INVENTORY", "GET_EVENTS")
        private fun stringList(items: JSONArray) = (0 until items.length()).map { items.getString(it) }
        internal fun page(items: List<JSONObject>): List<JSONObject> {
            val selected = mutableListOf<JSONObject>()
            var bytes = PAGE_ENVELOPE_BYTES // One large supported event is allowed alone.
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
