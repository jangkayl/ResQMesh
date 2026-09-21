package com.example.testresqmesh.core.network.dispatch

import com.example.testresqmesh.core.network.BlockControlEnvelope
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.PayloadDispatcherCallback
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class HybridPrivateRoutingTest {

    private class TestDispatcherCallback(
        val myName: String = "Relay [TAG]#B001",
        val myId: String = "B001"
    ) : PayloadDispatcherCallback {
        val directPayloads = mutableListOf<Pair<String, ByteArray>>()
        val broadcastPayloads = mutableListOf<Pair<ByteArray, String?>>()
        val endpointMap = mutableMapOf<String, String>()

        override fun getMyDeviceName() = myName
        override fun getMyNodeId() = myId
        override fun getSeenMessageIds(): MutableSet<String> = mutableSetOf()
        override fun getEndpointMedium(endpointId: String) = "BLE"
        override fun getConnectedEndpointIdByName(name: String) = endpointMap[name]
        override fun getConnectedEndpointIdByNodeId(nodeId: String) = endpointMap[nodeId]
        override fun getStpNeighbors(): Set<String> = emptySet()
        override fun sendDirectPayload(endpointId: String, payload: ByteArray) {
            directPayloads.add(endpointId to payload)
        }
        override fun sendPriorityPayload(endpointId: String, payload: ByteArray) {}
        override fun sendGattPayload(endpointId: String, payload: ByteArray) {}
        override fun onHeartbeatAck(endpointId: String, challengeId: String) {}
        override fun broadcastPayload(payload: ByteArray, excludeEndpointId: String?) {
            broadcastPayloads.add(payload to excludeEndpointId)
        }
        override fun onMessageSeen(msgId: String, readerName: String) {}
        override fun onMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>) {}
        override fun onPublicKeyReceived(senderName: String, senderNodeId: String, key: String) {}
        override fun onRoutingTableReceived(senderName: String, senderNodeId: String, connectedNodes: List<String>, connectedNodeIds: List<String>) {}
        override fun onMessageReceived(endpointId: String, msgId: String, senderName: String, text: String, isPrivate: Boolean, isSystem: Boolean, imageBase64: String?, audioBase64: String?, locationLat: Double?, locationLng: Double?, medium: String, routePath: List<String>, channelId: String) {}
        override fun onLiveAudioChunk(sender: String, channelId: String, chunk: ByteArray) {}
        override fun onDeviceNameSync(endpointId: String, realName: String) {}
        override fun onDeviceGoodbye(endpointId: String) {}
        override fun onSosCancelled() {}
        override fun onBlockRequest(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope) {}
        override fun onBlockAck(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope) {}
        override fun onLegacyBlockControl(payloadType: String, senderName: String) {}
        override fun showNotification(sender: String, text: String) {}
        override fun showSosEmergencyNotification(sender: String, text: String) {}
    }

    @Test
    fun standardMessageHandler_happyPath_forwardsDirectlyWhenNextHopConnected() {
        val callback = TestDispatcherCallback(myName = "Relay#B001", myId = "B001")
        callback.endpointMap["C001"] = "ep-charlie"

        val payload = MeshPayload(
            id = "msg-1",
            type = "MESSAGE",
            isPrivate = true,
            isEncrypted = true,
            senderName = "Alice#A001",
            targetName = "Charlie#C001",
            targetNodeId = "C001",
            directedRouteNodeIds = listOf("A001", "B001", "C001"),
            relayHopCount = 0
        )
        val payloadBytes = ProtoBuf.encodeToByteArray(MeshPayload.serializer(), payload)

        val handler = StandardMessageHandler()
        handler.handle("ep-alice", payload, payloadBytes, callback)

        assertEquals(1, callback.directPayloads.size)
        assertEquals("ep-charlie", callback.directPayloads[0].first)
        assertTrue(callback.broadcastPayloads.isEmpty())
    }

    @Test
    fun standardMessageHandler_fallbackPath_broadcastsWhenNextHopDisconnected() {
        val callback = TestDispatcherCallback(myName = "Relay#B001", myId = "B001")
        // No connection for C001 in endpointMap

        val payload = MeshPayload(
            id = "msg-2",
            type = "MESSAGE",
            isPrivate = true,
            isEncrypted = true,
            senderName = "Alice#A001",
            targetName = "Charlie#C001",
            targetNodeId = "C001",
            directedRouteNodeIds = listOf("A001", "B001", "C001"),
            relayHopCount = 0
        )
        val payloadBytes = ProtoBuf.encodeToByteArray(MeshPayload.serializer(), payload)

        val handler = StandardMessageHandler()
        handler.handle("ep-alice", payload, payloadBytes, callback)

        assertTrue(callback.directPayloads.isEmpty())
        assertEquals(1, callback.broadcastPayloads.size)
        assertEquals("ep-alice", callback.broadcastPayloads[0].second)

        val broadcastedPayload = ProtoBuf.decodeFromByteArray(MeshPayload.serializer(), callback.broadcastPayloads[0].first)
        assertEquals(1, broadcastedPayload.relayHopCount)
    }

    @Test
    fun standardMessageHandler_maxHopsExceeded_dropsWithoutBroadcasting() {
        val callback = TestDispatcherCallback(myName = "Relay#B001", myId = "B001")
        // No connection for C001

        val payload = MeshPayload(
            id = "msg-3",
            type = "MESSAGE",
            isPrivate = true,
            isEncrypted = true,
            senderName = "Alice#A001",
            targetName = "Charlie#C001",
            targetNodeId = "C001",
            directedRouteNodeIds = listOf("A001", "B001", "C001"),
            relayHopCount = 3 // MAX_PRIVATE_RELAY_HOPS = 3
        )
        val payloadBytes = ProtoBuf.encodeToByteArray(MeshPayload.serializer(), payload)

        val handler = StandardMessageHandler()
        handler.handle("ep-alice", payload, payloadBytes, callback)

        assertTrue(callback.directPayloads.isEmpty())
        assertTrue(callback.broadcastPayloads.isEmpty())
    }

    @Test
    fun receiptHandler_fallbackPath_broadcastsWhenNextHopDisconnected() {
        val callback = TestDispatcherCallback(myName = "Relay#B001", myId = "B001")
        // Charlie sends receipt to Alice via Relay, but Alice endpoint is disconnected
        val receiptPayload = MeshPayload(
            id = "rcpt-1",
            type = "DELIVERED",
            isPrivate = true,
            targetMessageId = "msg-target-1",
            reader = "Charlie#C001",
            directedRouteNodeIds = listOf("C001", "B001", "A001"),
            relayHopCount = 0
        )
        val payloadBytes = ProtoBuf.encodeToByteArray(MeshPayload.serializer(), receiptPayload)

        val handler = ReceiptHandler()
        handler.handle("ep-charlie", receiptPayload, payloadBytes, callback)

        assertTrue(callback.directPayloads.isEmpty())
        assertEquals(1, callback.broadcastPayloads.size)
        assertEquals("ep-charlie", callback.broadcastPayloads[0].second)

        val broadcastedReceipt = ProtoBuf.decodeFromByteArray(MeshPayload.serializer(), callback.broadcastPayloads[0].first)
        assertEquals(1, broadcastedReceipt.relayHopCount)
    }
}
