package com.example.testresqmesh.data.models

data class ChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val imageBase64: String?,
    val audioBase64: String?,
    val isMine: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ConnectedDevice(
    val endpointId: String,
    val name: String
)

data class ScannedDevice(
    val endpointId: String,
    val name: String,
    val lastSeen: Long,
    val powerScore: Int = 0,
    val myRole: String = "IDLE",
    val isConnecting: Boolean = false
)