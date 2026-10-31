package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.network.*
import edu.cit.resqmesh.core.network.dispatch.InboundPayloadValidator.Companion.MAX_TTL
import edu.cit.resqmesh.core.network.ratelimit.InboundOriginGate
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.model.SosLocationSample
import edu.cit.resqmesh.core.model.SosRaiseCharge
import edu.cit.resqmesh.core.model.SosRaiseResult
import edu.cit.resqmesh.data.local.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.UUID

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Suppress("LongParameterList") // the origin gate joins the existing injected collaborators
class SosRepository(
    private val store: SosStore, private val signing: IncidentEventSigning,
    private val identity: IdentityProvider, private val gateway: MeshNetworkGateway,
    private val peers: MeshReadyPeerEvents, private val scope: CoroutineScope,
    private val sink: SosAlertSink, private val now: () -> Long = System::currentTimeMillis,
    private val originGate: InboundOriginGate? = null
) {
    private val mutex = Mutex()
    private val inbound = kotlinx.coroutines.channels.Channel<Pair<String, MeshPayload>>(INBOUND_QUEUE_CAPACITY)
    val alerts = store.alerts.stateIn(scope, SharingStarted.Eagerly, emptyList())
    fun events(id: String) = store.events(id)
    val incoming = MutableStateFlow<SosAlertEntity?>(null)
    val feedback = MutableSharedFlow<String>(extraBufferCapacity = FEEDBACK_BUFFER_CAPACITY)
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
        scope.launch { for ((endpoint, payload) in inbound) receiveEvent(endpoint, payload) }
        scope.launch {
            alerts.collect { list ->
                val current = incoming.value
                if (current != null) incoming.value = list.firstOrNull { it.sosId == current.sosId && !it.ended && !it.locallySilenced }
            }
        }
        scope.launch {
            peers.peers.collect {
                // A radio partition does not silence an already received emergency.
                flushPendingEvents()
            }
        }
        scope.launch { while (isActive) { delay(FLUSH_INTERVAL_MS); flushPendingEvents() } }
    }

    private suspend fun receiveEvent(endpoint: String, payload: MeshPayload) {
        if (payload.text.toByteArray().size > MAX_EVENT_TEXT_BYTES || payload.ttl !in 1..MAX_TTL || payload.relayHopCount !in 0..MAX_TTL) return
        val e = runCatching { SosProtocol.json.decodeFromString<SosEvent>(payload.text) }.getOrNull() ?: return
        if (!SosProtocol.envelopeMatches(payload, e)) {
            log("SOS_EVENT", "INBOUND_REJECT type=SOS_EVENT reason=envelope_mismatch")
            return
        }
        val result = ingest(e, live = true, endpoint = endpoint)
        if (result == SosIngestion.APPLIED && payload.ttl > 1 && payload.relayHopCount < MAX_TTL) {
            relayEvent(endpoint, payload, e)
        }
    }

    private suspend fun relayEvent(endpoint: String, payload: MeshPayload, e: SosEvent) {
        val forwarded = payload.copy(ttl = payload.ttl - 1, relayHopCount = payload.relayHopCount + 1)
        val dispatch = gateway.broadcastPriorityPayload(ProtoBuf.encodeToByteArray(forwarded), endpoint)
        if (!dispatch.anyAccepted && dispatch.neighbors.isNotEmpty()) store.retry(e.eventId)
        log("SOS_QUEUE", "relay id=${e.sosId} accepted=${dispatch.anyAccepted}")
    }

    suspend fun create(type: String, lat: Double? = null, lng: Double? = null, accuracy: Float? = null, captured: Long? = null): String =
        when (val r = raise(type, SosRaiseCharge.NONE, lat, lng, accuracy, captured)) {
            is SosRaiseResult.Created -> r.id
            is SosRaiseResult.Reopened -> r.id
            SosRaiseResult.Throttled -> error("unreachable: no charge refuses")
        }

    /**
     * Reopens the active own SOS (never charged) or creates a new one. [charge] is taken under the repository
     * lock, only when a new sosId will really be created, and refunded if that creation fails.
     */
    suspend fun raise(
        type: String, charge: SosRaiseCharge, lat: Double? = null, lng: Double? = null,
        accuracy: Float? = null, captured: Long? = null
    ): SosRaiseResult = mutex.withLock {
        store.all().firstOrNull { !it.ended && it.originNodeId == identity.getDeviceId() }
            ?.let { return@withLock SosRaiseResult.Reopened(it.sosId) }
        if (!charge.tryCharge()) return@withLock SosRaiseResult.Throttled
        var created = false
        try {
            val user = identity.getOrCreateUser()
            val id = SosProtocol.prefix(signing.publicKey) + UUID.randomUUID()
            val e = SosEvent(UUID.randomUUID().toString(), id, identity.getDeviceId(), user.displayName,
                signing.publicKey, type, 1, false, now(), now(), lat, lng, accuracy, captured?.takeIf { it > 0 })
            val signed = e.copy(signature = signing.sign(e.signingEvent()))
            check(applyEvent(signed, pending = true) == SosIngestion.APPLIED)
            changes.tryEmit(Unit)
            created = true
            dispatch(signed)
            SosRaiseResult.Created(id)
        } finally {
            if (!created) charge.refund() // the SOS was not stored: give the token back
        }
    }

    suspend fun updateLocation(id: String, lat: Double, lng: Double, accuracy: Float, captured: Long) = mutex.withLock {
        applyFix(id, SosLocationSample(lat, lng, accuracy, captured), moved = false)
    }

    /** A newer fix replaces a sharper old one: the person has moved away from it. */
    suspend fun updateMovedLocation(id: String, fix: SosLocationSample) = mutex.withLock {
        applyFix(id, fix, moved = true)
    }

    private suspend fun applyFix(id: String, fix: SosLocationSample, moved: Boolean) {
        val old = store.alert(id)?.takeIf { !it.ended && owns(it) }
        if (old != null && replacesFix(old, fix, moved) && validFix(fix)) {
            localRevision(old, false, SosLocationFix(fix.latitude, fix.longitude, fix.accuracy, fix.capturedAt))
        }
    }

    private fun replacesFix(old: SosAlertEntity, fix: SosLocationSample, moved: Boolean): Boolean {
        val capturedAt = old.locationCapturedAt ?: return true
        val sharper = old.accuracyMeters?.let { fix.accuracy <= it } ?: true
        return fix.capturedAt >= capturedAt && (moved || sharper)
    }

    private fun validFix(fix: SosLocationSample) = fix.latitude.isFinite() && fix.latitude in LATITUDE_RANGE &&
        fix.longitude.isFinite() && fix.longitude in LONGITUDE_RANGE &&
        fix.accuracy.isFinite() && fix.accuracy >= 0 && fix.capturedAt > 0

    suspend fun end(id: String) = mutex.withLock {
        val old = store.alert(id) ?: return@withLock
        check(old.originNodeId == identity.getDeviceId() && old.signingKey == signing.publicKey) { "Only the SOS origin can end it" }
        if (!old.ended) localRevision(old, true, SosLocationFix(old.latitude, old.longitude, old.accuracyMeters, old.locationCapturedAt))
    }

    private suspend fun localRevision(old: SosAlertEntity, ended: Boolean, fix: SosLocationFix) {
        val e = SosEvent(UUID.randomUUID().toString(), old.sosId, old.originNodeId, old.originName, old.signingKey,
            old.emergencyType, old.revision + 1, ended, old.createdAt, maxOf(now(), old.updatedAt),
            fix.latitude, fix.longitude, fix.accuracyMeters, fix.capturedAt)
        val signed = e.copy(signature = signing.sign(e.signingEvent()))
        check(applyEvent(signed, pending = true) == SosIngestion.APPLIED)
        if (ended) { sink.ended(old.sosId); if (incoming.value?.sosId == old.sosId) incoming.value = null }
        changes.tryEmit(Unit)
        dispatch(signed)
    }

    @Suppress("UnusedParameter") // the alarm no longer depends on the path; callers still say where it came from
    suspend fun ingest(e: SosEvent, live: Boolean = false, endpoint: String? = null): SosIngestion = mutex.withLock {
        if (!SosProtocol.valid(e) || !signing.verify(e.signingEvent(), e.signingKey)) {
            log("SOS_EVENT", "rejected id=${e.sosId.take(100)}")
            return@withLock SosIngestion.REJECTED
        }
        if (SosProtocol.futureDated(e, now())) {
            log("SOS_EVENT", "INBOUND_REJECT type=SOS_EVENT reason=future_created_at id=${e.sosId.take(100)}")
            return@withLock SosIngestion.REJECTED
        }
        val previouslyKnown = store.alert(e.sosId) != null
        val isNewRaise = !previouslyKnown && e.revision == 1L
        // An end of a known SOS is never limited or delayed; it is bounded by the raises that created the sosId.
        val endOfKnown = previouslyKnown && e.ended
        if (!endOfKnown && isRateLimited(e, endpoint, isNewRaise)) return@withLock SosIngestion.REJECTED
        val result = applyEvent(e, pending = false)
        if (result == SosIngestion.APPLIED) {
            changes.tryEmit(Unit)
            if (e.ended) {
                sink.ended(e.sosId)
                if (incoming.value?.sosId == e.sosId) incoming.value = null
            } else if (shouldAlarm(e, previouslyKnown)) {
                store.alert(e.sosId)?.let { sink.received(it); incoming.value = it }
            }
        }
        result
    }

    /**
     * The alarm fires once per sosId: for the first revision this phone sees, whatever its number and whether it
     * came live or by sync, while the alert is active and created within 10 minutes (a known sosId is silent).
     * Own SOS is excluded by node id and by signing key (the Keystore key normally does not survive a reinstall).
     */
    private suspend fun shouldAlarm(e: SosEvent, previouslyKnown: Boolean): Boolean {
        if (previouslyKnown || e.ended || e.originNodeId == identity.getDeviceId()) return false
        return e.signingKey != signing.publicKey && now() - e.createdAt <= SYNC_ALARM_MAX_AGE_MS
    }

    /**
     * Only a verified, not yet stored event from a link charges its signing key's bucket; duplicates are free.
     * SOS_NEW is charged only for revision 1 of an unknown sosId; a refine or end that arrives first is an update.
     */
    private suspend fun isRateLimited(e: SosEvent, endpoint: String?, isNewRaise: Boolean): Boolean {
        if (endpoint == null || originGate == null || store.event(e.eventId) != null) return false
        return !originGate.admitVerifiedSos(endpoint, SosProtocol.prefix(e.signingKey), isNewRaise)
    }

    private suspend fun applyEvent(e: SosEvent, pending: Boolean): SosIngestion {
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
            conversationKind = "SOS", ttl = gateway.currentMeshTtl().coerceIn(1, MAX_TTL))
        val result = gateway.broadcastPriorityPayload(ProtoBuf.encodeToByteArray(payload))
        if (result.anyAccepted) {
            store.accepted(e.eventId)
            store.transmission(e.sosId, e.revision, "SENT")
        }
        log("SOS_QUEUE", "id=${e.sosId} revision=${e.revision} accepted=${result.anyAccepted}")
    }

    private suspend fun flushPendingEvents() = mutex.withLock {
        store.pending().take(FLUSH_BATCH_SIZE).forEach { row ->
            val current = store.alert(row.sosId)
            if (current?.revision != row.revision) store.accepted(row.eventId)
            else dispatch(SosProtocol.json.decodeFromString(row.eventJson))
        }
    }

    private fun log(marker: String, message: String) { AppLogger.d(marker, message) }
}

/** Where a local SOS revision places the alert. */
private data class SosLocationFix(
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val capturedAt: Long?
)

private const val INBOUND_QUEUE_CAPACITY = 64
private const val FEEDBACK_BUFFER_CAPACITY = 8
private const val FLUSH_INTERVAL_MS = 5_000L
private const val FLUSH_BATCH_SIZE = 16
private const val MAX_EVENT_TEXT_BYTES = 8_192
private val LATITUDE_RANGE = -90.0..90.0
private val LONGITUDE_RANGE = -180.0..180.0

/** A sync-delivered SOS older than this is history, not an emergency to alarm on. */
private const val SYNC_ALARM_MAX_AGE_MS = 10 * 60 * 1000L
