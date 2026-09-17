package com.example.testresqmesh.core.network.bluetooth.state

import java.util.concurrent.ConcurrentHashMap

/** Owns generation-bound heartbeat challenges; scheduling and disconnect I/O stay with the caller. */
class HeartbeatCoordinator(private val now: () -> Long = System::currentTimeMillis) {
    private val challenges = ConcurrentHashMap<String, HeartbeatChallenge>()

    fun contains(endpoint: String): Boolean = challenges.containsKey(endpoint)

    fun pending(endpoint: String): HeartbeatChallenge? = challenges[endpoint]

    fun begin(endpoint: String, role: BleLinkRole, generation: Long, id: String): HeartbeatChallenge? {
        val challenge = HeartbeatChallenge(endpoint, role, generation, id, now())
        return if (challenges.putIfAbsent(endpoint, challenge) == null) challenge else null
    }

    fun markSent(endpoint: String, generation: Long, id: String): HeartbeatChallenge? {
        val pending = challenges[endpoint] ?: return null
        if (pending.generation != generation || pending.id != id) return null
        val sent = pending.copy(sentAt = now())
        return if (challenges.replace(endpoint, pending, sent)) sent else null
    }

    fun acknowledge(endpoint: String, id: String, generation: Long): Boolean {
        val pending = challenges[endpoint] ?: return false
        return pending.accepts(endpoint, id, generation) && challenges.remove(endpoint, pending)
    }

    fun remove(endpoint: String, expected: HeartbeatChallenge? = null): Boolean =
        if (expected == null) challenges.remove(endpoint) != null
        else challenges.remove(endpoint, expected)

    fun clear() = challenges.clear()
}
