package com.example.testresqmesh.core.network

import com.example.testresqmesh.core.network.bluetooth.state.OutboundQueuePolicy
import org.junit.Assert.*
import org.junit.Test

class RelayForwarderTest {
    private class Fixture {
        var time = 0L
        var active = true
        var endpoint: String? = "old-endpoint"
        var result = TransportDispatchResult.REJECTED_QUEUE_FULL
        val sends = mutableListOf<String>()
        val timers = mutableListOf<Pair<Long, () -> Unit>>()
        val logs = mutableListOf<String>()
        val relay = RelayForwarder({ active }, { endpoint }, { ep, _, _ -> sends += ep; result },
            { delay, action -> timers += time + delay to action }, { time }, { logs += it })
        fun tick(duration: Long) {
            val target = time + duration
            while (true) {
                val next = timers.filter { it.first <= target }.minByOrNull { it.first } ?: break
                timers.remove(next); time = next.first; next.second()
            }
            time = target
        }
    }

    @Test fun rejectedForwardResolvesReplacementEndpointAndStopsAfterAcceptance() {
        val f = Fixture(); f.relay.forward("wire", "B001", byteArrayOf(1), false)
        f.endpoint = "replacement"; f.result = TransportDispatchResult.ACCEPTED
        f.tick(249); assertEquals(listOf("old-endpoint"), f.sends)
        f.tick(1); assertEquals(listOf("old-endpoint", "replacement"), f.sends)
        f.tick(20_000); assertEquals(2, f.sends.size)
    }

    @Test fun unavailableHopWakesOnReadyAndDuplicatesShareOneOwner() {
        val f = Fixture(); f.endpoint = null
        repeat(10) { f.relay.forward("wire", "b001", byteArrayOf(1), true) }
        f.endpoint = "fresh"; f.result = TransportDispatchResult.ACCEPTED; f.relay.wake()
        f.tick(100); assertEquals(listOf("fresh"), f.sends)
        f.tick(20_000); assertEquals(1, f.sends.size)
    }

    @Test fun expiryAndSessionStopBoundRetries() {
        val f = Fixture(); f.relay.forward("wire", "B001", byteArrayOf(1), false)
        f.tick(10_000)
        assertTrue(f.logs.any { it.startsWith("RELAY_EXPIRED") })
        val count = f.sends.size; f.tick(50_000); assertEquals(count, f.sends.size)
        f.relay.forward("next", "B001", byteArrayOf(2), false)
        f.active = false; f.tick(10_000)
        assertEquals(count + 1, f.sends.size)
    }

    @Test fun acceptedNeighborIsNotRepeatedWhenAnotherNeighborRejects() {
        var time = 0L
        val counts = mutableMapOf<String, Int>()
        val timers = mutableListOf<() -> Unit>()
        var bReady = false
        val relay = RelayForwarder({ true }, { it }, { ep, _, _ ->
            counts[ep] = (counts[ep] ?: 0) + 1
            if (ep == "A" || bReady) TransportDispatchResult.ACCEPTED else TransportDispatchResult.REJECTED_QUEUE_FULL
        }, { _, action -> timers += action }, { time })
        relay.forward("public", "A", byteArrayOf(1), false)
        relay.forward("public", "B", byteArrayOf(1), false)
        bReady = true; time = 250; timers.removeAt(0)()
        assertEquals(1, counts["A"])
        assertEquals(2, counts["B"])
    }

    @Test fun invalidFramesNeverRetryAndOrdinaryWorkCannotConsumeControlReserve() {
        val f = Fixture(); f.result = TransportDispatchResult.REJECTED_INVALID_FRAME
        f.relay.forward("invalid", "B001", byteArrayOf(1), false); f.tick(10_000)
        assertEquals(1, f.sends.size)
        f.result = TransportDispatchResult.REJECTED_QUEUE_FULL
        f.relay.forward("bulk", "B001", ByteArray(OutboundQueuePolicy.MAX_ORDINARY_BYTES - 4), false)
        f.relay.forward("overflow", "B001", byteArrayOf(2), false)
        assertTrue(f.logs.any { it.startsWith("RELAY_OVERFLOW") })
        f.relay.forward("receipt", "B001", byteArrayOf(3), true)
        assertTrue(f.logs.any { it.startsWith("RELAY_RETAINED transmission=receipt") })
    }
}
