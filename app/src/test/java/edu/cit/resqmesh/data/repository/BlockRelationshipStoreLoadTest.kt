package edu.cit.resqmesh.data.repository

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRelationshipStoreLoadTest {
    private fun record(peer: String) = JSONObject()
        .put("peerName", peer).put("operationId", "op-$peer").put("origin", "LOCAL")
        .put("status", "PENDING_ACK").put("updatedAt", 1L)

    private fun prefs(vararg peers: String): SharedPreferences {
        val json = JSONArray().apply { peers.forEach { put(record(it)) } }.toString()
        val loader = SharedPreferences::class.java.classLoader
        return Proxy.newProxyInstance(loader, arrayOf(SharedPreferences::class.java)) { _, method, _ ->
            if (method.name == "getString") json else null
        } as SharedPreferences
    }

    @Test fun placeholderAndIdLessRecordsAreDroppedOnLoad() {
        val store = BlockRelationshipStore(prefs("Unknown Node", "Unknown", "Plain Name", "Carl#C003"))
        assertEquals(listOf("Carl#C003"), store.activeRelationships().map { it.peerName })
    }

    @Test fun blockableNamesNeedAStableIdentity() {
        assertTrue(BlockRelationshipStore.isBlockable("Carl#C003"))
        assertFalse(BlockRelationshipStore.isBlockable("Unknown Node"))
        assertFalse(BlockRelationshipStore.isBlockable("Carl"))
        assertFalse(BlockRelationshipStore.isBlockable(null))
    }
}
