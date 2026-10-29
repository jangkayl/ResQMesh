package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.DomainEventEntityFakeDao
import edu.cit.resqmesh.core.model.IncidentEntityFakeDao
import edu.cit.resqmesh.core.network.*
import edu.cit.resqmesh.data.local.dao.IncidentOfferDao
import edu.cit.resqmesh.data.local.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class IncidentAutomaticSyncTest {
    private class Radio(val scope: CoroutineScope) {
        val nodes = mutableMapOf<String, Node>()
        var drop: (String, String, MeshPayload) -> Boolean = { _, _, _ -> false }
        var reject: (String, MeshPayload) -> Boolean = { _, _ -> false }
        val sent = mutableListOf<MeshPayload>()
        var epoch = 1L
        fun connect(a: Node, b: Node) {
            a.links[b.id] = ++epoch; b.links[a.id] = epoch
            publish(a); publish(b)
        }
        fun disconnect(a: Node, b: Node) {
            a.links.remove(b.id); b.links.remove(a.id)
            publish(a); publish(b)
        }
        private fun publish(node: Node) {
            node.peers.update(node.links.keys.map { ConnectedDevice(it, "$it#$it", nodeId = it, isPayloadReady = true) })
        }
        fun send(from: Node, to: String, bytes: ByteArray): TransportDispatchResult {
            val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes)
            sent += payload
            if (to !in from.links) return TransportDispatchResult.REJECTED_NOT_READY
            if (reject(from.id, payload)) return TransportDispatchResult.REJECTED_QUEUE_FULL
            if (!drop(from.id, to, payload)) scope.launch {
                val receiver = nodes.getValue(to)
                if (from.id !in receiver.links) return@launch
                val callback = when (payload.type) {
                    "EVENT_SYNC_REQ" -> "onEventSyncRequest"
                    "EVENT_SYNC_RESP" -> "onEventSyncResponse"
                    "DOMAIN_EVENT" -> "onDomainEvent"
                    else -> return@launch
                }
                @Suppress("UNCHECKED_CAST")
                (receiver.callbacks[callback] as? ((String, MeshPayload) -> Unit))?.invoke(from.id, payload)
            }
            return TransportDispatchResult.ACCEPTED
        }
        fun op(p: MeshPayload): String = if (p.type.startsWith("EVENT_SYNC")) JSONObject(p.text).optString("op") else ""
    }

    private class Node(val id: String, val radio: Radio) {
        val links = mutableMapOf<String, Long>()
        val peers = MeshReadyPeerEvents()
        val incidents = IncidentEntityFakeDao()
        val events = DomainEventEntityFakeDao(incidents)
        val offers = MemoryOffers()
        val callbacks = mutableMapOf<String, Any?>()
        val gateway = Proxy.newProxyInstance(MeshNetworkGateway::class.java.classLoader,
            arrayOf(MeshNetworkGateway::class.java)) { _, method, args ->
            when {
                method.name.startsWith("setOn") -> { callbacks[method.name.removePrefix("set").replaceFirstChar { it.lowercase() }] = args!![0]; null }
                method.name.startsWith("getOn") -> callbacks[method.name.removePrefix("get").replaceFirstChar { it.lowercase() }]
                else -> when (method.name) {
                    "getMyDeviceName" -> "$id#$id"
                    "getMyNodeId" -> id
                    "currentMeshTtl" -> 6
                    "isDeviceBlocked" -> false
                    "hasReadyEndpoint" -> args!![0] in links
                    "linkEstablishedAt" -> links[args!![0]] ?: 0L
                    "sendPriorityPayload", "sendDirectPayload" -> radio.send(this, args!![0] as String, args[1] as ByteArray)
                    "broadcastPriorityPayload", "broadcastPayload" -> BroadcastDispatchResult(links.keys
                        .filter { args!!.size < 2 || it != args[1] }.associateWith { radio.send(this, it, args!![0] as ByteArray) })
                    else -> null
                }
            }
        } as MeshNetworkGateway
        private val identity = object : IdentityProvider {
            val user = UserEntity(id, id, id, null, 1L)
            override suspend fun getOrCreateUser(displayName: String?) = user
            override fun observeUser(): Flow<UserEntity?> = flowOf(user)
            override suspend fun getUserId() = id
            override fun getDeviceId() = id
        }
        val signer = object : IncidentEventSigning {
            override val publicKey = "key-$id"
            override fun sign(event: DomainEventEntity) = "$publicKey:${IncidentSyncSnapshot.eventHash(event)}"
            override fun verify(event: DomainEventEntity, publicKey: String) = event.signature == "$publicKey:${IncidentSyncSnapshot.eventHash(event)}"
        }
        var repo: IncidentRepository
        var owner = CoroutineScope(radio.scope.coroutineContext + SupervisorJob(radio.scope.coroutineContext[Job]))
        init {
            radio.nodes[id] = this
            repo = repository(owner)
        }
        fun repository(scope: CoroutineScope) = IncidentRepository(incidents, events, identity, gateway, scope,
            peers, offers, eventSigning = signer)
        suspend fun create() = repo.createIncident("Medical", "Critical", "test", "area")
        suspend fun snapshot() = repo.syncSnapshot()
        fun restart() {
            owner.cancel()
            owner = CoroutineScope(radio.scope.coroutineContext + SupervisorJob(radio.scope.coroutineContext[Job]))
            repo = repository(owner)
        }
    }

    private suspend fun TestScope.settle(ms: Long = 12_000) { advanceTimeBy(ms); runCurrent() }

    @Test fun reconnectRecoversOfflineChangesWithoutReadyNotificationOrRefresh() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        val incident = a.create()
        radio.connect(a, b); settle()
        radio.disconnect(a, b); runCurrent()
        assertTrue(a.repo.cancelHelp(incident.incidentId))
        radio.connect(a, b); settle()
        assertEquals("CANCELLED", b.incidents.getIncidentById(incident.incidentId)?.status)
        assertEquals(a.snapshot().stateRoot, b.snapshot().stateRoot)
    }

    @Test fun equalReporterVersionsDoNotHideHelperOffersOrConfirmation() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        val incident = a.create(); radio.connect(a, b); settle()
        radio.drop = { _, _, p -> p.type == "DOMAIN_EVENT" }
        assertTrue(b.repo.offerHelp(incident.incidentId, "Nearby")); settle()
        assertEquals(1L, a.incidents.getIncidentById(incident.incidentId)?.version)
        val offer = a.offers.getForIncident(incident.incidentId).single()
        assertTrue(a.repo.selectLead(incident.incidentId, offer.offerId)); settle()
        assertTrue(b.repo.confirmLead(incident.incidentId)); settle()
        assertEquals("RESPONDING", a.incidents.getIncidentById(incident.incidentId)?.status)
        assertEquals(a.snapshot().stateRoot, b.snapshot().stateRoot)
    }

    @Test fun unchangedPeersExchangeDigestsWithoutEventHistory() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        a.create(); radio.connect(a, b); settle(); radio.sent.clear()
        settle(36_000)
        assertTrue(radio.sent.any { radio.op(it) == "DIGEST" })
        assertFalse(radio.sent.any { radio.op(it) in setOf("GET_EVENTS", "PUT_EVENTS", "INVENTORY", "CATALOG") })
    }

    @Test fun periodicCheckRecoversLostConnectedUpdateWhenChangeHintsAreLost() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        val incident = a.create(); radio.connect(a, b); settle()
        radio.drop = { _, _, _ -> true }
        assertTrue(a.repo.cancelHelp(incident.incidentId)); settle(1_000)
        radio.drop = { _, _, p -> p.type == "DOMAIN_EVENT" }
        settle(65_000)
        assertEquals("CANCELLED", b.incidents.getIncidentById(incident.incidentId)?.status)
    }

    @Test fun droppedResponseIsRetriedWithFreshEnvelope() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        a.create(); var dropped = false
        radio.drop = { _, _, p -> (p.type == "EVENT_SYNC_RESP" && radio.op(p) == "GET_EVENTS" && !dropped).also { if (it) dropped = true } }
        radio.connect(a, b); settle(75_000)
        assertTrue(dropped)
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
        val requests = radio.sent.filter { it.type == "EVENT_SYNC_REQ" && radio.op(it) == "GET_EVENTS" }
        assertTrue(requests.size >= 2)
        assertEquals(requests.size, requests.map { it.id }.distinct().size)
    }

    @Test fun queueRejectionKeepsDurableHistoryUntilCapacityReturns() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        a.create(); radio.reject = { _, _ -> true }; radio.connect(a, b); settle(18_000)
        assertTrue(b.incidents.getSyncIncidents().isEmpty())
        radio.reject = { _, _ -> false }; settle(60_000)
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
    }

    @Test fun catalogueAndEventPagesCoverMoreThanTwentyFourRecordsBothDirections() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        repeat(30) { a.create(); b.create() }
        radio.connect(a, b); settle(120_000)
        assertEquals(60, a.incidents.getSyncIncidents().size)
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
        assertEquals(a.snapshot().stateRoot, b.snapshot().stateRoot)
        assertTrue(radio.sent.filter { radio.op(it) == "CATALOG" && it.type == "EVENT_SYNC_RESP" }
            .any { JSONObject(it.text).optString("next").isNotEmpty() })
    }

    @Test fun staleSnapshotRestartsInsteadOfSkippingHistory() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        repeat(26) { a.create() }
        var injected = false
        radio.drop = { from, _, p ->
            if (!injected && from == "A" && p.type == "EVENT_SYNC_RESP" && radio.op(p) == "CATALOG") {
                injected = true; backgroundScope.launch { a.create() }
            }
            p.type == "DOMAIN_EVENT"
        }
        radio.connect(a, b); settle(120_000)
        assertTrue(injected)
        assertEquals(27, b.incidents.getSyncIncidents().size)
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
    }

    @Test fun repairedEventsPropagateAcrossABCRoute() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio); val c = Node("C", radio)
        radio.drop = { _, _, p -> p.type == "DOMAIN_EVENT" }
        radio.connect(a, b); radio.connect(b, c); val incident = a.create(); settle(90_000)
        assertNotNull(c.incidents.getIncidentById(incident.incidentId))
        assertEquals(a.snapshot().stateRoot, c.snapshot().stateRoot)
    }

    @Test fun sameHistoryWithCorruptProjectionIsRebuiltFromSignedEvents() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        val incident = a.create(); radio.connect(a, b); settle()
        b.incidents.insertOrUpdate(b.incidents.getIncidentById(incident.incidentId)!!.copy(status = "CANCELLED"))
        settle(45_000)
        assertEquals("OPEN", b.incidents.getIncidentById(incident.incidentId)?.status)
        assertEquals(a.snapshot().stateRoot, b.snapshot().stateRoot)
    }

    @Test fun replacingEndpointCancelsOldRequestAndRecoversOnNewSession() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        a.create(); radio.drop = { _, _, _ -> true }; radio.connect(a, b); settle(1_000)
        radio.disconnect(a, b); runCurrent(); radio.drop = { _, _, _ -> false }; radio.connect(a, b); settle()
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
    }

    @Test fun forgedEventsAndConflictingIdsCannotPoisonDigestOrOverwriteHistory() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val incident = a.create()
        val event = a.events.getEventsForEntity(incident.incidentId).single()
        val root = a.snapshot().historyRoot
        assertEquals(IncidentIngestionResult.REJECTED, a.repo.ingestIncomingEvent(event.copy(payloadJson = "{}")))
        val bad = event.copy(eventId = "forged", signature = "bad")
        val b = Node("B", radio)
        assertEquals(IncidentIngestionResult.REJECTED, b.repo.ingestIncomingEvent(bad))
        assertTrue(b.snapshot().events.isEmpty())
        assertEquals(root, a.snapshot().historyRoot)
    }

    @Test fun processRestartRecoversFromPersistedEventsAndCurrentPeerSnapshot() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        a.create(); radio.drop = { _, _, _ -> true }; radio.connect(a, b); settle(1_000)
        b.restart(); radio.drop = { _, _, _ -> false }; settle(65_000)
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
    }

    @Test fun legacyEventStoredBeforePredecessorCanApplyOnReplay() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio)
        val created = DomainEventEntity("create", "legacy", "INCIDENT", "INCIDENT_CREATED", "reporter", "Reporter", 1, 1, "{}")
        val assigned = DomainEventEntity("assign", "legacy", "INCIDENT", "INCIDENT_ASSIGNED", "helper", "Helper", 2, 2, "{}")
        val resolved = DomainEventEntity("resolve", "legacy", "INCIDENT", "INCIDENT_RESOLVED", "helper", "Helper", 3, 3, "{}")
        assertTrue(a.repo.applyIncomingEvent(created))
        assertEquals(IncidentIngestionResult.DEFERRED, a.repo.ingestIncomingEvent(resolved))
        assertFalse(a.events.getEventById("resolve")!!.applied)
        a.repo.handleSyncResponse("peer", JSONObject().put("events", org.json.JSONArray(listOf(assigned, resolved)
            .map(IncidentSyncSnapshot::eventJson))).toString())
        assertEquals("RESOLVED", a.incidents.getIncidentById("legacy")?.status)
        assertTrue(a.events.getEventById("resolve")!!.applied)
    }

    @Test fun periodicSnapshotDetectsSameEndpointNewGenerationWithoutNotification() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        a.create(); radio.drop = { _, _, _ -> true }; radio.connect(a, b); settle(1_000)
        a.links["B"] = ++radio.epoch; b.links["A"] = radio.epoch
        radio.drop = { _, _, _ -> false }; settle(65_000)
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
    }

    @Test fun offlineHelperRevisionsRemainHistoryAfterClosureAndConverge() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        val incident = a.create(); radio.connect(a, b); settle()
        assertTrue(b.repo.offerHelp(incident.incidentId, "first")); settle()
        radio.disconnect(a, b); runCurrent()
        assertTrue(b.repo.offerHelp(incident.incidentId, "second"))
        assertTrue(b.repo.offerHelp(incident.incidentId, "third"))
        assertTrue(a.repo.cancelHelp(incident.incidentId))
        radio.connect(a, b); settle(90_000)
        assertEquals("CANCELLED", b.incidents.getIncidentById(incident.incidentId)?.status)
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
        assertEquals(a.snapshot().stateRoot, b.snapshot().stateRoot)
    }

    private suspend fun Node.age(incidentId: String, ageMs: Long) {
        val incident = incidents.getIncidentById(incidentId)!!
        val then = System.currentTimeMillis() - ageMs
        incidents.insertOrUpdate(incident.copy(createdAt = then, updatedAt = then))
    }

    @Test fun newPhoneGetsOpenAndRecentlyClosedIncidentsButNotOldClosedOnes() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        val oldOpen = a.create(); a.age(oldOpen.incidentId, 3 * DAY)
        val oldClosed = a.create(); assertTrue(a.repo.cancelHelp(oldClosed.incidentId))
        a.age(oldClosed.incidentId, 2 * DAY)
        val recentClosed = a.create(); assertTrue(a.repo.cancelHelp(recentClosed.incidentId))
        runCurrent() // flush the offline broadcasts before the phones meet
        radio.connect(a, b); settle(60_000)
        assertNotNull(b.incidents.getIncidentById(oldOpen.incidentId))
        assertNotNull(b.incidents.getIncidentById(recentClosed.incidentId))
        assertNull(b.incidents.getIncidentById(oldClosed.incidentId))
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
    }

    @Test fun newPhoneReceivesAtMostTheNewestHundredIncidents() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        repeat(130) { a.create() }
        runCurrent() // flush the offline broadcasts before the phones meet
        radio.connect(a, b); settle(300_000)
        assertEquals(IncidentSyncScope.MAX_INCIDENTS, b.incidents.getSyncIncidents().size)
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
        assertEquals(a.snapshot().stateRoot, b.snapshot().stateRoot)
    }

    @Test fun liveBurstIsPacedButCatchUpSyncDeliversEverything() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        radio.connect(a, b); settle()
        radio.drop = { _, _, p -> p.type.startsWith("EVENT_SYNC") } // live path only
        repeat(6) { a.create() }
        runCurrent()
        assertEquals(4, b.incidents.getSyncIncidents().size) // receiver burst of 4 per reporter key
        radio.drop = { _, _, _ -> false }
        settle(60_000)
        assertEquals(6, b.incidents.getSyncIncidents().size)
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
    }

    @Test fun futureDatedEventIsRejectedWithoutBeingStored() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        val incident = a.create()
        val event = a.events.getEventsForEntity(incident.incidentId).single()
        val future = event.copy(eventId = "future", entityId = "future-incident",
            timestamp = System.currentTimeMillis() + 60 * 60 * 1000, signature = null)
        val signed = future.copy(signature = a.signer.sign(future))
        assertEquals(IncidentIngestionResult.REJECTED, b.repo.ingestIncomingEvent(signed))
        assertNull(b.events.getEventById("future"))
    }

    @Test fun liveWithdrawalIsNeverPacedAfterTheOfferBurst() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        val incident = a.create(); radio.connect(a, b); settle()
        radio.drop = { _, _, p -> p.type.startsWith("EVENT_SYNC") } // live path only
        repeat(6) { assertTrue(b.repo.offerHelp(incident.incidentId, "note $it")); runCurrent() }
        assertEquals(6L, a.offers.getForIncident(incident.incidentId).single().revision)
        assertTrue(b.repo.withdrawOffer(incident.incidentId)); runCurrent()
        assertTrue(a.offers.getForIncident(incident.incidentId).single().withdrawn)
    }

    @Test fun forgedLiveCreationNeverSpendsTheVictimsBudget() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        radio.connect(a, b); settle()
        val real = a.create(); runCurrent()
        val event = a.events.getEventsForEntity(real.incidentId).single()
        repeat(10) {
            val forged = IncidentSyncSnapshot.eventJson(
                event.copy(eventId = "forged-$it", entityId = "fake-$it", signature = "bad"))
            @Suppress("UNCHECKED_CAST")
            val deliver = b.callbacks["onDomainEvent"] as (String, MeshPayload) -> Unit
            deliver("A", MeshPayload(id = "f$it", type = "DOMAIN_EVENT",
                senderName = "A#A", senderNodeId = "A", text = forged.toString()))
        }
        runCurrent()
        radio.drop = { _, _, p -> p.type.startsWith("EVENT_SYNC") }
        repeat(3) { a.create() }
        runCurrent()
        assertEquals(4, b.incidents.getSyncIncidents().size)
    }

    @Test fun concurrentDeclineAndRevocationWhileApartConverge() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        val incident = a.create(); radio.connect(a, b); settle()
        assertTrue(b.repo.offerHelp(incident.incidentId, "Nearby")); settle()
        val offer = a.offers.getForIncident(incident.incidentId).single()
        assertTrue(a.repo.selectLead(incident.incidentId, offer.offerId)); settle()
        assertEquals("AWAITING_HELPER", b.incidents.getIncidentById(incident.incidentId)?.status)
        radio.disconnect(a, b); runCurrent()
        assertTrue(b.repo.declineLead(incident.incidentId))
        assertTrue(a.repo.revokeLead(incident.incidentId))
        radio.connect(a, b); settle(90_000)
        assertEquals(a.incidents.getIncidentById(incident.incidentId), b.incidents.getIncidentById(incident.incidentId))
        assertEquals(0, a.snapshot().pending); assertEquals(0, b.snapshot().pending)
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
        assertEquals(a.snapshot().stateRoot, b.snapshot().stateRoot)
    }

    @Test fun readyNotificationReconcilesSameEndpointReconnectBeforePeriodicCheck() = runTest {
        val radio = Radio(backgroundScope); val a = Node("A", radio); val b = Node("B", radio)
        a.create(); radio.drop = { _, _, _ -> true }; radio.connect(a, b); settle(1_000)
        // Same endpoint, new link generation; the READY list is unchanged so the state flow stays silent.
        a.links["B"] = ++radio.epoch; b.links["A"] = radio.epoch
        radio.drop = { _, _, _ -> false }
        a.peers.publish(ConnectedDevice("B", "B#B", nodeId = "B", isPayloadReady = true))
        b.peers.publish(ConnectedDevice("A", "A#A", nodeId = "A", isPayloadReady = true))
        settle(8_000)
        assertEquals(a.snapshot().historyRoot, b.snapshot().historyRoot)
    }

    private companion object { const val DAY = 24L * 60 * 60 * 1000 }

    private class MemoryOffers : IncidentOfferDao {
        val rows = mutableMapOf<String, IncidentOfferEntity>()
        val flow = MutableStateFlow<List<IncidentOfferEntity>>(emptyList())
        override fun observeForIncident(incidentId: String) = flow.map { it.filter { o -> o.incidentId == incidentId } }
        override fun observeAllActiveOffers() = flow.map { it.filter { o -> !o.withdrawn } }
        override suspend fun getForIncident(incidentId: String) = rows.values.filter { it.incidentId == incidentId }
        override suspend fun getById(offerId: String) = rows[offerId]
        override suspend fun getForIncidents(incidentIds: List<String>) =
            rows.values.filter { it.incidentId in incidentIds }
            .sortedWith(compareBy<IncidentOfferEntity> { it.incidentId }.thenBy { it.offerId })
        override suspend fun getByHelper(incidentId: String, helperKey: String) = rows.values.firstOrNull { it.incidentId == incidentId && it.helperKey == helperKey }
        override suspend fun deleteForIncident(incidentId: String): Int {
            val ids = getForIncident(incidentId).map { it.offerId }; ids.forEach { rows.remove(it) }; flow.value = rows.values.toList(); return ids.size
        }
        override suspend fun insertOffer(offerId: String, incidentId: String, helperKey: String, helperNodeId: String,
            helperName: String, note: String, revision: Long, withdrawn: Boolean, updatedAt: Long): Long {
            rows[offerId] = IncidentOfferEntity(offerId, incidentId, helperKey, helperNodeId, helperName, note, revision, withdrawn, updatedAt)
            flow.value = rows.values.toList(); return 1L
        }
    }
}
