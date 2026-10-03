package com.example.testresqmesh.feature.setup.model


data class ConnectionUiState(
    val isOnline: Boolean = false,
    val connectionStatus: String = "Ready to deploy Mesh Node.",
    val myNodeName: String = "",
    val isRescanning: Boolean = false
)
