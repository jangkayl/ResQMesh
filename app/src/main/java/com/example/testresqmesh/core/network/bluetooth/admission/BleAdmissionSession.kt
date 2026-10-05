package com.example.testresqmesh.core.network.bluetooth.admission

import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.BleAdmissionScheduler
import com.example.testresqmesh.core.network.bluetooth.BleAdvertisement

internal data class BootstrapCandidate(
    val identity: String,
    var endpoint: String,
    var peerName: String,
    var peerConnections: Int,
    var nextAttemptAt: Long,
    var attempts: Int = 0,
    val enqueuedAt: Long = System.currentTimeMillis()
)

internal class BleAdmissionSession(
    private val scheduler: BleAdmissionScheduler,
    private val isNodeActive: () -> Boolean
) {
    val candidates = linkedMapOf<String, BootstrapCandidate>()
    var drainScheduled = false
    var generation = 0L
    var lastBridgeAt = Long.MIN_VALUE
    val advertisements = linkedMapOf<String, Pair<BleAdvertisement, Long>>()

    fun stop() {
        generation++
        candidates.clear()
        advertisements.clear()
        drainScheduled = false
        lastBridgeAt = Long.MIN_VALUE
    }

    fun later(delay: Long = 0L, action: () -> Unit) {
        val captured = generation
        scheduler.postDelayed(delay) {
            if (captured == generation && isNodeActive()) action()
        }
    }

    fun candidateIdentity(peerName: String, endpoint: String): String =
        NodeIdentity.idOf(peerName)?.let { "node:$it" } ?: "endpoint:${NodeIdentity.key(peerName).ifEmpty { endpoint }}"
}
