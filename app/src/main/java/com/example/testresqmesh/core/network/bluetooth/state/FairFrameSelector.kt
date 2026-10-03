package com.example.testresqmesh.core.network.bluetooth.state

/** Control reserve is independent of small ordinary frames; never split an active frame. */
class FairFrameSelector {
    private var smallBurst = 0
    fun <T> next(entries: Collection<T>, control: (T) -> Boolean, bytes: (T) -> Int): T? {
        entries.firstOrNull(control)?.let { return it }
        val small = entries.firstOrNull { bytes(it) <= SMALL_FRAME_BYTES }
        val bulk = entries.firstOrNull { bytes(it) > SMALL_FRAME_BYTES }
        return if (small != null && (bulk == null || smallBurst < 4)) {
            smallBurst = (smallBurst + 1).coerceAtMost(4)
            small
        } else {
            smallBurst = 0
            bulk
        }
    }

    companion object { const val SMALL_FRAME_BYTES = 4 * 1024 }
}
