package edu.cit.resqmesh.core.network.bluetooth.state

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothSocket
import edu.cit.resqmesh.core.network.dedup.ExpiringIdCache
import edu.cit.resqmesh.core.network.ratelimit.InboundGuards
import edu.cit.resqmesh.core.utils.collections.BoundedLruMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

class BleStateStore(val guards: InboundGuards = InboundGuards()) {
    fun retireServerOwnership(link: BleLink, releaseOwner: (String) -> Unit): Boolean {
        if (link.role != BleLinkRole.SERVER || !links.retire(link, releaseOwner)) return false
        activeServerConnections.remove(link.endpoint)
        gattFlights[link.endpoint]?.takeIf { it.link === link }?.let {
            if (gattFlights.remove(link.endpoint, it)) it.writing.set(false)
        }
        return true
    }
    val links = BleLinkRegistry()
    val connectedEndpointIds = CopyOnWriteArraySet<String>()
    val connectedEndpointNames = ConcurrentHashMap<String, String>()
    val seenMessageIds = ExpiringIdCache()
    /** Peer node ID to the reliable-transfer protocol version it advertised; LRU-bounded. */
    val peerTransferVersions = BoundedLruMap<String, Int>(MAX_PEER_TRANSFER_VERSIONS)
    
    val isNodeActive = AtomicBoolean(false)
    val isConnecting = AtomicBoolean(false)
    
    val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()
    val activeServerConnections = ConcurrentHashMap<String, BluetoothDevice>()
    val activeL2capSockets = ConcurrentHashMap<String, BluetoothSocket>()
    val pendingQueues = ConcurrentHashMap<String, ConcurrentLinkedDeque<GattTransfer>>()
    val gattFlights = ConcurrentHashMap<String, GattTransferFlight>()
    val serverIndications = ServerIndicationLedger()
    val isWriting = ConcurrentHashMap<String, AtomicBoolean>()
    val chunkBuffers = ConcurrentHashMap<String, ByteArray>()
    val connectionMtu = ConcurrentHashMap<String, Int>()
    val connectionInteractionTimes = ConcurrentHashMap<String, Long>()
    val l2capOutboundProgressTimes = ConcurrentHashMap<String, Long>()
    val blockedDevices = ConcurrentHashMap<String, Boolean>()
    val orphanDetectionTime = ConcurrentHashMap<String, Long>()
    
    val endpointLastSeen = ConcurrentHashMap<String, Long>()
    val endpointFirstSeen = BoundedLruMap<String, Long>(MAX_SCANNED_ENDPOINTS)
    val endpointLastScore = BoundedLruMap<String, String>(MAX_SCANNED_ENDPOINTS)
    /** MAC address -> stable node ID lifted from the peer's advertisement. */
    val endpointNodeIds = ConcurrentHashMap<String, String>()
    val connectionEstablishTime = ConcurrentHashMap<String, Long>()
    val writeFailureCount = ConcurrentHashMap<String, Int>()

    /**
     * Stable node IDs whose AUTO GATT transport connected but received no ATT discovery response.
     * Only those peers retry with explicit LE, preserving AUTO for known-good OEM combinations.
     */
    val explicitLeTransportPeers = ConcurrentHashMap.newKeySet<String>()

    var connectingMacAddress: String? = null

    /**
     * Timestamp when [isConnecting] was last acquired. Used by the watchdog as an absolute
     * backstop so a dropped GATT callback can never strand the lock permanently.
     */
    var connectLockAcquiredAt: Long = 0L

    private companion object {
        // Scan-fed tables see every advertising MAC in range, so they cannot grow without bound.
        const val MAX_SCANNED_ENDPOINTS = 256
        const val MAX_PEER_TRANSFER_VERSIONS = 512
    }
}
