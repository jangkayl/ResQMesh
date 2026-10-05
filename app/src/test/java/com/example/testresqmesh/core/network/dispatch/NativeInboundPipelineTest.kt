package com.example.testresqmesh.core.network.dispatch

import android.os.Handler
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.ReliableMeshTransfers
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class NativeInboundPipelineTest {
    private class Fixture {
        val store = BleStateStore()
        val versions = mutableMapOf<String, Int>()
        val calls = mutableListOf<String>()
        var current = true
        var live = true
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
            hasReadyEndpoint = { true },
            isDeviceBlocked = { false },
            transfers = { error("The transfer journal must stay lazy in this scenario") },
            removeHeartbeat = { calls += "heartbeat-removed" },
            disconnectFromEndpoint = { calls += "disconnect" },
            disconnectDirectIdentity = { _, _ -> calls += "identity-disconnect" },
            sendSystemPulse = { calls += "pulse" },
            connectedListener = { { calls += "connected" } },
            livenessListener = { liveness },
            dispatch = { _, _ -> calls += "dispatch" }
        )

        fun receive(payload: MeshPayload) = pipeline.processBinaryPayload("endpoint", ProtoBuf.encodeToByteArray(payload))
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
        assertTrue(f.versions.isEmpty())
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
