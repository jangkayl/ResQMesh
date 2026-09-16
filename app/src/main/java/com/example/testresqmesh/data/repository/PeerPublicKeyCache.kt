package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.NodeIdentity

/** Keep the source endpoint so a posted connection update cannot erase a newer pulse. */
class PeerPublicKeyCache {
    private data class Entry(val key: String, val endpointId: String)
    private val keys = mutableMapOf<String, Entry>()

    @Synchronized fun put(peerName: String, key: String, endpointId: String) {
        if (key.isNotBlank()) {
            forget(peerName)
            keys[peerName] = Entry(key, endpointId)
        }
    }

    @Synchronized fun get(peerName: String): String? =
        keys.entries.firstOrNull { NodeIdentity.matches(it.key, peerName) }?.value?.key

    /** A new endpoint invalidates an old key, but preserves a pulse received on this endpoint. */
    @Synchronized fun observeDirectLink(peerName: String, endpointId: String) {
        keys.keys.removeAll { name ->
            NodeIdentity.matches(name, peerName) && keys[name]?.endpointId != endpointId
        }
    }

    /** A delayed disconnect from an older endpoint must not erase its replacement's key. */
    @Synchronized fun forgetDirectLink(peerName: String, endpointId: String) {
        keys.keys.removeAll { name ->
            NodeIdentity.matches(name, peerName) && keys[name]?.endpointId == endpointId
        }
    }

    @Synchronized fun forget(peerName: String) {
        keys.keys.removeAll { NodeIdentity.matches(it, peerName) }
    }

    @Synchronized fun clear() = keys.clear()
}
