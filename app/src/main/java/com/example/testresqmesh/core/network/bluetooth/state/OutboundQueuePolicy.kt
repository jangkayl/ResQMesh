package com.example.testresqmesh.core.network.bluetooth.state

/** Shared per-link pressure limits, including the active transfer. Control keeps reserved headroom. */
object OutboundQueuePolicy {
    const val RESERVED_CONTROL_TRANSFERS = 8
    const val MAX_ORDINARY_BYTES = 2 * 1024 * 1024
    const val RESERVED_CONTROL_BYTES = 64 * 1024

    fun fitsSingleTransfer(bytes: Int, priority: Boolean): Boolean =
        bytes > 0 && bytes <= MAX_ORDINARY_BYTES + if (priority) RESERVED_CONTROL_BYTES else 0

    fun canAccept(count: Int, bytes: Long, addedBytes: Int, priority: Boolean): Boolean {
        val countLimit = MeshFrameCodec.MAX_PENDING_TRANSFERS - if (priority) 0 else RESERVED_CONTROL_TRANSFERS
        val byteLimit = MAX_ORDINARY_BYTES + if (priority) RESERVED_CONTROL_BYTES else 0
        return addedBytes > 0 && count < countLimit && bytes + addedBytes <= byteLimit
    }
}
