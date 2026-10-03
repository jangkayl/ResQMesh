package com.example.testresqmesh.core.network.bluetooth.state

/** Android server completions carry an address, not a connection or operation token. */
class ServerIndicationLedger {
    data class Ticket(val endpoint: String, val generation: Long, val operation: Long, val owner: GattTransferFlight)
    private val pending = mutableMapOf<String, Ticket>()
    private var registration = 0L

    @Synchronized fun begin(flight: GattTransferFlight, operation: Long): Ticket? {
        val endpoint = flight.link.endpoint
        if (pending.containsKey(endpoint)) return null
        return Ticket(endpoint, flight.link.generation, operation, flight).also { pending[endpoint] = it }
    }
    @Synchronized fun rejected(ticket: Ticket) { if (pending[ticket.endpoint] === ticket) pending.remove(ticket.endpoint) }
    /** Capture ownership at callback ingress, before posting onto the main handler. */
    @Synchronized fun take(endpoint: String, capturedRegistration: Long): Ticket? =
        if (capturedRegistration == registration) pending.remove(endpoint) else null
    @Synchronized fun unresolved(endpoint: String): Boolean = pending.containsKey(endpoint)
    @Synchronized fun hasUnresolved(): Boolean = pending.isNotEmpty()
    /** Clear only when closing/replacing the server registration; retirement keeps tombstones. */
    @Synchronized fun reset(newRegistration: Long) { registration = newRegistration; pending.clear() }
}
