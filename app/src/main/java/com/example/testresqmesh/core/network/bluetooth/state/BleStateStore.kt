package com.example.testresqmesh.core.network.bluetooth.state

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

class BleStateStore {
    val connectedEndpointIds = CopyOnWriteArraySet<String>()
    val connectedEndpointNames = ConcurrentHashMap<String, String>()
    val seenMessageIds = CopyOnWriteArraySet<String>()
    
    val isNodeActive = AtomicBoolean(false)
    val isConnecting = AtomicBoolean(false)
    
    val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()
    val activeServerConnections = ConcurrentHashMap<String, BluetoothDevice>()
    val activeL2capSockets = ConcurrentHashMap<String, BluetoothSocket>()
    val pendingQueues = ConcurrentHashMap<String, ConcurrentLinkedQueue<ByteArray>>()
    val isWriting = ConcurrentHashMap<String, AtomicBoolean>()
    val chunkBuffers = ConcurrentHashMap<String, ByteArray>()
    val connectionMtu = ConcurrentHashMap<String, Int>()
    val connectionAttempts = ConcurrentHashMap<String, Long>()
    val connectionInteractionTimes = ConcurrentHashMap<String, Long>()
    val blockedDevices = ConcurrentHashMap<String, Boolean>()
    val orphanDetectionTime = ConcurrentHashMap<String, Long>()
    
    val endpointLastSeen = ConcurrentHashMap<String, Long>()
    val endpointFirstSeen = ConcurrentHashMap<String, Long>()
    val endpointLastScore = ConcurrentHashMap<String, String>()
    /** MAC address -> stable node ID lifted from the peer's advertisement. */
    val endpointNodeIds = ConcurrentHashMap<String, String>()
    val connectionEstablishTime = ConcurrentHashMap<String, Long>()
    val writeFailureCount = ConcurrentHashMap<String, Int>()

    var connectingMacAddress: String? = null
}
