package com.example.testresqmesh.core.network.bluetooth.state

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

enum class BleLinkRole { CLIENT, SERVER }

enum class BleLinkState {
    DISCONNECTED, CONNECTING, DISCOVERING, CONFIGURING, READY, DISCONNECTING
}

/** One physical GATT attempt. Endpoint addresses and stable peer IDs are deliberately separate. */
class BleLink(
    val generation: Long,
    val endpoint: String,
    val role: BleLinkRole,
    @Volatile var peerNodeId: String?,
    val outboundQueue: ConcurrentLinkedDeque<GattTransfer>,
    val writeInFlight: AtomicBoolean,
    val startedAt: Long
) {
    @Volatile var gatt: BluetoothGatt? = null
    @Volatile var serverDevice: BluetoothDevice? = null
    @Volatile var state: BleLinkState = BleLinkState.CONNECTING
        internal set
    @Volatile var currentOperation: String? = null
    @Volatile var mtu: Int = 20
    @Volatile var lastInteractionAt: Long = startedAt
    @Volatile var readyAt: Long = 0L
    @Volatile var retryCount: Int = 0
}

/** Sole owner of link lifecycle transitions. Later phases migrate the remaining endpoint maps. */
class BleLinkRegistry {
    private val links = mutableMapOf<Pair<String, BleLinkRole>, BleLink>()
    private var nextGeneration = 0L

    @Synchronized
    fun begin(
        endpoint: String,
        role: BleLinkRole,
        peerNodeId: String?,
        queue: ConcurrentLinkedDeque<GattTransfer>,
        writing: AtomicBoolean,
        now: Long = System.currentTimeMillis()
    ): BleLink {
        val link = BleLink(++nextGeneration, endpoint, role, peerNodeId, queue, writing, now)
        links[endpoint to role] = link
        return link
    }

    @Synchronized fun isCurrent(link: BleLink): Boolean = links[link.endpoint to link.role] === link

    @Synchronized fun current(endpoint: String, role: BleLinkRole): BleLink? = links[endpoint to role]

    /** A redundant unfinished role must not cancel a ready path to the same peer. */
    @Synchronized fun hasReadyPeerExcept(link: BleLink): Boolean = links.values.any { other ->
        other !== link && other.state == BleLinkState.READY &&
            (other.endpoint == link.endpoint ||
                (!link.peerNodeId.isNullOrBlank() && link.peerNodeId == other.peerNodeId))
    }

    /** Complete a generation-bound setup deadline only for the attempt still configuring. */
    @Synchronized fun expireConfiguring(link: BleLink): Boolean =
        if (isCurrent(link) && link.state == BleLinkState.CONFIGURING) {
            transition(link, BleLinkState.DISCONNECTING)
        } else false

    @Synchronized
    fun transition(link: BleLink, next: BleLinkState, now: Long = System.currentTimeMillis()): Boolean {
        if (!isCurrent(link) || next !in allowed(link.state)) return false
        link.state = next
        if (next == BleLinkState.READY) link.readyAt = now
        return true
    }

    @Synchronized
    fun isReady(endpoint: String): Boolean =
        links[endpoint to BleLinkRole.CLIENT]?.state == BleLinkState.READY ||
            links[endpoint to BleLinkRole.SERVER]?.state == BleLinkState.READY

    @Synchronized
    fun forget(link: BleLink): Boolean {
        if (!isCurrent(link)) return false
        link.state = BleLinkState.DISCONNECTED
        links.remove(link.endpoint to link.role)
        return true
    }

    @Synchronized
    fun clear() {
        links.values.forEach { it.state = BleLinkState.DISCONNECTED }
        links.clear()
    }

    private fun allowed(state: BleLinkState): Set<BleLinkState> = when (state) {
        BleLinkState.DISCONNECTED -> setOf(BleLinkState.CONNECTING)
        BleLinkState.CONNECTING -> setOf(BleLinkState.DISCOVERING, BleLinkState.CONFIGURING, BleLinkState.DISCONNECTING)
        BleLinkState.DISCOVERING -> setOf(BleLinkState.CONFIGURING, BleLinkState.DISCONNECTING)
        BleLinkState.CONFIGURING -> setOf(BleLinkState.READY, BleLinkState.DISCONNECTING)
        BleLinkState.READY -> setOf(BleLinkState.DISCONNECTING)
        BleLinkState.DISCONNECTING -> setOf(BleLinkState.DISCONNECTED)
    }
}
