package com.example.testresqmesh.core.model

data class ChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val imageBase64: String?,
    val audioBase64: String?,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val isMine: Boolean,
    val isPrivate: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val isHopped: Boolean = false,
    val receiveMedium: String = "Bluetooth 5.4",
    val deliveredTo: List<String> = emptyList(),
    val seenBy: List<String> = emptyList(),
    val outboundRoute: List<String> = emptyList(),
    val returnRoute: List<String> = emptyList(),
    val isSOS: Boolean = false
)

data class ConnectedDevice(
    val endpointId: String,
    val name: String,
    val isClassicConnected: Boolean = false,
    /**
     * True while a physical socket is established but the peer has not completed the name
     * handshake yet. The link is real and must be reflected in the UI, but the name is a
     * placeholder so it must not be published into the routing tables.
     */
    val isProvisional: Boolean = false
)

data class KnownNode(
    val name: String,
    val isDirect: Boolean,
    val lastSeen: Long
)

data class ScannedDevice(
    val endpointId: String,
    val name: String,
    val lastSeen: Long,
    /** Number of connections the peer advertised it currently holds. */
    val powerScore: Int = 0,
    /** The peer's advertised master-election score. */
    val myRole: String = "IDLE",
    val isConnecting: Boolean = false,
    /** Stable node ID lifted straight out of the advertisement. */
    val nodeId: String = ""
)

/**
 * A single BLE discovery / connection-state update emitted by the network layer.
 *
 * Replaces the previous six positional-argument lambda, which was easy to mis-order and gave no
 * indication that `powerScore` carried a connection count while `myRole` carried an election score.
 */
data class ScanEvent(
    val endpointId: String,
    val name: String,
    val nodeId: String = "",
    /** Null when this event only reports a connection-state change, not a fresh advertisement. */
    val peerConnections: Int? = null,
    /** Null when this event only reports a connection-state change, not a fresh advertisement. */
    val peerScore: String? = null,
    val isConnecting: Boolean = false
)