package com.example.testresqmesh.core.network.bluetooth.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

class BleLinkRegistryTest {
    private fun BleLinkRegistry.beginClient(endpoint: String = "endpoint") =
        begin(endpoint, BleLinkRole.CLIENT, "stable-id", ConcurrentLinkedDeque(), AtomicBoolean(false), 100L)

    @Test fun readyRequiresCompletedSetup() {
        val registry = BleLinkRegistry()
        val link = registry.beginClient()
        assertFalse(registry.isReady(link.endpoint))
        assertFalse(registry.transition(link, BleLinkState.READY))
        assertTrue(registry.transition(link, BleLinkState.DISCOVERING))
        assertFalse(registry.isReady(link.endpoint))
        assertTrue(registry.transition(link, BleLinkState.CONFIGURING))
        assertTrue(registry.transition(link, BleLinkState.READY, 200L))
        assertEquals(200L, link.readyAt)
        assertTrue(registry.isReady(link.endpoint))
        assertTrue(registry.transition(link, BleLinkState.DISCONNECTING))
        assertTrue(registry.forget(link))
        assertFalse(registry.isReady(link.endpoint))
    }

    @Test fun oldAttemptCannotOwnReplacementLifecycle() {
        val registry = BleLinkRegistry()
        val old = registry.beginClient()
        val replacement = registry.beginClient()
        assertTrue(replacement.generation > old.generation)
        assertFalse(registry.isCurrent(old))
        assertFalse(registry.transition(old, BleLinkState.DISCOVERING))
        assertFalse(registry.forget(old))
        assertTrue(registry.isCurrent(replacement))
        assertEquals(BleLinkState.CONNECTING, replacement.state)
    }

    @Test fun clientAndServerSameEndpointKeepSeparateLifecycles() {
        val registry = BleLinkRegistry()
        val client = registry.beginClient()
        val server = registry.begin(
            client.endpoint, BleLinkRole.SERVER, "stable-id", ConcurrentLinkedDeque(), AtomicBoolean(false), 101L
        )
        assertTrue(registry.transition(server, BleLinkState.CONFIGURING))
        assertTrue(registry.transition(server, BleLinkState.READY))
        assertTrue(registry.isCurrent(client))
        assertTrue(registry.isCurrent(server))
        assertTrue(registry.isReady(client.endpoint))
        assertTrue(registry.forget(server))
        assertFalse(registry.isReady(client.endpoint))
    }

    @Test fun outboundAclRemainsLiveUntilClientStartsDisconnecting() {
        val registry = BleLinkRegistry()
        val client = registry.beginClient("shared-endpoint")
        assertTrue(registry.hasLiveRole(client.endpoint, BleLinkRole.CLIENT))
        assertTrue(registry.transition(client, BleLinkState.DISCOVERING))
        assertTrue(registry.hasLiveRole(client.endpoint, BleLinkRole.CLIENT))
        assertTrue(registry.transition(client, BleLinkState.DISCONNECTING))
        assertFalse(registry.hasLiveRole(client.endpoint, BleLinkRole.CLIENT))
    }

    @Test fun serverSetupDeadlineCannotCloseReadyOrReplacementLink() {
        val registry = BleLinkRegistry()
        val old = registry.begin("endpoint", BleLinkRole.SERVER, null, ConcurrentLinkedDeque(), AtomicBoolean(false), 100L)
        assertTrue(registry.transition(old, BleLinkState.CONFIGURING))
        val replacement = registry.begin("endpoint", BleLinkRole.SERVER, null, ConcurrentLinkedDeque(), AtomicBoolean(false), 101L)
        assertTrue(registry.transition(replacement, BleLinkState.CONFIGURING))
        assertFalse(registry.expireConfiguring(old))
        assertEquals(BleLinkState.CONFIGURING, replacement.state)
        assertTrue(registry.transition(replacement, BleLinkState.READY))
        assertFalse(registry.expireConfiguring(replacement))
        assertEquals(BleLinkState.READY, replacement.state)
        val stalled = registry.begin("endpoint", BleLinkRole.SERVER, null, ConcurrentLinkedDeque(), AtomicBoolean(false), 102L)
        assertTrue(registry.transition(stalled, BleLinkState.CONFIGURING))
        assertTrue(registry.expireConfiguring(stalled))
        assertEquals(BleLinkState.DISCONNECTING, stalled.state)
    }

    @Test fun unfinishedServerRoleRecognizesReadyPeerOnSameOrDifferentEndpoint() {
        val registry = BleLinkRegistry()
        val client = registry.begin("client-mac", BleLinkRole.CLIENT, "peer-id", ConcurrentLinkedDeque(), AtomicBoolean(false))
        assertTrue(registry.transition(client, BleLinkState.DISCOVERING))
        assertTrue(registry.transition(client, BleLinkState.CONFIGURING))
        assertTrue(registry.transition(client, BleLinkState.READY))
        val server = registry.begin("server-mac", BleLinkRole.SERVER, "peer-id", ConcurrentLinkedDeque(), AtomicBoolean(false))
        assertTrue(registry.transition(server, BleLinkState.CONFIGURING))
        assertTrue(registry.hasReadyPeerExcept(server))
        assertTrue(registry.forget(client))
        assertFalse(registry.hasReadyPeerExcept(server))
    }
}
