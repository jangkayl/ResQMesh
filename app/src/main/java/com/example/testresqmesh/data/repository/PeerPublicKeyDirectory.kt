package com.example.testresqmesh.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.testresqmesh.core.model.NodeIdentity
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent trust-on-first-use directory for keys learned from direct or relayed SYSTEM pulses.
 * Public keys are not secret, but a changed key is never silently substituted for a pinned key.
 */
class PeerPublicKeyDirectory(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE))

    enum class Observation { TRUSTED, UNCHANGED, KEY_CHANGE_PENDING, INVALID_IDENTITY }

    private data class Entry(
        val nodeId: String,
        val displayName: String,
        val publicKey: String,
        val fingerprint: String,
        val firstSeenAt: Long,
        val lastSeenAt: Long,
        val pendingKey: String? = null
    )

    private val entries = load().toMutableMap()

    @Synchronized fun observe(peerName: String, advertisedNodeId: String, publicKey: String): Observation {
        val nodeId = stableId(peerName, advertisedNodeId) ?: return Observation.INVALID_IDENTITY
        if (publicKey.isBlank()) return Observation.INVALID_IDENTITY
        val now = System.currentTimeMillis()
        val current = entries[nodeId]
        if (current == null) {
            entries[nodeId] = Entry(nodeId, NodeIdentity.displayNameOf(peerName), publicKey, fingerprint(publicKey), now, now)
            persist()
            return Observation.TRUSTED
        }
        if (current.publicKey == publicKey) {
            entries[nodeId] = current.copy(displayName = NodeIdentity.displayNameOf(peerName), lastSeenAt = now, pendingKey = null)
            persist()
            return Observation.UNCHANGED
        }
        entries[nodeId] = current.copy(displayName = NodeIdentity.displayNameOf(peerName), lastSeenAt = now, pendingKey = publicKey)
        persist()
        return Observation.KEY_CHANGE_PENDING
    }

    @Synchronized fun trustedKey(peerName: String): String? {
        val nodeId = NodeIdentity.idOf(peerName)
        if (nodeId != null) return entries[nodeId]?.publicKey
        return entries.values.firstOrNull { matchesPeer(it, peerName) }?.publicKey
    }

    @Synchronized fun hasPendingChange(peerName: String): Boolean {
        val nodeId = NodeIdentity.idOf(peerName)
        if (nodeId != null) return entries[nodeId]?.pendingKey != null
        return entries.values.any { matchesPeer(it, peerName) && it.pendingKey != null }
    }

    /** UI may call this only after presenting the old/new fingerprint to the user. */
    @Synchronized fun acceptPendingChange(peerName: String): Boolean {
        val nodeId = NodeIdentity.idOf(peerName)
        val current = (if (nodeId != null) entries[nodeId] else null)
            ?: entries.values.firstOrNull { matchesPeer(it, peerName) && it.pendingKey != null }
            ?: return false
        val replacement = current.pendingKey ?: return false
        entries[current.nodeId] = current.copy(
            publicKey = replacement,
            fingerprint = fingerprint(replacement),
            pendingKey = null,
            lastSeenAt = System.currentTimeMillis()
        )
        persist()
        return true
    }

    /** Dismisses a pending key change by clearing pendingKey and keeping the pinned key. */
    @Synchronized fun rejectPendingChange(peerName: String): Boolean {
        val nodeId = NodeIdentity.idOf(peerName)
        val current = (if (nodeId != null) entries[nodeId] else null)
            ?: entries.values.firstOrNull { matchesPeer(it, peerName) && it.pendingKey != null }
            ?: return false
        if (current.pendingKey == null) return false
        entries[current.nodeId] = current.copy(pendingKey = null)
        persist()
        return true
    }

    private fun matchesPeer(entry: Entry, peerName: String): Boolean {
        val queryId = NodeIdentity.idOf(peerName)
        if (queryId != null && entry.nodeId.equals(queryId, ignoreCase = true)) return true
        val cleanQuery = NodeIdentity.displayNameOf(peerName).trim()
        val cleanEntry = NodeIdentity.displayNameOf(entry.displayName).trim()
        if (cleanQuery.isNotEmpty() && cleanQuery.equals(cleanEntry, ignoreCase = true)) return true
        val queryBase = cleanQuery.substringBefore('[').trim()
        val entryBase = cleanEntry.substringBefore('[').trim()
        if (queryBase.isNotEmpty() && queryBase.equals(entryBase, ignoreCase = true)) return true
        return NodeIdentity.matches(entry.displayName, peerName)
    }

    private fun stableId(peerName: String, advertisedNodeId: String): String? {
        val fromName = NodeIdentity.idOf(peerName)
        val fromPulse = advertisedNodeId.trim().uppercase()
        if (fromName != null && fromPulse.isNotEmpty() && fromName != fromPulse) return null
        return fromName ?: fromPulse.takeIf { it.isNotEmpty() }
    }

    private fun fingerprint(publicKey: String): String = MessageDigest.getInstance("SHA-256")
        .digest(publicKey.toByteArray())
        .joinToString("") { "%02X".format(it) }
        .take(16)

    private fun load(): Map<String, Entry> = runCatching {
        val array = JSONArray(preferences.getString(KEY_ENTRIES, "[]"))
        buildMap {
            repeat(array.length()) { index ->
                val json = array.getJSONObject(index)
                val nodeId = json.getString("nodeId").uppercase()
                if (nodeId.isNotBlank()) put(nodeId, Entry(
                    nodeId = nodeId,
                    displayName = json.optString("displayName"),
                    publicKey = json.getString("publicKey"),
                    fingerprint = json.optString("fingerprint"),
                    firstSeenAt = json.optLong("firstSeenAt"),
                    lastSeenAt = json.optLong("lastSeenAt"),
                    pendingKey = json.optString("pendingKey").takeIf { it.isNotBlank() }
                ))
            }
        }
    }.getOrDefault(emptyMap())

    private fun persist() {
        val array = JSONArray()
        entries.values.forEach { entry -> array.put(JSONObject().apply {
            put("nodeId", entry.nodeId)
            put("displayName", entry.displayName)
            put("publicKey", entry.publicKey)
            put("fingerprint", entry.fingerprint)
            put("firstSeenAt", entry.firstSeenAt)
            put("lastSeenAt", entry.lastSeenAt)
            put("pendingKey", entry.pendingKey)
        }) }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private companion object {
        const val PREFERENCES = "resqmesh_peer_public_keys"
        const val KEY_ENTRIES = "entries_v1"
    }
}
