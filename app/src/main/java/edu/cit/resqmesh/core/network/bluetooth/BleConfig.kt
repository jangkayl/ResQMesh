package edu.cit.resqmesh.core.network.bluetooth

import java.util.UUID

/**
 * Fixed BLE identifiers and timing used by the GATT client, server and link supervision.
 * The UUIDs are the over-the-air ResQMesh service contract and must never change.
 */
object BleConfig {
    /** ResQMesh custom service. */
    val SERVICE_UUID: UUID = UUID.fromString("B9A34F5C-7462-4C61-8935-7C2D4A15A3E4")
    val RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6A81C2E5-309F-4D88-B270-4A9A65D8B6C7")
    val TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("1E4D9C7B-6F2A-4B9E-981D-F8A32C5B4E10")

    /** Exposes the dynamic L2CAP port. */
    val L2CAP_PSM_CHARACTERISTIC_UUID: UUID = UUID.fromString("8C91321D-4A22-4215-99A1-3E2A15C81F4B")

    /** Standard Client Characteristic Configuration descriptor (required for notifications). */
    val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    /**
     * Direct radio links per phone. Larger meshes expand through routed neighbors.
     * MUST BE 3! If set to 1, it causes an infinite eviction loop.
     */
    const val MAX_TOTAL_CONNECTIONS = 3

    /**
     * How long an inbound server link may stay nameless before it is kicked. Raised from 5s because
     * a peer whose first inbound payload was a relayed message (non-empty routePath, so auto-rename
     * is intentionally skipped) could be dropped despite having a perfectly healthy socket.
     */
    const val NAME_HANDSHAKE_TIMEOUT_MS = 10_000L

    /**
     * Absolute ceiling on the post-connect handshake (discovery and descriptor write). Always
     * fires, so a dropped OEM callback can never strand the connect lock.
     */
    const val HANDSHAKE_WATCHDOG_MS = 5_000L

    /** Bounded retry for a synchronous `discoverServices()` rejection from a busy OEM stack. */
    const val DISCOVERY_REQUEST_RETRY_MS = 300L
    const val MAX_DISCOVERY_REQUEST_ATTEMPTS = 3

    /** Hard ceiling on holding the connect lock, enforced by [session.BleLifecycleSupervisor]. */
    const val CONNECT_LOCK_MAX_HOLD_MS = 25_000L

    /**
     * Window in which two links to the same peer are treated as a simultaneous-connect collision
     * rather than a redundant duplicate.
     */
    const val DUPLICATE_LINK_GRACE_MS = 5_000L

    const val HEARTBEAT_ACK_TIMEOUT_MS = 8_000L

    /**
     * Consecutive unanswered heartbeat challenges before a silent link is retired. One miss used to
     * retire the link, which dropped edge-of-range links during brief blackouts. Each challenge
     * waits [HEARTBEAT_ACK_TIMEOUT_MS], so three misses cost about 24 s after silence is noticed.
     */
    const val HEARTBEAT_MAX_MISSES = 3
    const val GATT_CHUNK_TIMEOUT_MS = 4_000L

    /** Payload bytes per write on the default 23-byte ATT MTU, used until a larger MTU is negotiated. */
    const val DEFAULT_ATT_PAYLOAD_BYTES = 20

    /** MTU asked for once the link is READY and the setup queue is idle. */
    const val REQUESTED_MTU = 247
}
