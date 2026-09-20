package com.example.testresqmesh.core.network.bluetooth

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
            onDecision = { decisions.add(it) }
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
    fun twoDirectLinksPeerAdvertisesOneExplicitTwoLinkPolicyDenialIsRecorded() {
        val f = TestFixture(directLinks = 2, localScoreStr = "900AA")
        val ad = BleAdvertisement(
            endpointId = "11:22:33:44:55:66",
            peerName = "Dave#D4",
            nodeId = "D4",
            electionScore = "800DD",
            directConnections = 1
        )

        f.controller.handle(ad)

        assertEquals(1, f.decisions.size)
        val decision = f.decisions[0]
        assertEquals(BleAdmissionReason.TWO_LINK_PEER_CONNECTED, decision.reason)
        assertEquals(BleConnectStartResult.REJECTED, decision.result)
        assertEquals(1, decision.peerAdvertisedConnections)
        assertTrue(f.connectCalls.isEmpty())
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
}
