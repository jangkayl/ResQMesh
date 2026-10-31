package edu.cit.resqmesh.core.network.dispatch

import android.os.Handler
import edu.cit.resqmesh.core.network.DetachedSender
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.ReliableMeshTransfers
import edu.cit.resqmesh.core.network.bluetooth.state.BleStateStore
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class NativeInboundPipelineTest {
    private class Fixture {
        val store = BleStateStore()
        val versions = edu.cit.resqmesh.core.utils.collections.BoundedLruMap<String, Int>(16)
        val calls = mutableListOf<String>()
        val dispatched = mutableListOf<String>()
        var current = true
        var live = true
        var livePeerEndpoint: String? = null
        var liveness: ((String, Boolean) -> Unit)? = { _, _ -> calls += "old-listener" }
        val pipeline = NativeInboundPipeline(
            store = store,
            handler = Handler(),
            peerTransferVersions = versions,
            generation = { 7L },
            nodeIdentity = { "A001" },
            isTransportGenerationCurrent = { current },
            hasLiveSocket = { live },
            nodeIdForEndpoint = { "B002" },
            endpointForPeer = { livePeerEndpoint },
            hasReadyEndpoint = { true },
            isDeviceBlocked = { false },
            transfers = { error("The transfer journal must stay lazy in this scenario") },
            removeHeartbeat = { calls += "heartbeat-removed" },
            disconnectFromEndpoint = { calls += "disconnect" },
            disconnectDirectIdentity = { _, _ -> calls += "identity-disconnect" },
            sendSystemPulse = { calls += "pulse" },
            connectedListener = { { calls += "connected" } },
            livenessListener = { liveness },
            dispatch = { endpoint, _ -> calls += "dispatch"; dispatched += endpoint }
        )

        fun receive(payload: MeshPayload) = pipeline.processBinaryPayload("endpoint", ProtoBuf.encodeToByteArray(payload))
    }

    @Test fun completedTransferDispatchesWithoutItsSenderLink() {
        val f = Fixture()
        f.store.isNodeActive.set(true)
        f.pipeline.processCompletedTransfer("B002", ProtoBuf.encodeToByteArray(MeshPayload(id = "image")))
        assertEquals(listOf(DetachedSender.endpointOf("B002")), f.dispatched)
        assertEquals("B002", DetachedSender.peerOf(f.dispatched.single()))
    }

    @Test fun completedTransferPrefersTheLiveLink() {
        val f = Fixture()
        f.store.isNodeActive.set(true)
        f.livePeerEndpoint = "endpoint"
        val bytes = ProtoBuf.encodeToByteArray(MeshPayload(id = "image", type = "CONVERSATION"))
        f.pipeline.processCompletedTransfer("B002", bytes)
        assertEquals(listOf("endpoint"), f.dispatched)
    }

    @Test fun completedTransferFromBlockedPeerOrStoppedNodeIsRefused() {
        val f = Fixture()
        val bytes = ProtoBuf.encodeToByteArray(MeshPayload(id = "image"))
        f.store.isNodeActive.set(false)
        f.pipeline.processCompletedTransfer("B002", bytes)
        f.store.isNodeActive.set(true)
        f.store.blockedDevices["B002"] = true
        f.pipeline.processCompletedTransfer("B002", bytes)
        assertTrue(f.dispatched.isEmpty())
    }

    @Test fun frameFloodOnOneLinkIsDroppedAfterTheFramesPerSecondBudget() {
        val f = Fixture()
        repeat(100) { f.receive(MeshPayload(id = "p$it", type = "PING")) }
        assertTrue(f.calls.count { it == "dispatch" } in 64..66)
    }

    @Test fun obsoleteTransportCannotDispatchOrTouchTheJournal() {
        val f = Fixture()
        f.current = false
        f.receive(MeshPayload(id = "synthetic", type = "PING"))
        assertTrue(f.calls.isEmpty())
    }

    @Test fun unownedSocketCannotPublishLivenessOrDispatch() {
        val f = Fixture()
        f.live = false
        f.receive(MeshPayload(id = "synthetic", type = "PING"))
        assertTrue(f.calls.isEmpty())
    }

    @Test fun currentListenerRunsBeforeHeartbeatRemovalAndDispatch() {
        val f = Fixture()
        f.liveness = { _, responsive ->
            assertTrue(responsive)
            f.calls += "current-listener"
        }
        f.receive(MeshPayload(id = "synthetic", type = "PING"))
        assertEquals(listOf("current-listener", "heartbeat-removed", "dispatch"), f.calls)
    }

    @Test fun relayedIdentityPulseCannotRenameItsPhysicalForwarder() {
        val f = Fixture()
        f.store.connectedEndpointNames["endpoint"] = "Forwarder#B002"
        f.receive(MeshPayload(id = "synthetic", type = "SYSTEM", senderName = "Remote#C003",
            senderNodeId = "C003", routePath = listOf("Forwarder#B002"), transferProtocol = ReliableMeshTransfers.VERSION))
        assertEquals("Forwarder#B002", f.store.connectedEndpointNames["endpoint"])
        assertTrue(f.versions.size == 0)
        assertEquals(listOf("old-listener", "heartbeat-removed", "dispatch"), f.calls)
    }

    @Test fun transferEnvelopeRequiresAdmittedIdentityBeforeJournalAccess() {
        val f = Fixture()
        f.receive(MeshPayload(id = "synthetic", type = ReliableMeshTransfers.PIECE))
        assertTrue(f.calls.isEmpty())
    }

    @Test fun malformedPayloadRetainsExistingDispatcherHandoff() {
        val f = Fixture()
        f.pipeline.processBinaryPayload("endpoint", byteArrayOf(0x80.toByte()))
        assertEquals(listOf("dispatch"), f.calls)
    }
}
