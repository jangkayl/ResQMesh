package com.example.testresqmesh.core.network.bluetooth

import android.os.Handler

interface BleAdmissionScheduler {
    fun post(action: () -> Unit)
    fun postDelayed(delayMs: Long, action: () -> Unit)
}

class HandlerAdmissionScheduler(private val handler: Handler) : BleAdmissionScheduler {
    override fun post(action: () -> Unit) { handler.post(action) }
    override fun postDelayed(delayMs: Long, action: () -> Unit) { handler.postDelayed(action, delayMs) }
}
