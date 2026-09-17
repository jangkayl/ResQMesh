package com.example.testresqmesh.core.network.bluetooth.state

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt

/**
 * Owns the transport-independent state transitions for one queued GATT transfer.
 * Android I/O stays in NativeBleManager; this class decides which flight and callback may advance.
 */
class GattTransferCoordinator(private val store: BleStateStore) {
    enum class Completion { STALE, MORE, DONE }

    fun enqueue(endpoint: String, transfer: GattTransfer, priority: Boolean): Boolean {
        val queue = store.pendingQueues.computeIfAbsent(endpoint) { java.util.concurrent.ConcurrentLinkedDeque() }
        synchronized(queue) {
            if (queue.size >= MeshFrameCodec.MAX_PENDING_TRANSFERS) return false
            if (priority) queue.addFirst(transfer) else queue.addLast(transfer)
        }
        store.isWriting.putIfAbsent(endpoint, java.util.concurrent.atomic.AtomicBoolean(false))
        return true
    }

    fun claimNext(
        endpoint: String,
        link: BleLink,
        gatt: BluetoothGatt?,
        serverDevice: BluetoothDevice?
    ): GattTransferFlight? {
        val queue = store.pendingQueues[endpoint] ?: return null
        val writing = store.isWriting[endpoint] ?: return null
        store.gattFlights[endpoint]?.takeIf { !store.links.isCurrent(it.link) }?.let { stale ->
            if (store.gattFlights.remove(endpoint, stale)) stale.writing.set(false)
        }
        if (!writing.compareAndSet(false, true)) return null
        val transfer = queue.pollFirst()
        if (transfer == null) {
            writing.set(false)
            return null
        }
        return GattTransferFlight(transfer, link, queue, writing, gatt, serverDevice).also {
            store.gattFlights[endpoint] = it
        }
    }

    fun owns(flight: GattTransferFlight): Boolean {
        val endpoint = flight.link.endpoint
        return store.links.isCurrent(flight.link) &&
            flight.link.state == BleLinkState.READY &&
            store.pendingQueues[endpoint] === flight.queue &&
            store.gattFlights[endpoint] === flight
    }

    fun beginChunk(flight: GattTransferFlight, chunkLength: Int): Long {
        flight.chunkLength = chunkLength
        return ++flight.operationId
    }

    fun recordInitiationRejected(flight: GattTransferFlight, maxAttempts: Int): Boolean =
        ++flight.retryCount >= maxAttempts

    fun callbackMatches(
        flight: GattTransferFlight,
        role: BleLinkRole,
        gatt: BluetoothGatt?,
        device: BluetoothDevice?
    ): Boolean =
        flight.link.role == role && store.links.isCurrent(flight.link) &&
            (role != BleLinkRole.CLIENT || flight.gatt === gatt) &&
            (role != BleLinkRole.SERVER || flight.serverDevice === device)

    fun completeChunk(flight: GattTransferFlight): Completion {
        val endpoint = flight.link.endpoint
        if (store.gattFlights[endpoint] !== flight) return Completion.STALE
        flight.offset += flight.chunkLength
        flight.chunkLength = 0
        flight.retryCount = 0
        if (flight.offset < flight.transfer.frame.size) return Completion.MORE
        if (!store.gattFlights.remove(endpoint, flight)) return Completion.STALE
        flight.writing.set(false)
        return Completion.DONE
    }

    fun remove(flight: GattTransferFlight): Boolean {
        if (!store.gattFlights.remove(flight.link.endpoint, flight)) return false
        flight.writing.set(false)
        return true
    }

    fun drainForPromotion(endpoint: String): List<GattTransfer> {
        val promoted = mutableListOf<GattTransfer>()
        store.gattFlights.remove(endpoint)?.let { flight ->
            flight.writing.set(false)
            promoted += flight.transfer
        }
        store.pendingQueues[endpoint]?.let { queue ->
            while (true) promoted += queue.pollFirst() ?: break
        }
        store.isWriting[endpoint]?.set(false)
        return promoted
    }
}
