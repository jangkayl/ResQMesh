package com.example.testresqmesh.feature.radar.ui

/** Explicit classification of a Radar row, so grouping and actions never depend on status text. */
enum class NodeKind {
    DIRECT,
    UNRESPONSIVE,
    HANDSHAKING,
    RELAY,
    HOPPED,
    DISCOVERED,
    SYNCING,
    OFFLINE,
    BLOCKED_OFFLINE
}

data class NodeItemData(
    val endpointId: String,
    /** Fully qualified node name. Always used for block/unblock/connect actions. */
    val name: String,
    val status: String,
    val kind: NodeKind = NodeKind.DISCOVERED,
    val isConnected: Boolean = false,
    val isActiveRelay: Boolean = false,
    val isBlocked: Boolean = false,
    /** Human friendly text for display only. Falls back to [name] when empty. */
    val label: String = "",
    /** Current repository-verified path. Empty for nearby/offline peers. */
    val route: List<String> = emptyList()
)
