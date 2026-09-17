package com.example.testresqmesh.data.repository

import android.content.Context
import com.example.testresqmesh.core.model.BlockRelationship
import com.example.testresqmesh.core.model.BlockRelationshipOrigin
import com.example.testresqmesh.core.model.BlockRelationshipStatus
import com.example.testresqmesh.core.model.NodeIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Persists direct-link block relationships by stable node identity, never BLE address. */
class BlockRelationshipStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _relationships = MutableStateFlow(load())
    val relationships: StateFlow<Map<String, BlockRelationship>> = _relationships.asStateFlow()

    fun beginLocal(peerName: String, operationId: String, now: Long = System.currentTimeMillis()): BlockRelationship = synchronized(lock) {
        val key = NodeIdentity.key(peerName)
        val current = _relationships.value[key]
        if (current != null && current.deniesDirectLink) return@synchronized current
        val relationship = BlockRelationship(peerName, operationId, BlockRelationshipOrigin.LOCAL, BlockRelationshipStatus.PENDING_ACK, now)
        replace(key, relationship)
        relationship
    }

    /** Returns the active relationship; duplicate requests retain the original operation state. */
    fun acceptRemote(peerName: String, operationId: String, replyPublicKey: String, now: Long = System.currentTimeMillis()): BlockRelationship = synchronized(lock) {
        val key = NodeIdentity.key(peerName)
        val current = _relationships.value[key]
        if (current != null && current.deniesDirectLink) return@synchronized current
        if (current?.status == BlockRelationshipStatus.RELEASED_LOCALLY && current.operationId == operationId) {
            return@synchronized current
        }
        val relationship = BlockRelationship(
            peerName = peerName,
            operationId = operationId,
            origin = BlockRelationshipOrigin.REMOTE,
            status = BlockRelationshipStatus.CONFIRMED,
            updatedAt = now,
            replyPublicKey = replyPublicKey
        )
        replace(key, relationship)
        relationship
    }

    fun confirmLocalAck(peerName: String, operationId: String, now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val key = NodeIdentity.key(peerName)
        val current = _relationships.value[key] ?: return@synchronized false
        if (current.origin != BlockRelationshipOrigin.LOCAL || current.operationId != operationId ||
            current.status != BlockRelationshipStatus.PENDING_ACK
        ) return@synchronized false
        replace(key, current.copy(status = BlockRelationshipStatus.CONFIRMED, updatedAt = now))
        true
    }

    /** Local release never affects the remote peer and leaves a tombstone against stale callbacks. */
    fun releaseLocal(peerName: String, now: Long = System.currentTimeMillis()): BlockRelationship? = synchronized(lock) {
        val key = NodeIdentity.key(peerName)
        val current = _relationships.value[key] ?: return@synchronized null
        if (current.status == BlockRelationshipStatus.RELEASED_LOCALLY) return@synchronized current
        val released = current.copy(status = BlockRelationshipStatus.RELEASED_LOCALLY, updatedAt = now)
        replace(key, released)
        released
    }

    fun setSealedRequest(peerName: String, operationId: String, sealedRequest: String): BlockRelationship? = synchronized(lock) {
        val key = NodeIdentity.key(peerName)
        val current = _relationships.value[key] ?: return@synchronized null
        if (current.origin != BlockRelationshipOrigin.LOCAL || current.operationId != operationId) return@synchronized null
        val updated = current.copy(sealedRequest = sealedRequest, updatedAt = System.currentTimeMillis())
        replace(key, updated)
        updated
    }

    fun activeRelationships(): Collection<BlockRelationship> =
        _relationships.value.values.filter { it.deniesDirectLink }

    fun relationshipFor(peerName: String): BlockRelationship? =
        _relationships.value[NodeIdentity.key(peerName)] ?: _relationships.value.values.firstOrNull {
            NodeIdentity.matches(it.peerName, peerName)
        }

    private fun replace(key: String, relationship: BlockRelationship) {
        _relationships.value = _relationships.value.toMutableMap().apply { put(key, relationship) }
        persist(_relationships.value)
    }

    private fun load(): Map<String, BlockRelationship> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RELATIONSHIPS, "[]"))
        buildMap {
            repeat(array.length()) { index ->
                val json = array.getJSONObject(index)
                val relationship = BlockRelationship(
                    peerName = json.getString("peerName"),
                    operationId = json.getString("operationId"),
                    origin = BlockRelationshipOrigin.valueOf(json.getString("origin")),
                    status = BlockRelationshipStatus.valueOf(json.getString("status")),
                    updatedAt = json.getLong("updatedAt"),
                    sealedRequest = json.optString("sealedRequest"),
                    replyPublicKey = json.optString("replyPublicKey")
                )
                val key = NodeIdentity.key(relationship.peerName)
                if (key.isNotBlank()) put(key, relationship)
            }
        }
    }.getOrDefault(emptyMap())

    private fun persist(relationships: Map<String, BlockRelationship>) {
        val array = JSONArray()
        relationships.values.forEach { relationship ->
            array.put(JSONObject().apply {
                put("peerName", relationship.peerName)
                put("operationId", relationship.operationId)
                put("origin", relationship.origin.name)
                put("status", relationship.status.name)
                put("updatedAt", relationship.updatedAt)
                put("sealedRequest", relationship.sealedRequest)
                put("replyPublicKey", relationship.replyPublicKey)
            })
        }
        preferences.edit().putString(KEY_RELATIONSHIPS, array.toString()).apply()
    }

    private companion object {
        const val PREFERENCES = "resqmesh_block_relationships"
        const val KEY_RELATIONSHIPS = "relationships_v1"
    }
}
