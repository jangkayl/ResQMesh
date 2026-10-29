package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.network.*
import edu.cit.resqmesh.data.local.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)
class SosLifecycleAndSyncTest {
    private class MemoryStore : SosStore {
        private val rows = linkedMapOf<String, SosAlertEntity>()
        val events = linkedMapOf<String, SosEventEntity>()
        override val alerts = MutableStateFlow<List<SosAlertEntity>>(emptyList())
        private val eventFlow = MutableStateFlow<List<SosEventEntity>>(emptyList())
        override fun events(id: String) = eventFlow.map { rows -> rows.filter { it.sosId == id } }
        override suspend fun all() = rows.values.toList()
        override suspend fun alert(id: String) = rows[id]
        override suspend fun event(id: String) = events[id]
        override suspend fun putAlert(alert: SosAlertEntity) { rows[alert.sosId] = alert; alerts.value = rows.values.toList() }
        override suspend fun putEvent(event: SosEventEntity) { check(!events.containsKey(event.eventId)); events[event.eventId] = event; eventFlow.value = events.values.toList() }
        override suspend fun pending() = events.values.filter { it.pending }
        override suspend fun accepted(id: String) { events[id]?.let { events[id] = it.copy(pending = false) } }
        override suspend fun retry(id: String) { events[id]?.let { events[id] = it.copy(pending = true) } }
        override suspend fun supersede(id: String, revision: Long) { events.values.toList().filter { it.sosId == id && it.revision < revision }.forEach { accepted(it.eventId) } }
        override suspend fun silence(id: String) { rows[id]?.let { putAlert(it.copy(locallySilenced = true)) } }
        override suspend fun transmission(id: String, revision: Long, state: String) { rows[id]?.takeIf { it.revision == revision }?.let { putAlert(it.copy(transmission = state)) } }
        override suspend fun transaction(block: suspend () -> Unit) { block() }
    }
    private class Sink : SosAlertSink {
        val alarms = mutableListOf<String>()
        val ended = mutableListOf<String>()
        override fun received(alert: SosAlertEntity) { alarms += alert.sosId }
        override fun ended(id: String) { ended += id }
        override fun silence(id: String) {}
        override fun offline() {}
    }
    private class Signing(override val publicKey: String) : IncidentEventSigning {
        override fun sign(event: DomainEventEntity) = SosProtocol.hash(publicKey + event.payloadJson)
        override fun verify(event: DomainEventEntity, publicKey: String) = event.signature == SosProtocol.hash(publicKey + event.payloadJson)
    }
    private class Network(val scope: CoroutineScope) {
        val nodes = linkedMapOf<String, Node>()
        var reject = false
        var rejectFrom: String? = null
        var dropEvents = false
        val sent = mutableListOf<MeshPayload>()
        var generation = 0L
        fun connect(a: Node, b: Node) { a.links[b.id] = ++generation; b.links[a.id] = generation; publish(a); publish(b) }
        fun disconnect(a: Node, b: Node) { a.links.remove(b.id); b.links.remove(a.id); publish(a); publish(b) }
        fun publish(n: Node) { n.peers.update(n.links.keys.map { ConnectedDevice(it, it, nodeId = it, isPayloadReady = true) }) }
        fun send(from: Node, to: String, bytes: ByteArray): TransportDispatchResult {
            val p = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes); sent += p
            if (reject || rejectFrom == from.id) return TransportDispatchResult.REJECTED_QUEUE_FULL
            if (!from.links.containsKey(to)) return TransportDispatchResult.REJECTED_NOT_READY
            if (!(dropEvents && p.type == "SOS_EVENT")) scope.launch {
                val receiver = nodes.getValue(to)
                if (from.id in receiver.links) receiver.gateway.onSosPacket?.invoke(from.id, p)
            }
            return TransportDispatchResult.ACCEPTED
        }
    }
    private class Node(
        val id: String, val network: Network, store: MemoryStore = MemoryStore(),
        gate: edu.cit.resqmesh.core.network.ratelimit.InboundOriginGate? = null,
        clock: () -> Long = { 1000 },
        signingKey: String? = null
    ) {
        val store = store
        val links = mutableMapOf<String, Long>()
        val peers = MeshReadyPeerEvents()
        val sink = Sink()
        val signing = Signing(signingKey ?: "key-$id")
        private val callbacks = mutableMapOf<String, Any?>()
        val gateway = Proxy.newProxyInstance(MeshNetworkGateway::class.java.classLoader,
            arrayOf(MeshNetworkGateway::class.java)) { _, m, args ->
            when {
                m.name.startsWith("set") -> { callbacks[m.name.removePrefix("set")] = args?.get(0); null }
                m.name == "getMyNodeId" -> id
                m.name == "getMyDeviceName" -> id
                m.name.startsWith("get") -> callbacks[m.name.removePrefix("get")]
                m.name == "currentMeshTtl" -> 10
                m.name == "hasReadyEndpoint" -> args!![0] in links
                m.name == "isDeviceBlocked" -> false
                m.name == "linkEstablishedAt" -> links[args!![0]] ?: 0L
                m.name.startsWith("broadcast") -> {
                    val excluded = args?.getOrNull(1) as? String
                    BroadcastDispatchResult(links.keys.filter { it != excluded }.associateWith { network.send(this, it, args!![0] as ByteArray) })
                }
                m.name.startsWith("send") -> network.send(this, args!![0] as String, args[1] as ByteArray)
                else -> null
            }
        } as MeshNetworkGateway
        private val identity = object : IdentityProvider {
            override suspend fun getOrCreateUser(displayName: String?) = UserEntity(id, id, id, null, 1)
            override fun observeUser() = flowOf(UserEntity(id, id, id, null, 1))
            override suspend fun getUserId() = id
            override fun getDeviceId() = id
        }
        val repo = SosRepository(
            store, signing, identity, gateway, peers, network.scope, sink, now = clock, originGate = gate
        )
        init { network.nodes[id] = this }
    }

    @Test fun forgedSosNeverChargesAndMismatchedEnvelopeIsRejected() = runTest {
        val net = Network(backgroundScope)
        val gate = edu.cit.resqmesh.core.network.ratelimit.InboundOriginGate(
            edu.cit.resqmesh.core.network.ratelimit.OriginRateLimiter())
        val a = Node("A", net); val b = Node("B", net, gate = gate)
        val id = a.repo.create("Medical")
        val real = SosProtocol.json.decodeFromString<SosEvent>(a.store.alert(id)!!.eventJson)
        fun envelope(event: SosEvent, node: String = event.originNodeId) = MeshPayload(
            id = event.eventId, type = "SOS_EVENT", text = SosProtocol.json.encodeToString(event),
            senderNodeId = node, sosId = event.sosId, ttl = 5)
        // Forged copies claiming the victim, with an invalid signature, must not spend the victim bucket.
        repeat(5) { b.gateway.onSosPacket?.invoke("X", envelope(real.copy(eventId = "f$it", signature = "bad"))) }
        // A genuine event wrapped in an envelope that names another sender is refused.
        b.gateway.onSosPacket?.invoke("X", envelope(real, node = "EVIL"))
        runCurrent()
        assertNull(b.store.alert(id))
        b.gateway.onSosPacket?.invoke("X", envelope(real))
        runCurrent()
        assertNotNull(b.store.alert(id))
        // The verified event charged its own key bucket (burst 4): a fifth new alert from the same key is limited.
        repeat(3) { assertTrue(gate.admitVerifiedSos("X", SosProtocol.prefix(real.signingKey), true)) }
        assertFalse(gate.admitVerifiedSos("X", SosProtocol.prefix(real.signingKey), true))
    }

    @Test fun cancellationBeforeCreateCannotResurrectAndLateLocationIsIgnored() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net); val b = Node("B", net)
        val id = a.repo.create("Medical")
        val create = SosProtocol.json.decodeFromString<SosEvent>(a.store.alert(id)!!.eventJson)
        a.repo.end(id)
        val end = SosProtocol.json.decodeFromString<SosEvent>(a.store.alert(id)!!.eventJson)
        assertEquals(SosIngestion.APPLIED, b.repo.ingest(end, true))
        assertEquals(SosIngestion.STALE, b.repo.ingest(create, true))
        assertEquals(SosIngestion.DUPLICATE, b.repo.ingest(end, true))
        a.repo.updateLocation(id, 10.0, 20.0, 5f, 1001)
        assertEquals(2L, a.store.alert(id)!!.revision)
        assertTrue(b.store.alert(id)!!.ended)
        assertTrue(b.sink.alarms.isEmpty())
        val resurrection = create.copy(eventId = "new", revision = 3)
        val signed = resurrection.copy(signature = a.signing.sign(resurrection.signingEvent()))
        assertEquals(SosIngestion.STALE, b.repo.ingest(signed))
    }

    @Test fun silenceAndCancellationAffectOnlyMatchingConcurrentAlert() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net); val b = Node("B", net); val c = Node("C", net)
        val aid = a.repo.create("Medical"); val cid = c.repo.create("Fire")
        b.repo.ingest(SosProtocol.json.decodeFromString(a.store.alert(aid)!!.eventJson), true)
        b.repo.ingest(SosProtocol.json.decodeFromString(c.store.alert(cid)!!.eventJson), true)
        b.repo.silence(aid)
        assertFalse(b.store.alert(aid)!!.ended)
        assertFalse(b.store.alert(cid)!!.locallySilenced)
        a.repo.end(aid)
        b.repo.ingest(SosProtocol.json.decodeFromString(a.store.alert(aid)!!.eventJson), true)
        assertTrue(b.store.alert(aid)!!.ended); assertFalse(b.store.alert(cid)!!.ended)
        assertEquals(listOf(aid, cid), b.sink.alarms)
    }

    @Test fun forgedCancellationAndTamperedReplayAreRejected() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net); val b = Node("B", net)
        val id = a.repo.create("Medical")
        val e = SosProtocol.json.decodeFromString<SosEvent>(a.store.alert(id)!!.eventJson)
        b.repo.ingest(e)
        val forged = e.copy(eventId = "fake", ended = true, revision = 2, signature = "forged")
        assertEquals(SosIngestion.REJECTED, b.repo.ingest(forged))
        val wrongKey = forged.copy(signingKey = "attacker")
        assertEquals(SosIngestion.REJECTED, b.repo.ingest(wrongKey.copy(signature = Signing("attacker").sign(wrongKey.signingEvent()))))
        assertFalse(b.store.alert(id)!!.ended)
    }

    @Test fun threeHopReconnectRepairsMissedCancellationWithoutNewAlarm() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net); val b = Node("B", net); val c = Node("C", net)
        net.connect(a, b); net.connect(b, c); runCurrent()
        val id = a.repo.create("Medical"); runCurrent()
        assertNotNull(c.store.alert(id)); assertFalse(c.store.alert(id)!!.ended)
        net.disconnect(b, c); runCurrent(); a.repo.end(id); runCurrent()
        assertFalse(c.store.alert(id)!!.ended)
        net.connect(b, c); runCurrent(); advanceTimeBy(35_000); runCurrent()
        assertTrue(c.store.alert(id)!!.ended)
        assertEquals(1, c.sink.alarms.count { it == id })
        assertTrue(net.sent.filter { it.type == "SOS_EVENT" }.all { it.ttl in 1..10 })
    }

    @Test fun rejectedSendSurvivesRestartAndNewReadyLink() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net); val b = Node("B", net)
        net.connect(a, b); net.reject = true; runCurrent()
        val id = a.repo.create("Medical")
        assertEquals("QUEUED", a.store.alert(id)!!.transmission)
        assertTrue(a.store.pending().isNotEmpty())
        net.reject = false; advanceTimeBy(5001); runCurrent()
        assertNotNull(b.store.alert(id))
        val restarted = Node("A", Network(backgroundScope), a.store)
        assertEquals(id, restarted.repo.create("Other"))
        assertTrue(restarted.sink.alarms.isEmpty())
    }

    @Test fun connectedBackstopRepairsAcceptedButDroppedEvent() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net); val b = Node("B", net)
        net.connect(a, b); runCurrent(); net.dropEvents = true
        val id = a.repo.create("Medical"); runCurrent(); advanceTimeBy(35_000); runCurrent()
        assertNotNull(b.store.alert(id))
        a.repo.end(id); runCurrent(); advanceTimeBy(35_000); runCurrent()
        assertTrue(b.store.alert(id)!!.ended)
        assertEquals(listOf(id), b.sink.alarms) // sync alarmed once when it brought the alert; the end stays silent
    }

    @Test fun rejectedUrgentRelayPersistsThenRetries() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net); val b = Node("B", net); val c = Node("C", net)
        net.connect(a, b); net.connect(b, c); runCurrent(); net.rejectFrom = "B"
        val id = a.repo.create("Medical"); runCurrent()
        assertNotNull(b.store.alert(id)); assertNull(c.store.alert(id))
        assertTrue(b.store.pending().any { it.sosId == id })
        net.rejectFrom = null; advanceTimeBy(5001); runCurrent()
        assertNotNull(c.store.alert(id))
        assertEquals(1, c.sink.alarms.count { it == id })
    }

    private suspend fun latest(n: Node, id: String) =
        SosProtocol.json.decodeFromString<SosEvent>(n.store.alert(id)!!.eventJson)

    @Test fun fourRaisesInAMinuteAllApplyAndTheFifthIsLimited() = runTest {
        val net = Network(backgroundScope)
        val gate = edu.cit.resqmesh.core.network.ratelimit.InboundOriginGate(
            edu.cit.resqmesh.core.network.ratelimit.OriginRateLimiter())
        val a = Node("A", net); val b = Node("B", net, gate = gate)
        repeat(4) {
            val id = a.repo.create("Medical")
            assertEquals(SosIngestion.APPLIED, b.repo.ingest(latest(a, id), true, "X"))
            a.repo.end(id)
        }
        assertEquals(4, b.sink.alarms.size)
        val fifth = a.repo.create("Medical")
        assertEquals(SosIngestion.REJECTED, b.repo.ingest(latest(a, fifth), true, "X"))
    }

    @Test fun laterRevisionOfAnUnknownSosIsChargedAsUpdateNotNew() = runTest {
        val net = Network(backgroundScope)
        val gate = edu.cit.resqmesh.core.network.ratelimit.InboundOriginGate(
            edu.cit.resqmesh.core.network.ratelimit.OriginRateLimiter())
        val a = Node("A", net); val b = Node("B", net, gate = gate)
        val id = a.repo.create("Medical")
        a.repo.updateLocation(id, 10.0, 20.0, 5f, 1001)
        assertEquals(2L, latest(a, id).revision)
        assertEquals(SosIngestion.APPLIED, b.repo.ingest(latest(a, id), true, "X")) // refine arrives first
        val prefix = SosProtocol.prefix(a.signing.publicKey)
        repeat(4) { assertTrue(gate.admitVerifiedSos("X", prefix, true)) } // the full SOS_NEW burst is untouched
    }

    @Test fun syncDeliveredActiveRecentSosAlarmsOnceAndNeverTwice() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net); val b = Node("B", net)
        val id = a.repo.create("Medical")
        val event = latest(a, id)
        assertEquals(SosIngestion.APPLIED, b.repo.ingest(event, live = false))
        assertEquals(listOf(id), b.sink.alarms)
        assertEquals(SosIngestion.DUPLICATE, b.repo.ingest(event, live = false))
        assertEquals(SosIngestion.DUPLICATE, b.repo.ingest(event, live = true))
        assertEquals(listOf(id), b.sink.alarms)
    }

    @Test fun syncDeliveredEndedOrOldSosStaysSilent() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net)
        val old = Node("B", net, clock = { 1000L + 10 * 60_000L + 1 })
        val ended = Node("C", net)
        val oldId = a.repo.create("Medical")
        assertEquals(SosIngestion.APPLIED, old.repo.ingest(latest(a, oldId), live = false))
        a.repo.end(oldId)
        assertEquals(SosIngestion.APPLIED, ended.repo.ingest(latest(a, oldId), live = false))
        assertTrue(old.sink.alarms.isEmpty())
        assertTrue(ended.sink.alarms.isEmpty())
    }

    private class CountingCharge(var allow: Boolean = true) : edu.cit.resqmesh.core.model.SosRaiseCharge {
        var charged = 0; var refunded = 0
        override fun tryCharge() = allow.also { if (it) charged++ }
        override fun refund() { refunded++ }
    }

    @Test fun reopenIsNeverChargedButRaiseAfterEndIs() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net)
        val charge = CountingCharge()
        val first = a.repo.raise("Medical", charge) as edu.cit.resqmesh.core.model.SosRaiseResult.Created
        // A double tap while the first is active reopens it and charges nothing.
        val reopened = a.repo.raise("Medical", charge) as edu.cit.resqmesh.core.model.SosRaiseResult.Reopened
        assertEquals(first.id, reopened.id)
        assertEquals(1, charge.charged)
        a.repo.end(first.id)
        assertTrue(a.repo.raise("Medical", charge) is edu.cit.resqmesh.core.model.SosRaiseResult.Created)
        assertEquals(2, charge.charged)
        charge.allow = false
        a.repo.end(a.store.all().first { !it.ended }.sosId)
        assertEquals(edu.cit.resqmesh.core.model.SosRaiseResult.Throttled, a.repo.raise("Medical", charge))
        assertTrue(a.store.all().none { !it.ended })
    }

    @Test fun concurrentDoubleTapChargesOnce() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net)
        val charge = CountingCharge()
        val results = listOf(async { a.repo.raise("Medical", charge) }, async { a.repo.raise("Medical", charge) })
            .awaitAll()
        assertEquals(1, results.count { it is edu.cit.resqmesh.core.model.SosRaiseResult.Created })
        assertEquals(1, charge.charged)
    }

    @Test fun refineFirstAlarmsOnceAndRevisionOneLaterStaysSilent() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net); val b = Node("B", net)
        val id = a.repo.create("Medical")
        val first = latest(a, id)
        a.repo.updateLocation(id, 10.0, 20.0, 5f, 1001)
        assertEquals(SosIngestion.APPLIED, b.repo.ingest(latest(a, id), true))
        assertEquals(listOf(id), b.sink.alarms)
        assertEquals(SosIngestion.STALE, b.repo.ingest(first, true))
        assertEquals(listOf(id), b.sink.alarms)
    }

    @Test fun ownSigningKeyNeverAlarmsEvenWithAnotherNodeId() = runTest {
        val net = Network(backgroundScope); val a = Node("A", net)
        val id = a.repo.create("Medical")
        // Same Keystore key under a different node id: build the event as seen by a phone that owns key-A.
        val same = Node("A3", net, signingKey = "key-A")
        assertEquals(SosIngestion.APPLIED, same.repo.ingest(latest(a, id), live = false))
        assertTrue(same.sink.alarms.isEmpty())
    }

    @Test fun threeRaiseRefineEndCyclesWithinAMinuteAllApply() = runTest {
        val net = Network(backgroundScope)
        val gate = edu.cit.resqmesh.core.network.ratelimit.InboundOriginGate(
            edu.cit.resqmesh.core.network.ratelimit.OriginRateLimiter())
        val a = Node("A", net); val b = Node("B", net, gate = gate)
        repeat(3) { i ->
            val id = a.repo.create("Medical")
            assertEquals(SosIngestion.APPLIED, b.repo.ingest(latest(a, id), true, "X"))
            a.repo.updateLocation(id, 10.0, 20.0, 5f, 1001L + i)
            assertEquals(SosIngestion.APPLIED, b.repo.ingest(latest(a, id), true, "X"))
            a.repo.end(id)
            assertEquals(SosIngestion.APPLIED, b.repo.ingest(latest(a, id), true, "X"))
        }
        assertEquals(3, b.sink.alarms.size)
    }

    @Test fun threeReorderedRaisesWithRefinesFirstAreNeverLimited() = runTest {
        val net = Network(backgroundScope)
        val gate = edu.cit.resqmesh.core.network.ratelimit.InboundOriginGate(
            edu.cit.resqmesh.core.network.ratelimit.OriginRateLimiter())
        val a = Node("A", net); val b = Node("B", net, gate = gate)
        repeat(3) { i ->
            val id = a.repo.create("Medical")
            val first = latest(a, id)
            a.repo.updateLocation(id, 10.0, 20.0, 5f, 1001L + i)
            // Worst case per raise: the refine and then the late revision 1 are both charged as updates.
            assertEquals(SosIngestion.APPLIED, b.repo.ingest(latest(a, id), true, "X"))
            assertEquals(SosIngestion.STALE, b.repo.ingest(first, true, "X"))
            a.repo.end(id)
            assertEquals(SosIngestion.APPLIED, b.repo.ingest(latest(a, id), true, "X"))
        }
        assertEquals(3, b.sink.alarms.size)
    }
}
