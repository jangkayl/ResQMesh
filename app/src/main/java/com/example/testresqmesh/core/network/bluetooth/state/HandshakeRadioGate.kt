package com.example.testresqmesh.core.network.bluetooth.state

/**
 * Owns the short radio-setup quarantine across client and server callbacks.
 *
 * The first owner pauses discovery scanning; only the last owner may resume it. This prevents an
 * old timeout or disconnect callback from restarting scanning underneath a replacement handshake.
 */
class HandshakeRadioGate {
    private val owners = LinkedHashMap<String, Long>()

    @Synchronized fun begin(owner: String, now: Long = System.currentTimeMillis()): Boolean {
        val wasIdle = owners.isEmpty()
        owners[owner] = now
        return wasIdle
    }

    @Synchronized fun finish(owner: String): Boolean {
        if (owners.remove(owner) == null) return false
        return owners.isEmpty()
    }

    @Synchronized fun isActive(): Boolean = owners.isNotEmpty()

    @Synchronized fun activeOwnerInfo(now: Long = System.currentTimeMillis()): Pair<String?, Long> {
        val entry = owners.entries.firstOrNull() ?: return null to 0L
        return entry.key to (now - entry.value).coerceAtLeast(0L)
    }

    @Synchronized fun clear(): Boolean {
        val hadOwners = owners.isNotEmpty()
        owners.clear()
        return hadOwners
    }
}
