package com.example.testresqmesh.core.network.bluetooth

enum class MeshTransportState(val status: String) {
    OFFLINE("Mesh is offline"),
    BLUETOOTH_OFF("Bluetooth is off — reconnection will resume when enabled"),
    PERMISSION_REQUIRED("Mesh needs permission — allow Nearby Devices and Location access"),
    STARTING("Starting Bluetooth mesh…"),
    SEARCHING("Searching for nearby devices"),
    CONNECTED("Connected to nearby mesh devices"),
    ERROR("Bluetooth mesh needs attention — retrying automatically")
}

enum class BleAvailability { AVAILABLE, BLUETOOTH_OFF, PERMISSION_REQUIRED, UNSUPPORTED }

/** Called on the transport's serial lane. Generations invalidate work before platform cleanup. */
class BleSessionLifecycle(
    private val startTransport: (Long) -> Unit,
    private val stopTransport: () -> Unit,
    private val publishState: (MeshTransportState) -> Unit
) {
    @Volatile var requested = false
        private set
    @Volatile var generation = 0L
        private set
    @Volatile var running = false
        private set
    var state = MeshTransportState.OFFLINE
        private set

    fun start(availability: BleAvailability) {
        requested = true
        reconcile(availability)
    }

    fun reconcile(availability: BleAvailability) {
        if (!requested) return
        if (availability != BleAvailability.AVAILABLE) {
            suspendTransport()
            setState(when (availability) {
                BleAvailability.BLUETOOTH_OFF -> MeshTransportState.BLUETOOTH_OFF
                BleAvailability.PERMISSION_REQUIRED -> MeshTransportState.PERMISSION_REQUIRED
                else -> MeshTransportState.ERROR
            })
        } else if (!running) {
            generation++
            running = true
            setState(MeshTransportState.STARTING)
            startTransport(generation)
        }
    }

    fun owns(captured: Long): Boolean = requested && running && generation == captured

    fun ready(captured: Long) {
        if (owns(captured)) setState(MeshTransportState.SEARCHING)
    }

    fun peers(count: Int) {
        if (running && state != MeshTransportState.STARTING) {
            setState(if (count > 0) MeshTransportState.CONNECTED else MeshTransportState.SEARCHING)
        }
    }

    fun failed(captured: Long) {
        if (!owns(captured)) return
        suspendTransport()
        setState(MeshTransportState.ERROR)
    }

    fun stop() {
        requested = false
        suspendTransport()
        setState(MeshTransportState.OFFLINE)
    }

    private fun suspendTransport() {
        if (!running) return
        running = false
        generation++
        stopTransport()
    }

    private fun setState(next: MeshTransportState) {
        if (state == next) return
        state = next
        publishState(next)
    }
}
