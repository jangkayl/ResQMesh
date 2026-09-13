package com.example.testresqmesh.core.network.bluetooth.state

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class BleStateStore {
    val connectedEndpointIds = mutableSetOf<String>()
    val connectedEndpointNames = mutableMapOf<String, String>()
    val seenMessageIds = java.util.LinkedHashSet<String>()
    
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
    
    val endpointLastSeen = mutableMapOf<String, Long>()
    val endpointFirstSeen = mutableMapOf<String, Long>()
    val endpointLastScore = mutableMapOf<String, String>()
    val connectionEstablishTime = mutableMapOf<String, Long>()
    val writeFailureCount = ConcurrentHashMap<String, Int>()

    var connectingMacAddress: String? = null
}
