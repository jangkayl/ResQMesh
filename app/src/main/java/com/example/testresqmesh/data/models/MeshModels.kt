package com.example.testresqmesh.data.models

data class ChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val imageBase64: String?,
    val audioBase64: String?,
    val isMine: Boolean
)

data class ConnectedDevice(
    val endpointId: String,
    val name: String
)