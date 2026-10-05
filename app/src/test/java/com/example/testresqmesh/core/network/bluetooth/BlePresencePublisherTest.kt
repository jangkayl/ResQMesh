package com.example.testresqmesh.core.network.bluetooth

import android.bluetooth.BluetoothDevice
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.ReliableMeshTransfers
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.*
import org.junit.Test

class BlePresencePublisherTest {
    private val store = BleStateStore()
    private var now = 100_000L
    private var name = "Local#A001"
    private var nodeId = "A001"
    private var key = "test-key"
    private var ttl = 10
    private var peerUpdates = 0
    private val ready = mutableSetOf<String>()
    private val blocked = mutableSetOf<String>()
    private val packets = mutableListOf<MeshPayload>()

    private fun publisher() = BlePresencePublisher(
        store = store,
        updatePeerCount = { peerUpdates++ },
        nodeIdForEndpoint = { store.endpointNodeIds[it] },
        hasReadyEndpoint = { it in ready },
        isDeviceBlocked = { it in blocked },
        myDeviceName = { name },
        myNodeId = { nodeId },
        publicKey = { key },
        getMeshProfileTtl = { ttl },
        broadcastPriorityPayload = { packets += ProtoBuf.decodeFromByteArray<MeshPayload>(it) },
        clock = { now },
        newPulseId = { "system-${packets.size}" }
    )

    private fun peer(endpoint: String, id: String?, label: String, isReady: Boolean = true) {
        // Same allocation technique as existing admission tests; no Bluetooth operation executes.
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null) as sun.misc.Unsafe
        store.activeServerConnections[endpoint] = unsafe.allocateInstance(BluetoothDevice::class.java) as BluetoothDevice
        store.connectedEndpointNames[endpoint] = label
        if (id != null) store.endpointNodeIds[endpoint] = id
        if (isReady) ready += endpoint
    }

    @Test fun inactivePublisherDoesNotUpdateSessionOrDispatch() {
        publisher().publish(forceFull = true)
        assertEquals(0, peerUpdates)
        assertTrue(packets.isEmpty())
    }

    @Test fun rosterKeepsSortedIdentityNamePairsAndExcludesUnusablePeers() {
        store.isNodeActive.set(true)
        peer("b", "b002", "Beta#B002")
        peer("a", "a001", "Alpha#A001")
        peer("a-second", "a001", "Alpha#A001")
        peer("blocked", "C003", "Blocked#C003")
        peer("configuring", "D004", "Configuring#D004", isReady = false)
        peer("unknown", null, "Unknown Node")
        blocked += "Blocked#C003"
        publisher().publish()
        assertEquals(listOf("A001", "B002"), packets.single().connectedNodeIds)
        assertEquals(listOf("Alpha#A001", "Beta#B002"), packets.single().connectedNodes)
    }

    @Test fun unchangedRosterUsesPingUntilTheExactThirtySecondBoundary() {
        store.isNodeActive.set(true)
        val publisher = publisher()
        publisher.publish()
        now += 29_999
        publisher.publish()
        assertEquals("PING", packets.last().type)
        assertEquals("P1", packets.last().id)
        now++
        publisher.publish()
        assertEquals("SYSTEM", packets.last().type)
        assertEquals(100_002L, packets.last().topologySequence)
        publisher.publish(forceFull = true)
        assertEquals(100_003L, packets.last().topologySequence)
    }

    @Test fun transportResetForcesSnapshotWithoutResettingSequenceOrPingCounter() {
        store.isNodeActive.set(true)
        val publisher = publisher()
        publisher.publish()
        publisher.publish()
        publisher.resetTransport()
        publisher.publish()
        publisher.publish()
        assertEquals(listOf("SYSTEM", "PING", "SYSTEM", "PING"), packets.map { it.type })
        assertEquals(100_002L, packets[2].topologySequence)
        assertEquals("P2", packets.last().id)
    }

    @Test fun forcedSnapshotReadsCurrentIdentityKeyAndProfile() {
        store.isNodeActive.set(true)
        val publisher = publisher()
        publisher.publish()
        name = "Renamed#F006"
        nodeId = "F006"
        key = "updated-test-key"
        ttl = 4
        publisher.publish(forceFull = true)
        val packet = packets.last()
        assertEquals(name, packet.senderName)
        assertEquals(nodeId, packet.senderNodeId)
        assertEquals(key, packet.publicKey)
        assertEquals(4, packet.ttl)
        assertEquals(ReliableMeshTransfers.VERSION, packet.transferProtocol)
    }

    @Test fun losingAllNeighborsPublishesAnEmptyWithdrawalImmediately() {
        store.isNodeActive.set(true)
        peer("b", "B002", "Beta#B002")
        val publisher = publisher()
        publisher.publish()
        store.activeServerConnections.clear()
        publisher.publish()
        assertEquals("SYSTEM", packets.last().type)
        assertTrue(packets.last().connectedNodeIds.isEmpty())
        assertTrue(packets.last().connectedNodes.isEmpty())
    }
}
