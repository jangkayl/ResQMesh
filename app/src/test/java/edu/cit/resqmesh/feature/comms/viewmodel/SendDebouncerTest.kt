package edu.cit.resqmesh.feature.comms.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendDebouncerTest {
    private var now = 1_000L
    private val debouncer = SendDebouncer(400L) { now }

    @Test
    fun repeatedSendInsideWindowIsDropped() {
        assertTrue(debouncer.tryAcquire("pub:txt"))
        now += 399
        assertFalse(debouncer.tryAcquire("pub:txt"))
        now += 1
        assertTrue(debouncer.tryAcquire("pub:txt"))
    }

    @Test
    fun differentKindsAndConversationsDoNotBlockEachOther() {
        assertTrue(debouncer.tryAcquire("pub:txt"))
        assertTrue(debouncer.tryAcquire("pub:aud"))
        assertTrue(debouncer.tryAcquire("priv:bob:txt"))
        assertTrue(debouncer.tryAcquire("priv:amy:txt"))
    }

    @Test
    fun blockedCheckDoesNotConsumeTheGate() {
        assertFalse(debouncer.isBlocked("k"))
        assertFalse(debouncer.isBlocked("k"))
        assertTrue(debouncer.tryAcquire("k"))
        assertTrue(debouncer.isBlocked("k"))
    }
}
