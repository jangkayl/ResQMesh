package com.example.testresqmesh.core.network.bluetooth

import com.example.testresqmesh.core.network.TransportDispatchResult
import com.example.testresqmesh.core.network.bluetooth.state.BleLink

/** One full identity exchange for either GATT role, owned by that exact link generation. */
class ReadyIdentityExchange(
    private val isCurrentReady: (BleLink) -> Boolean,
    private val sendFullIdentity: (BleLink) -> TransportDispatchResult,
    private val schedule: (Long, () -> Unit) -> Unit
) {
    fun start(link: BleLink) = attempt(link, 1)

    private fun attempt(link: BleLink, number: Int) {
        if (!isCurrentReady(link)) return
        val result = sendFullIdentity(link)
        if (!result.accepted && result != TransportDispatchResult.REJECTED_INVALID_FRAME && number < 3) {
            schedule(5_000L) { attempt(link, number + 1) }
        }
    }
}
