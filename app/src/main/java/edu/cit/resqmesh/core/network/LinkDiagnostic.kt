package edu.cit.resqmesh.core.network

/** Read-only copy of one live BLE link for the developer console. It holds no Android Bluetooth classes. */
data class LinkDiagnostic(
    val endpoint: String,
    val role: String,
    val generation: Long,
    val state: String,
    val nodeId: String?,
    val name: String?,
    val identityAdmitted: Boolean,
    val transport: String,
    val readyAt: Long
)
