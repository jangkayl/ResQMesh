package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.network.*
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.data.local.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.UUID

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class SosRepository(
    private val store: SosStore, private val signing: IncidentEventSigning,
    private val identity: IdentityProvider, private val gateway: MeshNetworkGateway,
    private val peers: MeshReadyPeerEvents, private val scope: CoroutineScope,
    private val sink: SosAlertSink, private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val inbound = kotlinx.coroutines.channels.Channel<Pair<String, MeshPayload>>(64)
    val alerts = store.alerts.stateIn(scope, SharingStarted.Eagerly, emptyList())
    fun events(id: String) = store.events(id)
    val incoming = MutableStateFlow<SosAlertEntity?>(null)
    val feedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val ownActive = alerts.map { list -> list.firstOrNull { !it.ended && it.originNodeId == identity.getDeviceId() } }
        .stateIn(scope, SharingStarted.Eagerly, null)
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val sync = SosSyncCoordinator(gateway, peers, store, scope, ::ingest, changes)

    init {
        gateway.onSosPacket = { endpoint, payload ->
            if (payload.type == "SOS_EVENT") {
                if (!inbound.trySend(endpoint to payload).isSuccess) log("SOS_QUEUE", "inbound-full")
            } else sync.receive(endpoint, payload)
        }
        scope.launch { for ((endpoint, payload) in inbound) {
                if (payload.text.toByteArray().size > 8_192 || payload.ttl !in 1..10 || payload.relayHopCount !in 0..10) continue
                val e = runCatching { SosProtocol.json.decodeFromString<SosEvent>(payload.text) }.getOrNull()
                    ?: continue
                val result = ingest(e, live = true)
                if (result == SosIngestion.APPLIED && payload.ttl > 1 && payload.relayHopCount < 10) {
                    val forwarded = payload.copy(ttl = payload.ttl - 1, relayHopCount = payload.relayHopCount + 1)
                    val dispatch = gateway.broadcastPriorityPayload(ProtoBuf.encodeToByteArray(forwarded), endpoint)
                    if (!dispatch.anyAccepted && dispatch.neighbors.isNotEmpty()) store.retry(e.eventId)
                    log("SOS_QUEUE", "relay id=${e.sosId} accepted=${dispatch.anyAccepted}")
                }
        } }
        scope.launch {
            alerts.collect { list ->
                val current = incoming.value
                if (current != null) incoming.value = list.firstOrNull { it.sosId == current.sosId && !it.ended && !it.locallySilenced }
            }
        }
        scope.launch {
            peers.peers.collect {
                // A radio partition does not silence an already received emergency.
                flush()
            }
        }
        scope.launch { while (isActive) { delay(5_000); flush() } }
    }

    suspend fun create(type: String, lat: Double? = null, lng: Double? = null, accuracy: Float? = null, captured: Long? = null): String = mutex.withLock {
        store.all().firstOrNull { !it.ended && it.originNodeId == identity.getDeviceId() }?.let { return@withLock it.sosId }
        val user = identity.getOrCreateUser()
        val id = SosProtocol.prefix(signing.publicKey) + UUID.randomUUID()
        val e = SosEvent(UUID.randomUUID().toString(), id, identity.getDeviceId(), user.displayName,
            signing.publicKey, type, 1, false, now(), now(), lat, lng, accuracy, captured?.takeIf { it > 0 })
        val signed = e.copy(signature = signing.sign(e.signingEvent()))
        check(apply(signed, pending = true) == SosIngestion.APPLIED)
        changes.tryEmit(Unit)
        dispatch(signed)
        id
    }

    suspend fun updateLocation(id: String, lat: Double, lng: Double, accuracy: Float, captured: Long) = mutex.withLock {
        val old = store.alert(id) ?: return@withLock
        if (old.ended || old.originNodeId != identity.getDeviceId() || old.signingKey != signing.publicKey) return@withLock
        if (old.locationCapturedAt != null && (captured < old.locationCapturedAt ||
                (old.accuracyMeters != null && accuracy > old.accuracyMeters))) return@withLock
        if (!lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0 ||
            !accuracy.isFinite() || accuracy < 0 || captured <= 0) return@withLock
        localRevision(old, false, lat, lng, accuracy, captured)
    }

    suspend fun end(id: String) = mutex.withLock {
        val old = store.alert(id) ?: return@withLock
        check(old.originNodeId == identity.getDeviceId() && old.signingKey == signing.publicKey) { "Only the SOS origin can end it" }
        if (!old.ended) localRevision(old, true, old.latitude, old.longitude, old.accuracyMeters, old.locationCapturedAt)
    }

    private suspend fun localRevision(old: SosAlertEntity, ended: Boolean, lat: Double?, lng: Double?, accuracy: Float?, captured: Long?) {
        val e = SosEvent(UUID.randomUUID().toString(), old.sosId, old.originNodeId, old.originName, old.signingKey,
            old.emergencyType, old.revision + 1, ended, old.createdAt, maxOf(now(), old.updatedAt), lat, lng, accuracy, captured)
        val signed = e.copy(signature = signing.sign(e.signingEvent()))
        check(apply(signed, pending = true) == SosIngestion.APPLIED)
        if (ended) { sink.ended(old.sosId); if (incoming.value?.sosId == old.sosId) incoming.value = null }
        changes.tryEmit(Unit)
        dispatch(signed)
    }

    suspend fun ingest(e: SosEvent, live: Boolean = false): SosIngestion = mutex.withLock {
        if (!SosProtocol.valid(e) || !signing.verify(e.signingEvent(), e.signingKey)) {
            log("SOS_EVENT", "rejected id=${e.sosId.take(100)}")
            return@withLock SosIngestion.REJECTED
        }
        val previouslyKnown = store.alert(e.sosId) != null
        val result = apply(e, pending = false)
        if (result == SosIngestion.APPLIED) {
            changes.tryEmit(Unit)
            if (e.ended) {
                sink.ended(e.sosId)
                if (incoming.value?.sosId == e.sosId) incoming.value = null
            } else if (live && !previouslyKnown && e.revision == 1L && e.originNodeId != identity.getDeviceId()) {
                store.alert(e.sosId)?.let { sink.received(it); incoming.value = it }
            }
        }
        result
    }

    private suspend fun apply(e: SosEvent, pending: Boolean): SosIngestion {
        if (!SosProtocol.valid(e)) return SosIngestion.REJECTED
        val serialized = SosProtocol.json.encodeToString(e)
        var result = SosIngestion.REJECTED
        store.transaction {
            val knownEvent = store.event(e.eventId)
            if (knownEvent != null) {
                result = if (knownEvent.eventJson == serialized) SosIngestion.DUPLICATE else SosIngestion.REJECTED
                return@transaction
            }
            val old = store.alert(e.sosId)
            result = sosTransition(old, e)
            if (result != SosIngestion.APPLIED) return@transaction
            store.putEvent(SosEventEntity(e.eventId, e.sosId, e.revision, serialized, pending))
            store.putAlert(SosAlertEntity(e.sosId, e.originNodeId, e.originName, e.signingKey, e.emergencyType,
                e.revision, e.ended, e.createdAt, e.updatedAt, e.latitude, e.longitude, e.accuracyMeters,
                e.locationCapturedAt, old?.locallySilenced ?: false, serialized, if (pending) "QUEUED" else "RECEIVED"))
            store.supersede(e.sosId, e.revision)
        }
        if (result == SosIngestion.APPLIED) log(if (e.ended) "SOS_CANCEL" else "SOS_EVENT", "applied id=${e.sosId} revision=${e.revision}")
        return result
    }

    suspend fun silence(id: String) = mutex.withLock {
        store.silence(id); sink.silence(id)
        if (incoming.value?.sosId == id) incoming.value = null
    }

    suspend fun canReply(id: String): Boolean = store.alert(id)?.ended == false
    fun owns(alert: SosAlertEntity) = alert.originNodeId == identity.getDeviceId() && alert.signingKey == signing.publicKey

    private suspend fun dispatch(e: SosEvent) {
        val payload = MeshPayload(id = e.eventId, type = "SOS_EVENT", text = SosProtocol.json.encodeToString(e),
            senderName = e.originName, senderNodeId = e.originNodeId, sosId = e.sosId,
            conversationKind = "SOS", ttl = gateway.currentMeshTtl().coerceIn(1, 10))
        val result = gateway.broadcastPriorityPayload(ProtoBuf.encodeToByteArray(payload))
        if (result.anyAccepted) {
            store.accepted(e.eventId)
            store.transmission(e.sosId, e.revision, "SENT")
        }
        log("SOS_QUEUE", "id=${e.sosId} revision=${e.revision} accepted=${result.anyAccepted}")
    }

    private suspend fun flush() = mutex.withLock {
        store.pending().take(16).forEach { row ->
            val current = store.alert(row.sosId)
            if (current?.revision != row.revision) store.accepted(row.eventId)
            else dispatch(SosProtocol.json.decodeFromString(row.eventJson))
        }
    }

    private fun log(marker: String, message: String) { AppLogger.d(marker, message) }
}
