package com.example.testresqmesh.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class PrivateMessagePolicyTest {
    private fun buildWithKey(key: String?) = PayloadFactory.buildPrivatePayload(
        msgId = "message", timestamp = 1L, senderName = "Alice [A]#alice",
        targetName = "Bob [B]#bob", text = "secret", imageBase64 = null,
        audioBase64 = null, locationLat = null, locationLng = null,
        directedRoute = emptyList(), targetPubKey = key, channelId = "1"
    )

    @Test fun missingKeyCannotCreatePrivatePayload() {
        for (key in listOf(null, "", " ")) {
            try {
                buildWithKey(key)
                fail("A private payload must not be built without a recipient key")
            } catch (expected: IllegalArgumentException) {
                assertEquals("Recipient public key is unavailable", expected.message)
            }
        }
    }

    @Test fun disconnectInvalidatesOldKeyUntilNewPulse() {
        val cache = PeerPublicKeyCache()
        val peer = "Bob [B]#bob"
        cache.put(peer, "old-key", "old-endpoint")
        assertEquals("old-key", cache.get(peer))
        cache.forgetDirectLink(peer, "old-endpoint")
        assertNull(cache.get(peer))
        cache.put(peer, "new-key", "new-endpoint")
        assertEquals("new-key", cache.get(peer))
        cache.clear()
        assertNull(cache.get(peer))
    }

    @Test fun pulseBeforePostedConnectionUpdateKeepsCurrentKey() {
        val cache = PeerPublicKeyCache()
        val peer = "Bob [B]#bob"
        cache.put(peer, "old-key", "old-endpoint")
        cache.put(peer, "new-key", "new-endpoint")
        cache.observeDirectLink(peer, "new-endpoint")
        assertEquals("new-key", cache.get(peer))
        cache.forgetDirectLink(peer, "old-endpoint")
        assertEquals("new-key", cache.get(peer))
    }

    @Test fun newEndpointWithoutPulseCannotUseOldKey() {
        val cache = PeerPublicKeyCache()
        val peer = "Bob [B]#bob"
        cache.put(peer, "old-key", "old-endpoint")
        cache.observeDirectLink(peer, "new-endpoint")
        assertNull(cache.get(peer))
    }
}
