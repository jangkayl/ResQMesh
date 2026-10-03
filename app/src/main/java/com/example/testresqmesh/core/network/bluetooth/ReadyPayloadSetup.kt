package com.example.testresqmesh.core.network.bluetooth

/** Optional payload setup after CCCD readiness. All calls run on the GATT owner's handler. */
class ReadyPayloadSetup(
    private val isCurrentReady: () -> Boolean,
    private val isGattIdle: () -> Boolean,
    private val hasL2cap: () -> Boolean,
    private val requestPort: () -> Boolean,
    private val requestMtu: () -> Boolean,
    private val openPort: (Int, (Boolean) -> Unit) -> Unit,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val gate: (String?) -> Unit,
    private val log: (String) -> Unit = {},
    private val supportsPort: Boolean = true,
    private val onStalled: () -> Unit
) {
    private enum class Operation { READ_L2CAP_PSM, REQUEST_MTU }
    private var operation: Operation? = null
    private var operationToken = 0L
    private var cycle = 0L
    private var attempts = 0
    private var connecting = false
    private var waiting = false
    private var mtuAttempted = false
    private var readWanted = false
    private var stalled = false

    fun start() {
        if (stalled || !isCurrentReady() || hasL2cap() || connecting || waiting || readWanted || operation != null) return
        cycle++
        attempts = 0
        readWanted = true
        pump()
    }

    fun onGattIdle() = pump()

    private fun pump() {
        if (stalled || !isCurrentReady() || operation != null || !isGattIdle()) return
        if (readWanted && !hasL2cap()) {
            readWanted = false
            if (!supportsPort) { optionalMtu(); return }
            attempts++
            begin(Operation.READ_L2CAP_PSM)
            log("PSM_REQUEST attempt=$attempts")
            if (!requestPort()) onPort(null)
        }
    }

    private fun begin(next: Operation) {
        operation = next
        gate(next.name)
        val token = ++operationToken
        schedule(5_000L) {
            if (isCurrentReady() && operationToken == token && operation == next) {
                log("SETUP_TIMEOUT operation=$next")
                // Android may still own this ATT operation. A local timeout cannot make it idle.
                stalled = true
                if (hasL2cap()) log("ATT_QUARANTINED_L2CAP_PRESERVED") else onStalled()
            }
        }
    }

    fun onPort(port: Int?) {
        if (stalled || !isCurrentReady() || operation != Operation.READ_L2CAP_PSM) return
        operation = null
        // Reserve the next optional operation before releasing writes to the payload executor.
        if (!optionalMtu()) gate(null)
        if (port == null || port <= 0) {
            log("PSM_UNAVAILABLE attempt=$attempts")
            retry()
            return
        }
        connecting = true
        val owner = cycle
        openPort(port) { success ->
            if (!isCurrentReady() || owner != cycle || !connecting) return@openPort
            connecting = false
            log("L2CAP_SETUP result=${if (success) "READY" else "FAILED"} attempt=$attempts")
            if (!success) retry()
        }
    }

    private fun optionalMtu(): Boolean {
        if (mtuAttempted) return false
        mtuAttempted = true
        begin(Operation.REQUEST_MTU)
        log("MTU_REQUEST requested=247")
        if (!requestMtu()) onMtu()
        return true
    }

    fun onMtu() {
        if (stalled || !isCurrentReady() || operation != Operation.REQUEST_MTU) return
        operation = null
        gate(null)
        pump()
    }

    private fun retry() {
        if (waiting || !isCurrentReady()) return
        waiting = true
        val owner = cycle
        val delay = if (attempts >= 3) 30_000L else attempts * 1_000L
        log("SETUP_RETRY delayMs=$delay")
        schedule(delay) {
            if (!isCurrentReady() || owner != cycle) return@schedule
            waiting = false
            if (hasL2cap()) return@schedule
            if (attempts >= 3) { start(); return@schedule }
            readWanted = true
            pump()
        }
    }

    fun onSocketLost() {
        if (stalled) {
            if (isCurrentReady() && !hasL2cap()) onStalled()
            return
        }
        if (waiting || connecting || readWanted || operation != null || !isCurrentReady()) return
        waiting = true
        val owner = cycle
        schedule(30_000L) {
            if (!isCurrentReady() || owner != cycle) return@schedule
            waiting = false
            start()
        }
    }
}
