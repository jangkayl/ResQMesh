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
    val returnRoute: List<String> = emptyList()
)

data class ConnectedDevice(
    val endpointId: String,
    val name: String
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
    val powerScore: Int = 0,
    val myRole: String = "IDLE",
    val isConnecting: Boolean = false
)