package com.example.testresqmesh.core.network.bluetooth.state

/**
 * Owns the short radio-setup quarantine across client and server callbacks.
 *
 * The first owner pauses discovery scanning; only the last owner may resume it. This prevents an
 * old timeout or disconnect callback from restarting scanning underneath a replacement handshake.
 */
class HandshakeRadioGate {
    private val owners = mutableSetOf<String>()

    @Synchronized fun begin(owner: String): Boolean {
        val wasIdle = owners.isEmpty()
        owners += owner
        return wasIdle
    }

    @Synchronized fun finish(owner: String): Boolean {
        if (!owners.remove(owner)) return false
        return owners.isEmpty()
    }

    @Synchronized fun isActive(): Boolean = owners.isNotEmpty()

    @Synchronized fun clear(): Boolean {
        val hadOwners = owners.isNotEmpty()
        owners.clear()
        return hadOwners
    }
}
