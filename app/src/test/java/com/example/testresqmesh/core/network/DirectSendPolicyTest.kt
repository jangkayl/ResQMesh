package com.example.testresqmesh.core.network

import org.junit.Assert.*
import org.junit.Test

class DirectSendPolicyTest {
    @Test fun smallPrivateTextAndShortNoteUseOrdinaryFrames() {
        assertFalse(DirectSendPolicy.usesPieces("MESSAGE", 1734))
        assertFalse(DirectSendPolicy.usesPieces("CONVERSATION", 4096))
        assertTrue(DirectSendPolicy.usesPieces("MESSAGE", 4097))
        assertTrue(DirectSendPolicy.usesPieces("CONVERSATION", 8192))
        assertFalse(DirectSendPolicy.usesPieces("SYSTEM", 8192))
    }

    @Test fun usableL2capWinsAndFallbackIsDeterministic() {
        val gatt = DirectEndpoint("A", "epoch:client:1:GATT", false)
        val l2cap = DirectEndpoint("B", "epoch:server:2:L2CAP", true)
        assertEquals(l2cap, DirectSendPolicy.select(listOf(gatt, l2cap)))
        assertEquals(l2cap, DirectSendPolicy.select(listOf(l2cap, gatt)))
        assertEquals(gatt, DirectSendPolicy.select(listOf(l2cap.copy(l2cap = false), gatt)))
        assertNull(DirectSendPolicy.select(emptyList()))
    }

    @Test fun alternateEndpointPulseOrderingDoesNotChangeTheSelectedOwner() {
        val endpoints = listOf(DirectEndpoint("A", "client:1", false), DirectEndpoint("B", "server:2", true))
        val owner = DirectSendPolicy.select(endpoints)!!.owner
        repeat(10) { assertEquals(owner, DirectSendPolicy.select(if (it % 2 == 0) endpoints else endpoints.reversed())!!.owner) }
        assertNotEquals(owner, DirectSendPolicy.select(listOf(endpoints.last().copy(owner = "server:3")))!!.owner)
    }

    @Test fun messagesAndReceiptsUsePeerSelectionWhileHealthStaysOnItsEndpoint() {
        listOf("MESSAGE", "CONVERSATION", "SEEN", "DELIVERED").forEach { assertTrue(DirectSendPolicy.usesPeerEndpoint(it)) }
        listOf("SYSTEM", "PING", "PONG", "GOODBYE").forEach { assertFalse(DirectSendPolicy.usesPeerEndpoint(it)) }
    }
}
