package edu.cit.resqmesh.core.location

data class LiveLocationSpec(val intervalMs: Long, val minDistanceMeters: Float)

/**
 * Counts open live sessions and reports the fastest one, or null when the last session closes.
 * Main thread only, like every caller of [LocationClient].
 */
class LiveLocationDemand(private val onChanged: (LiveLocationSpec?) -> Unit) {
    private val sessions = mutableListOf<LiveLocationSpec>()

    var current: LiveLocationSpec? = null
        private set

    fun acquire(spec: LiveLocationSpec): AutoCloseable {
        sessions += spec
        refresh()
        var closed = false
        return AutoCloseable {
            if (!closed) {
                closed = true
                sessions.remove(spec)
                refresh()
            }
        }
    }

    private fun refresh() {
        val fastest = sessions.minWithOrNull(compareBy({ it.intervalMs }, { it.minDistanceMeters }))
        if (fastest != current) {
            current = fastest
            onChanged(fastest)
        }
    }
}
