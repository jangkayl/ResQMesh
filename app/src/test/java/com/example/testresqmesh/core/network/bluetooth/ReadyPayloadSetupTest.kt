package com.example.testresqmesh.core.network.bluetooth

import org.junit.Assert.*
import org.junit.Test

class ReadyPayloadSetupTest {
    private class Fixture(supported: Boolean = true) {
        var time = 0L
        var valid = true
        var idle = true
        var socket = false
        var readAccepted = true
        var mtuAccepted = true
        var reads = 0
        var mtus = 0
        var stalls = 0
        val gates = mutableListOf<String?>()
        val timers = mutableListOf<Pair<Long, () -> Unit>>()
        val opens = mutableListOf<Pair<Int, (Boolean) -> Unit>>()
        val setup = ReadyPayloadSetup({ valid }, { idle }, { socket }, { reads++; readAccepted },
            { mtus++; mtuAccepted }, { port, done -> opens += port to done },
            { delay, action -> timers += time + delay to action }, { gates += it }, supportsPort = supported,
            onStalled = { stalls++ })
        fun tick(duration: Long) {
            val target = time + duration
            while (true) {
                val next = timers.filter { it.first <= target }.minByOrNull { it.first } ?: break
                timers.remove(next); time = next.first; next.second()
            }
            time = target
        }
    }

    @Test fun waitsForActiveGattFrameThenSerializesPortAndMtu() {
        val f = Fixture(); f.idle = false; f.setup.start()
        assertEquals(0, f.reads)
        f.idle = true; f.setup.onGattIdle()
        assertEquals(listOf("READ_L2CAP_PSM"), f.gates)
        f.setup.onPort(25)
        assertEquals(listOf("READ_L2CAP_PSM", "REQUEST_MTU"), f.gates)
        assertEquals(25, f.opens.single().first)
        f.setup.onMtu()
        assertNull(f.gates.last())
    }

    @Test fun rejectedReadRetriesWithoutWaitingForAnotherReadyEvent() {
        val f = Fixture(); f.readAccepted = false; f.mtuAccepted = false
        f.setup.start(); assertEquals(1, f.reads)
        f.tick(999); assertEquals(1, f.reads)
        f.tick(1); assertEquals(2, f.reads)
        f.tick(2_000); assertEquals(3, f.reads)
        f.tick(29_999); assertEquals(3, f.reads)
        f.tick(1); assertEquals(4, f.reads)
        assertEquals(1, f.mtus)
    }

    @Test fun missingReadCallbackKeepsGateAndRetiresOwnerWithoutAnotherAttOperation() {
        val f = Fixture(); f.setup.start(); f.tick(5_000)
        assertEquals("READ_L2CAP_PSM", f.gates.last())
        assertEquals(1, f.stalls)
        f.tick(60_000)
        assertEquals(1, f.reads)
        assertEquals(0, f.mtus)
        f.setup.onPort(42)
        assertEquals("READ_L2CAP_PSM", f.gates.last())
        assertTrue(f.opens.isEmpty())
    }

    @Test fun missingMtuCallbackCannotReleaseWritesAndLateCallbackIsIgnored() {
        val f = Fixture(supported = false); f.setup.start(); f.tick(5_000)
        assertEquals(1, f.stalls)
        f.setup.onMtu(); f.setup.start(); f.tick(60_000)
        assertEquals(listOf("REQUEST_MTU"), f.gates)
        assertEquals(1, f.mtus)
    }
    @Test fun missingMtuCallbackPreservesHealthyL2capAndRetiresAfterSocketLoss() {
        val f = Fixture(); f.setup.start(); f.setup.onPort(25)
        f.socket = true; f.opens.single().second(true); f.tick(5_000)
        assertEquals(0, f.stalls)
        assertEquals("REQUEST_MTU", f.gates.last())
        f.setup.onMtu(); f.setup.onGattIdle(); f.tick(30_000)
        assertEquals(1, f.reads); assertEquals(1, f.mtus)
        f.setup.onSocketLost(); assertEquals(0, f.stalls)
        f.socket = false; f.setup.onSocketLost()
        assertEquals(1, f.stalls)
        f.valid = false; f.setup.onSocketLost(); assertEquals(1, f.stalls)
    }

    @Test fun failedSocketRetriesPortAndStaleGenerationDoesNothing() {
        val f = Fixture(); f.mtuAccepted = false; f.setup.start(); f.setup.onPort(25)
        f.opens.single().second(false)
        f.tick(1_000); assertEquals(2, f.reads)
        f.valid = false; f.setup.onPort(27); f.setup.onMtu(); f.tick(100_000)
        assertEquals(1, f.opens.size)
        assertEquals(2, f.reads)
    }

    @Test fun successfulSocketCancelsRecoveryAndDuplicateReadyDoesNotOpenAnother() {
        val f = Fixture(); f.setup.start(); f.setup.start()
        assertEquals(1, f.reads)
        f.setup.onPort(25); f.setup.onMtu()
        f.socket = true; f.opens.single().second(true)
        f.setup.start(); f.tick(40_000)
        assertEquals(1, f.reads)
        assertEquals(1, f.opens.size)
    }

    @Test fun socketLossRecoveryIsRateLimited() {
        val f = Fixture(); f.setup.start(); f.setup.onPort(25); f.setup.onMtu()
        f.socket = true; f.opens.single().second(true)
        f.socket = false; repeat(10) { f.setup.onSocketLost() }
        f.tick(29_999); assertEquals(1, f.reads)
        f.tick(1); assertEquals(2, f.reads)
    }

    @Test fun unsupportedL2capOnlyNegotiatesMtuOnce() {
        val f = Fixture(supported = false); f.mtuAccepted = false
        f.setup.start(); f.tick(100_000)
        assertEquals(0, f.reads)
        assertEquals(1, f.mtus)
    }
}
