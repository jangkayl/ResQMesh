package edu.cit.resqmesh.core.network.bluetooth.state

/**
 * Android server completions carry an address, not a connection or operation token.
 *
 * A ticket outlives its link as a tombstone so a late callback cannot advance a replacement flight on
 * the same address. Some stacks (seen on an OPPO CPH2127) never deliver the callback for an indication
 * lost in a disconnect, so a tombstone expires after [TOMBSTONE_TTL_MS]; otherwise it blocked that
 * address from reconnecting until server renewal, which healthy server peers defer.
 */
class ServerIndicationLedger(private val clock: () -> Long = System::currentTimeMillis) {
    data class Ticket(val endpoint: String, val generation: Long, val operation: Long, val owner: GattTransferFlight)
    private val pending = mutableMapOf<String, Ticket>()
    private val issuedAt = mutableMapOf<String, Long>()
    private var registration = 0L

    @Synchronized fun begin(flight: GattTransferFlight, operation: Long): Ticket? {
        expireStale()
        val endpoint = flight.link.endpoint
        if (pending.containsKey(endpoint)) return null
        issuedAt[endpoint] = clock()
        return Ticket(endpoint, flight.link.generation, operation, flight).also { pending[endpoint] = it }
    }
    @Synchronized fun rejected(ticket: Ticket) { if (pending[ticket.endpoint] === ticket) remove(ticket.endpoint) }
    /** Capture ownership at callback ingress, before posting onto the main handler. */
    @Synchronized fun take(endpoint: String, capturedRegistration: Long): Ticket? =
        if (capturedRegistration == registration) remove(endpoint) else null
    @Synchronized fun unresolved(endpoint: String): Boolean { expireStale(); return pending.containsKey(endpoint) }
    @Synchronized fun hasUnresolved(): Boolean { expireStale(); return pending.isNotEmpty() }
    /** Clear when closing/replacing the server registration; retirement keeps tombstones until they expire. */
    @Synchronized fun reset(newRegistration: Long) { registration = newRegistration; pending.clear(); issuedAt.clear() }

    private fun remove(endpoint: String): Ticket? {
        issuedAt.remove(endpoint)
        return pending.remove(endpoint)
    }

    private fun expireStale() {
        val now = clock()
        issuedAt.filterValues { now - it >= TOMBSTONE_TTL_MS }.keys.forEach(::remove)
    }

    companion object {
        /** Longer than the 4 s GATT chunk timeout, so a live indication always fails its flight first. */
        const val TOMBSTONE_TTL_MS = 10_000L
    }
}
