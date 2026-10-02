package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.network.*
import com.example.testresqmesh.data.local.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.decodeFromByteArray
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
    private class Node(val id: String, val network: Network, store: MemoryStore = MemoryStore()) {
        val store = store
        val links = mutableMapOf<String, Long>()
        val peers = MeshReadyPeerEvents()
        val sink = Sink()
        val signing = Signing("key-$id")
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
        val repo = SosRepository(store, signing, identity, gateway, peers, network.scope, sink, now = { 1000 })
        init { network.nodes[id] = this }
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
        assertTrue(b.sink.alarms.isEmpty())
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
}
