package com.example.testresqmesh.core.network.bluetooth

import android.bluetooth.BluetoothDevice
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePeerAdmissionControllerTest {

    private class TestScheduler : BleAdmissionScheduler {
        val postedTasks = mutableListOf<() -> Unit>()
        val delayedTasks = mutableListOf<Pair<Long, () -> Unit>>()

        override fun post(action: () -> Unit) {
            postedTasks.add(action)
        }

        override fun postDelayed(delayMs: Long, action: () -> Unit) {
            delayedTasks.add(delayMs to action)
        }

        fun runAll() {
            val currentPosted = postedTasks.toList()
            postedTasks.clear()
            currentPosted.forEach { it() }

            val currentDelayed = delayedTasks.toList()
            delayedTasks.clear()
            currentDelayed.forEach { it.second() }
        }
    }

    private class TestFixture(
        val localNameStr: String = "Alice#A1",
        var localScoreStr: String = "900AA",
        var directLinks: Int = 0,
        var maxLinks: Int = 3,
        var indirectRoute: Boolean = false,
        var payloadReadyDirect: Boolean = false,
        var connectResult: BleConnectStartResult = BleConnectStartResult.STARTED,
        var handshakeInfoSupplier: () -> Pair<String?, Long> = { null to 0L },
        var readyPeers: Int = 0,
        var hasReadyLinkToPeer: (String) -> Boolean = { false }
    ) {
        val store = BleStateStore()
        val scheduler = TestScheduler()
        var clockTime = 1_000_000L
        val decisions = mutableListOf<BleAdmissionDecision>()
        val connectCalls = mutableListOf<Pair<String, String>>()
        val blockedPeers = mutableSetOf<String>()
        val redundant = mutableSetOf<String>()
        val retired = mutableListOf<String>()
        var idle = true

        val controller = BlePeerAdmissionController(
            store = store,
            scheduler = scheduler,
            localName = { localNameStr },
            isBlocked = { blockedPeers.contains(it) },
            hasLinkToIdentity = { false },
            hasIndirectRoute = { indirectRoute },
            hasPayloadReadyDirectLink = { payloadReadyDirect },
            hasReadyLinkToIdentity = { hasReadyLinkToPeer(it) },
            directLinkCount = { directLinks },
            maxDirectLinks = { maxLinks },
            electionScore = { localScoreStr },
            latestEndpointForIdentity = { _, fallback -> fallback },
            connect = { endpoint, peer ->
                connectCalls.add(endpoint to peer)
                connectResult
            },
            onScanned = {},
            onDisconnected = {},
            onPulse = {},
            handshakeInfo = { handshakeInfoSupplier() },
            distinctReadyPeerCount = { readyPeers },
            clock = { clockTime },
            jitterMs = { min, _ -> min },
            onDecision = { decisions.add(it) },
            canRetireForBridge = { it in redundant },
            isTransportIdle = { idle },
            disconnectEndpoint = { retired.add(it); directLinks-- }
        )
    }

    @Test
    fun zeroOrOneDirectLinksElectedInitiatorNoRouteStartsAfterJitter() {
        val f = TestFixture(directLinks = 0, localScoreStr = "900AA")
        val ad = BleAdvertisement(
            endpointId = "11:22:33:44:55:66",
            peerName = "Bob#B2",
            nodeId = "B2",
            electionScore = "800BB",
            directConnections = 0
        )

        f.controller.handle(ad)

        assertEquals(1, f.decisions.size)
        val initialDecision = f.decisions[0]
        assertEquals(BleAdmissionReason.QUEUED_INITIATOR, initialDecision.reason)
        assertEquals(BleConnectStartResult.DEFERRED, initialDecision.result)
        assertEquals("local", initialDecision.electionWinner)
        assertEquals(0, initialDecision.distinctDirectPeers)
        assertEquals(0, initialDecision.distinctReadyPeers)
        assertEquals(0, initialDecision.directSockets)
        assertFalse(initialDecision.hasIndirectRoute)
        assertTrue(f.connectCalls.isEmpty())

        // Run delayed tasks (drain after jitter)
        f.clockTime += 500L
        f.scheduler.runAll()

        assertEquals(1, f.connectCalls.size)
        assertEquals("11:22:33:44:55:66" to "Bob#B2", f.connectCalls[0])
        val drainDecision = f.decisions.last()
        assertEquals(BleAdmissionReason.DRAIN_STARTED, drainDecision.reason)
        assertEquals(BleConnectStartResult.STARTED, drainDecision.result)
        assertEquals(1, drainDecision.attempts)
    }

    @Test
    fun twoDirectLinksPeerAdvertisesZeroMayProceed() {
        val f = TestFixture(directLinks = 2, localScoreStr = "900AA")
        val ad = BleAdvertisement(
            endpointId = "11:22:33:44:55:66",
            peerName = "Charlie#C3",
            nodeId = "C3",
            electionScore = "800CC",
            directConnections = 0
        )

        f.controller.handle(ad)

        assertEquals(BleAdmissionReason.QUEUED_INITIATOR, f.decisions.last().reason)
        f.clockTime += 500L
        f.scheduler.runAll()

        assertEquals(BleAdmissionReason.DRAIN_STARTED, f.decisions.last().reason)
        assertEquals(1, f.connectCalls.size)
    }

    @Test
    fun spareThirdSlotBridgesAnUnreachableConnectedCluster() {
        val f = TestFixture(directLinks = 2, localScoreStr = "900AA")
        f.controller.handle(BleAdvertisement("11:22:33:44:55:66", "Dave#D4", "D4", "800DD", 1))
        assertEquals(BleAdmissionReason.QUEUED_INITIATOR, f.decisions.last().reason)
        f.clockTime += 500L
        f.scheduler.runAll()
        assertEquals(1, f.connectCalls.size)
        assertEquals(BleAdmissionReason.DRAIN_STARTED, f.decisions.last().reason)
    }

    @Test
    fun indirectRoutePlusReadyDirectNeighborExplicitRoutePreservationDeferralIsRecorded() {
        val f = TestFixture(
            indirectRoute = true,
            payloadReadyDirect = true,
            localScoreStr = "900AA"
        )
        val ad = BleAdvertisement(
            endpointId = "11:22:33:44:55:66",
            peerName = "Eve#E5",
            nodeId = "E5",
            electionScore = "800EE",
            directConnections = 0
        )

        f.controller.handle(ad)

        assertEquals(1, f.decisions.size)
        val decision = f.decisions[0]
        assertEquals(BleAdmissionReason.ROUTE_PRESERVED, decision.reason)
        assertEquals(BleConnectStartResult.DEFERRED, decision.result)
        assertTrue(decision.hasIndirectRoute)
        assertTrue(f.connectCalls.isEmpty())
    }

    @Test
    fun indirectRouteButNoReadyDirectNeighborMayBootstrapThroughNormalGates() {
        val f = TestFixture(
            indirectRoute = true,
            payloadReadyDirect = false, // No ready direct neighbor remains
            localScoreStr = "900AA"
        )
        val ad = BleAdvertisement(
            endpointId = "11:22:33:44:55:66",
            peerName = "Eve#E5",
            nodeId = "E5",
            electionScore = "800EE",
            directConnections = 0
        )

        f.controller.handle(ad)

        assertEquals(BleAdmissionReason.QUEUED_INITIATOR, f.decisions.last().reason)
        f.clockTime += 500L
        f.scheduler.runAll()

        assertEquals(BleAdmissionReason.DRAIN_STARTED, f.decisions.last().reason)
        assertEquals(1, f.connectCalls.size)
    }

    @Test
    fun busyHandshakeCandidateRemainsQueuedAndRetries() {
        val f = TestFixture(
            localScoreStr = "900AA",
            connectResult = BleConnectStartResult.DEFERRED,
            handshakeInfoSupplier = { "client:11:22:33:44:55:66:1" to 1500L }
        )
        val ad = BleAdvertisement(
            endpointId = "AA:BB:CC:DD:EE:FF",
            peerName = "Frank#F6",
            nodeId = "F6",
            electionScore = "800FF",
            directConnections = 0
        )

        f.controller.handle(ad)
        f.clockTime += 500L
        f.scheduler.runAll()

        val drainDeferred = f.decisions.last()
        assertEquals(BleAdmissionReason.DRAIN_DEFERRED, drainDeferred.reason)
        assertEquals(BleConnectStartResult.DEFERRED, drainDeferred.result)
        assertEquals("client:…55:66:1", drainDeferred.handshakeOwner)
        assertEquals(1500L, drainDeferred.handshakeAgeMs)

        // Handshake finishes; connect succeeds on retry
        f.handshakeInfoSupplier = { null to 0L }
        f.connectResult = BleConnectStartResult.STARTED
        f.clockTime += BleBootstrapRetryPolicy.BUSY_RETRY_MS + 10L
        f.scheduler.runAll()

        val drainStarted = f.decisions.last()
        assertEquals(BleAdmissionReason.DRAIN_STARTED, drainStarted.reason)
        assertEquals(BleConnectStartResult.STARTED, drainStarted.result)
    }

    @Test
    fun configuringOrFailedSocketCountAndCleanupAreVisibleNoPermanentFalseCapacity() {
        val f = TestFixture(
            localScoreStr = "900AA",
            connectResult = BleConnectStartResult.REJECTED
        )
        val ad = BleAdvertisement(
            endpointId = "AA:BB:CC:DD:EE:FF",
            peerName = "Grace#G7",
            nodeId = "G7",
            electionScore = "800GG",
            directConnections = 0
        )

        f.controller.handle(ad)
        f.clockTime += 500L
        f.scheduler.runAll()

        val rejectDecision = f.decisions.last()
        assertEquals(BleAdmissionReason.DRAIN_REJECTED, rejectDecision.reason)
        assertEquals(BleConnectStartResult.REJECTED, rejectDecision.result)
        assertEquals(1, rejectDecision.attempts)
        assertEquals(0, rejectDecision.directSockets)
    }

    @Test
    fun lowerLocalElectionScoreLocalYieldIsRecordedAndRemoteInitiationExpected() {
        val f = TestFixture(
            localScoreStr = "700AA",
            directLinks = 0
        )
        val ad = BleAdvertisement(
            endpointId = "AA:BB:CC:DD:EE:FF",
            peerName = "Heidi#H8",
            nodeId = "H8",
            electionScore = "900HH",
            directConnections = 0
        )

        f.controller.handle(ad)

        assertEquals(1, f.decisions.size)
        val decision = f.decisions[0]
        assertEquals(BleAdmissionReason.ELECTION_YIELD, decision.reason)
        assertEquals(BleConnectStartResult.DEFERRED, decision.result)
        assertEquals("peer", decision.electionWinner)
        assertTrue(f.connectCalls.isEmpty())

        // Advancing clock should NOT start any connection because candidate yielded
        f.clockTime += 1000L
        f.scheduler.runAll()
        assertTrue(f.connectCalls.isEmpty())
    }

    @Test
    fun logStringPreservesPrivacyAndExcludesFullMac() {
        val decision = BleAdmissionDecision(
            peerSafeLabel = "node:A1B2",
            endpointSuffix = "…55:66",
            reason = BleAdmissionReason.QUEUED_INITIATOR,
            result = BleConnectStartResult.DEFERRED,
            distinctDirectPeers = 1,
            distinctReadyPeers = 1,
            directSockets = 2,
            maxDirectLinks = 3,
            peerAdvertisedConnections = 0,
            hasIndirectRoute = false,
            localScore = "0889F",
            peerScore = "0722A",
            electionWinner = "local",
            candidateAgeMs = 120L,
            attempts = 0,
            handshakeOwner = "client:…55:66:1",
            handshakeAgeMs = 300L,
            lockOwner = "none",
            lockAgeMs = 0L
        )

        val log = decision.toLogString()
        assertTrue(log.contains("peer=node:A1B2"))
        assertTrue(log.contains("endpoint=…55:66"))
        assertTrue(log.contains("reason=QUEUED_INITIATOR"))
        assertTrue(log.contains("result=DEFERRED"))
        assertTrue(log.contains("peers=1"))
        assertTrue(log.contains("readyPeers=1"))
        assertTrue(log.contains("sockets=2"))
        assertTrue(log.contains("max=3"))
        assertTrue(log.contains("peerConns=0"))
        assertTrue(log.contains("route=false"))
        assertTrue(log.contains("election=[local=0889F peer=0722A winner=local]"))
        assertTrue(log.contains("candidate=[age=120ms attempts=0]"))
        assertTrue(log.contains("handshake=[owner=client:…55:66:1 age=300ms]"))
        assertTrue(log.contains("lock=[owner=none age=0ms]"))
        assertFalse("Full MAC must not be logged", log.contains("11:22:33:44:55:66"))
    }

    @Test
    fun isolatedNodeElectionYieldTriggersFallbackInitiatorIfPeerFailsToConnect() {
        val f = TestFixture(directLinks = 0, readyPeers = 0, localScoreStr = "700AA")
        val ad = BleAdvertisement(
            endpointId = "11:22:33:44:55:66",
            peerName = "Bob#B2",
            nodeId = "B2",
            electionScore = "800BB",
            directConnections = 0
        )

        f.controller.handle(ad)

        assertEquals(1, f.decisions.size)
        assertEquals(BleAdmissionReason.ELECTION_YIELD, f.decisions[0].reason)
        assertTrue(f.connectCalls.isEmpty())

        // Initial drain ran at now + 0ms, scheduling the fallback wait
        f.scheduler.runAll()
        assertTrue(f.connectCalls.isEmpty())

        // Delayed tasks now contains the fallback delay of 2500ms
        assertEquals(1, f.scheduler.delayedTasks.size)
        assertEquals(2_500L, f.scheduler.delayedTasks[0].first)

        // When 2500ms expires and peer never connected (isolated node remains at 0 ready peers)
        f.clockTime += 2500L
        f.scheduler.runAll()

        // Fallback watchdog should initiate connection
        assertEquals(1, f.connectCalls.size)
        assertEquals("11:22:33:44:55:66" to "Bob#B2", f.connectCalls[0])
    }

    @Test
    fun isolatedNodeElectionYieldDoesNotTriggerFallbackIfConnected() {
        val f = TestFixture(directLinks = 0, readyPeers = 0, localScoreStr = "700AA")
        val ad = BleAdvertisement(
            endpointId = "11:22:33:44:55:66",
            peerName = "Bob#B2",
            nodeId = "B2",
            electionScore = "800BB",
            directConnections = 0
        )

        f.controller.handle(ad)
        assertEquals(1, f.decisions.size)

        f.scheduler.runAll()
        assertTrue(f.connectCalls.isEmpty())

        // Peer connects as server before fallback timer expires
        f.readyPeers = 1
        f.directLinks = 1
        f.hasReadyLinkToPeer = { true }

        f.clockTime += 2500L
        f.scheduler.runAll()

        // Should NOT connect because ready peer exists
        assertTrue(f.connectCalls.isEmpty())
    }

    @Test
    fun rebootedPeerWithZeroConnectionsPurgesStaleZombieSocket() {
        val disconnected = mutableListOf<String>()
        val f = TestFixture(directLinks = 1, readyPeers = 0, localScoreStr = "900AA")
        val oldMac = "AA:BB:CC:DD:EE:FF"
        f.store.connectedEndpointNames[oldMac] = "Bob#B2"
        f.store.connectedEndpointIds.add(oldMac)

        val controllerWithDisconnect = BlePeerAdmissionController(
            store = f.store,
            scheduler = f.scheduler,
            localName = { f.localNameStr },
            isBlocked = { false },
            hasLinkToIdentity = { false },
            hasIndirectRoute = { false },
            hasPayloadReadyDirectLink = { false },
            hasReadyLinkToIdentity = { false },
            directLinkCount = { f.directLinks },
            maxDirectLinks = { f.maxLinks },
            electionScore = { f.localScoreStr },
            latestEndpointForIdentity = { _, fallback -> fallback },
            disconnectEndpoint = { disconnected.add(it) },
            connect = { endpoint, peer ->
                f.connectCalls.add(endpoint to peer)
                f.connectResult
            },
            onScanned = {},
            onDisconnected = {},
            onPulse = {},
            handshakeInfo = { f.handshakeInfoSupplier() },
            distinctReadyPeerCount = { f.readyPeers },
            clock = { f.clockTime },
            jitterMs = { min, _ -> min },
            onDecision = { f.decisions.add(it) }
        )

        val newMac = "11:22:33:44:55:66"
        val rebootAd = BleAdvertisement(
            endpointId = newMac,
            peerName = "Bob#B2",
            nodeId = "B2",
            electionScore = "700BB",
            directConnections = 0
        )

        controllerWithDisconnect.handle(rebootAd)

        assertTrue(disconnected.contains(oldMac))
        assertFalse(f.store.connectedEndpointNames.containsKey(oldMac))
        assertFalse(f.store.connectedEndpointIds.contains(oldMac))
    }

    private fun createDummyDevice(): BluetoothDevice {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(BluetoothDevice::class.java) as BluetoothDevice
    }

    @Test
    fun evictRotatedGhostDoesNotPurgeLiveActiveSocket() {
        val disconnected = mutableListOf<String>()
        val f = TestFixture(directLinks = 1, readyPeers = 0, localScoreStr = "900AA")
        val oldCentralMac = "AA:BB:CC:DD:EE:FF"
        f.store.connectedEndpointNames[oldCentralMac] = "Bob#B2"
        f.store.connectedEndpointIds.add(oldCentralMac)
        f.store.activeServerConnections[oldCentralMac] = createDummyDevice()
        f.store.connectionEstablishTime[oldCentralMac] = f.clockTime

        val controllerWithDisconnect = BlePeerAdmissionController(
            store = f.store,
            scheduler = f.scheduler,
            localName = { f.localNameStr },
            isBlocked = { false },
            hasLinkToIdentity = { false },
            hasIndirectRoute = { false },
            hasPayloadReadyDirectLink = { false },
            hasReadyLinkToIdentity = { false },
            directLinkCount = { f.directLinks },
            maxDirectLinks = { f.maxLinks },
            electionScore = { f.localScoreStr },
            latestEndpointForIdentity = { _, fallback -> fallback },
            disconnectEndpoint = { disconnected.add(it) },
            connect = { endpoint, peer ->
                f.connectCalls.add(endpoint to peer)
                f.connectResult
            },
            onScanned = {},
            onDisconnected = {},
            onPulse = {},
            handshakeInfo = { f.handshakeInfoSupplier() },
            distinctReadyPeerCount = { f.readyPeers },
            clock = { f.clockTime },
            jitterMs = { min, _ -> min },
            onDecision = { f.decisions.add(it) }
        )

        val newAdvMac = "11:22:33:44:55:66"
        val adv = BleAdvertisement(
            endpointId = newAdvMac,
            peerName = "Bob#B2",
            nodeId = "B2",
            electionScore = "700BB",
            directConnections = 0
        )

        controllerWithDisconnect.handle(adv)

        // Live connection must NOT be disconnected by evictRotatedGhost or zombie check
        assertFalse(disconnected.contains(oldCentralMac))
        assertTrue(f.store.connectedEndpointNames.containsKey(oldCentralMac))
        assertTrue(f.store.activeServerConnections.containsKey(oldCentralMac))
    }

    @Test
    fun healthyActiveSocketNotPurgedAsZombieWhenDirectConnectionsZero() {
        val disconnected = mutableListOf<String>()
        val f = TestFixture(directLinks = 1, readyPeers = 1, localScoreStr = "900AA")
        val oldCentralMac = "AA:BB:CC:DD:EE:FF"
        f.store.connectedEndpointNames[oldCentralMac] = "Bob#B2"
        f.store.connectedEndpointIds.add(oldCentralMac)
        f.store.activeServerConnections[oldCentralMac] = createDummyDevice()
        // Connection established and had interaction recently (only 1 second ago)
        f.store.connectionEstablishTime[oldCentralMac] = f.clockTime - 1_000L
        f.store.connectionInteractionTimes[oldCentralMac] = f.clockTime - 1_000L

        val controllerWithDisconnect = BlePeerAdmissionController(
            store = f.store,
            scheduler = f.scheduler,
            localName = { f.localNameStr },
            isBlocked = { false },
            hasLinkToIdentity = { false },
            hasIndirectRoute = { false },
            hasPayloadReadyDirectLink = { false },
            hasReadyLinkToIdentity = { false },
            directLinkCount = { f.directLinks },
            maxDirectLinks = { f.maxLinks },
            electionScore = { f.localScoreStr },
            latestEndpointForIdentity = { _, fallback -> fallback },
            disconnectEndpoint = { disconnected.add(it) },
            connect = { endpoint, peer ->
                f.connectCalls.add(endpoint to peer)
                f.connectResult
            },
            onScanned = {},
            onDisconnected = {},
            onPulse = {},
            handshakeInfo = { f.handshakeInfoSupplier() },
            distinctReadyPeerCount = { f.readyPeers },
            clock = { f.clockTime },
            jitterMs = { min, _ -> min },
            onDecision = { f.decisions.add(it) }
        )

        val newAdvMac = "11:22:33:44:55:66"
        val adv = BleAdvertisement(
            endpointId = newAdvMac,
            peerName = "Bob#B2",
            nodeId = "B2",
            electionScore = "700BB",
            directConnections = 0
        )

        controllerWithDisconnect.handle(adv)

        // Fresh, actively communicating socket must not be purged
        assertFalse(disconnected.contains(oldCentralMac))
        assertTrue(f.store.connectedEndpointNames.containsKey(oldCentralMac))
    }

    @Test
    fun agedSilentSocketPurgedAsZombieWhenDirectConnectionsZero() {
        val disconnected = mutableListOf<String>()
        val f = TestFixture(directLinks = 1, readyPeers = 0, localScoreStr = "900AA")
        val oldCentralMac = "AA:BB:CC:DD:EE:FF"
        f.store.connectedEndpointNames[oldCentralMac] = "Bob#B2"
        f.store.connectedEndpointIds.add(oldCentralMac)
        f.store.activeServerConnections[oldCentralMac] = createDummyDevice()
        // Connection established 20s ago, no inbound progress for 20s (silent)
        f.store.connectionEstablishTime[oldCentralMac] = f.clockTime - 20_000L
        f.store.connectionInteractionTimes[oldCentralMac] = f.clockTime - 20_000L

        val controllerWithDisconnect = BlePeerAdmissionController(
            store = f.store,
            scheduler = f.scheduler,
            localName = { f.localNameStr },
            isBlocked = { false },
            hasLinkToIdentity = { false },
            hasIndirectRoute = { false },
            hasPayloadReadyDirectLink = { false },
            hasReadyLinkToIdentity = { false },
            directLinkCount = { f.directLinks },
            maxDirectLinks = { f.maxLinks },
            electionScore = { f.localScoreStr },
            latestEndpointForIdentity = { _, fallback -> fallback },
            disconnectEndpoint = { disconnected.add(it) },
            connect = { endpoint, peer ->
                f.connectCalls.add(endpoint to peer)
                f.connectResult
            },
            onScanned = {},
            onDisconnected = {},
            onPulse = {},
            handshakeInfo = { f.handshakeInfoSupplier() },
            distinctReadyPeerCount = { f.readyPeers },
            clock = { f.clockTime },
            jitterMs = { min, _ -> min },
            onDecision = { f.decisions.add(it) }
        )

        val newAdvMac = "11:22:33:44:55:66"
        val rebootAd = BleAdvertisement(
            endpointId = newAdvMac,
            peerName = "Bob#B2",
            nodeId = "B2",
            electionScore = "700BB",
            directConnections = 0
        )

        controllerWithDisconnect.handle(rebootAd)

        // Stale, silent socket from a peer advertising 0 connections must be purged
        assertTrue(disconnected.contains(oldCentralMac))
        assertFalse(f.store.connectedEndpointNames.containsKey(oldCentralMac))
        assertFalse(f.store.connectedEndpointIds.contains(oldCentralMac))
    }
    @Test
    fun suspensionCancelsDelayedCandidatesEvenIfANewSessionIsActive() {
        val f = TestFixture()
        f.controller.handle(BleAdvertisement("11:22:33:44:55:66", "Bob#B2", "B2", "800BB", 0))
        f.controller.stop()
        f.clockTime += 1_000L
        f.scheduler.runAll()
        f.controller.recover()
        assertTrue(f.connectCalls.isEmpty())
    }

    @Test
    fun staleAdvertisementsCannotDriveEndlessConnectionAttempts() {
        val f = TestFixture()
        f.controller.handle(BleAdvertisement("11:22:33:44:55:66", "Bob#B2", "B2", "800BB", 0))
        f.clockTime += BlePeerAdmissionController.CANDIDATE_FRESH_MS + 1L
        f.scheduler.runAll()
        assertTrue(f.connectCalls.isEmpty())
    }

    @Test
    fun losingLastReadyPeerReconsidersRetainedRoutedAdvertisement() {
        val f = TestFixture(indirectRoute = true, payloadReadyDirect = true)
        f.controller.handle(BleAdvertisement("11:22:33:44:55:66", "Bob#B2", "B2", "800BB", 1))
        assertEquals(BleAdmissionReason.ROUTE_PRESERVED, f.decisions.last().reason)
        f.payloadReadyDirect = false
        f.controller.recover()
        f.clockTime += 500L
        f.scheduler.runAll()
        assertEquals(1, f.connectCalls.size)
    }

    @Test
    fun fullCapacityDoesNotBlindlyEvictHealthyConnections() {
        val f = TestFixture(directLinks = 3)
        val ad = BleAdvertisement("11:22:33:44:55:66", "Bob#B2", "B2", "800BB", 0)
        f.controller.handle(ad)
        f.clockTime += 6_000L
        f.controller.handle(ad)
        f.scheduler.runAll()
        assertTrue(f.connectCalls.isEmpty())
    }

    @Test
    fun capacityReclaimUsesRetirementOnlyForIdleRedundantLinkAndHonorsCooldown() {
        val f = TestFixture(directLinks = 3)
        val endpoint = "AA:BB:CC:DD:EE:FF"
        val link = f.store.links.begin(endpoint,
            com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole.CLIENT, "EE",
            java.util.concurrent.ConcurrentLinkedDeque(), java.util.concurrent.atomic.AtomicBoolean(false))
        val states = com.example.testresqmesh.core.network.bluetooth.state.BleLinkState.entries
        f.store.links.transition(link, states.first { it.name == "DISCOVERING" })
        f.store.links.transition(link, states.first { it.name == "CONFIGURING" })
        f.store.links.transition(link, states.first { it.name == "READY" })
        f.store.connectionInteractionTimes[endpoint] = f.clockTime
        f.redundant.add(endpoint)
        f.idle = false
        val ad = BleAdvertisement("11:22:33:44:55:66", "Bob#B2", "B2", "800BB", 0)
        f.controller.handle(ad)
        f.clockTime += 6_000L
        f.controller.handle(ad)
        assertTrue(f.retired.isEmpty())
        f.idle = true
        f.controller.handle(ad)
        assertEquals(listOf(endpoint), f.retired)
        f.scheduler.runAll()
        f.clockTime += 500L
        f.scheduler.runAll()
        assertTrue(f.connectCalls.isNotEmpty())
        f.directLinks = 3
        f.controller.handle(BleAdvertisement("22:22:33:44:55:66", "Carl#C3", "C3", "800CC", 0))
        f.clockTime += 6_000L
        f.controller.handle(BleAdvertisement("22:22:33:44:55:66", "Carl#C3", "C3", "800CC", 0))
        assertEquals(1, f.retired.size)
    }

    @Test
    fun equalScoresUseStableIdentityToElectExactlyOneClusterBridgeInitiator() {
        val higher = TestFixture(localNameStr = "Bob#B2", localScoreStr = "900AA", directLinks = 1, readyPeers = 1)
        val lower = TestFixture(localNameStr = "Alice#A1", localScoreStr = "900AA", directLinks = 1, readyPeers = 1)
        higher.controller.handle(BleAdvertisement("11:22:33:44:55:66", "Alice#A1", "A1", "900AA", 1))
        lower.controller.handle(BleAdvertisement("22:22:33:44:55:66", "Bob#B2", "B2", "900AA", 1))
        higher.clockTime += 500L
        lower.clockTime += 500L
        higher.scheduler.runAll()
        lower.scheduler.runAll()
        assertEquals(1, higher.connectCalls.size)
        assertTrue(lower.connectCalls.isEmpty())
        assertEquals("local", higher.decisions.last().electionWinner)
        assertEquals("peer", lower.decisions.last().electionWinner)
    }

}
