package com.example.testresqmesh.core.network.bluetooth.state

import org.junit.Assert.*
import org.junit.Test

class BoundedPayloadWriterTest {
    @Test fun oneWorkerPreservesOrderAndControlOvertakesOnlyWholeFrames() {
        lateinit var run: () -> Unit
        lateinit var writer: BoundedPayloadWriter
        val sent = mutableListOf<Int>()
        var workers = 0
        writer = BoundedPayloadWriter({ true }, { payload ->
            sent += payload[0].toInt()
            if (sent.size == 1) assertTrue(writer.offer(byteArrayOf(9), priority = true))
            if (sent.size == 4) writer.close()
        }, { fail("unexpected writer failure") }, { workers++; run = it })
        assertTrue(writer.offer(byteArrayOf(1)))
        assertTrue(writer.offer(byteArrayOf(2)))
        assertTrue(writer.offer(byteArrayOf(3)))
        run()
        assertEquals(1, workers)
        assertEquals(listOf(1, 9, 2, 3), sent)
        assertFalse(writer.offer(byteArrayOf(4)))
    }

    @Test fun activePayloadConsumesByteBudgetAndKeepsControlReserve() {
        lateinit var run: () -> Unit
        lateinit var writer: BoundedPayloadWriter
        var calls = 0
        writer = BoundedPayloadWriter({ true }, {
            if (++calls == 1) {
                assertFalse(writer.offer(byteArrayOf(2)))
                assertTrue(writer.offer(byteArrayOf(3), true))
            } else writer.close()
        }, { fail("unexpected writer failure") }, { run = it })
        assertTrue(writer.offer(ByteArray(OutboundQueuePolicy.MAX_ORDINARY_BYTES - Int.SIZE_BYTES)))
        run()
        assertEquals(2, calls)
    }

    @Test fun failureReturnsActiveAndQueuedPayloadsExactlyOnce() {
        lateinit var run: () -> Unit
        val failures = mutableListOf<List<ByteArray>>()
        val writer = BoundedPayloadWriter({ true }, { throw java.io.IOException() }, { failures += it }, { run = it })
        writer.offer(byteArrayOf(1)); writer.offer(byteArrayOf(2))
        run()
        assertEquals(listOf(1, 2), failures.single().map { it[0].toInt() })
        assertFalse(writer.offer(byteArrayOf(3)))
        assertTrue(writer.close().isEmpty())
    }

    @Test fun obsoleteSocketDoesNotWriteOrReplayOntoReplacement() {
        lateinit var run: () -> Unit
        var owned = true
        val retired = mutableListOf<List<ByteArray>>()
        val writer = BoundedPayloadWriter({ owned }, { fail("obsolete write") }, { fail("obsolete replay") }, { run = it },
            onRetired = { retired += it })
        assertTrue(writer.offer(byteArrayOf(1)))
        owned = false
        run()
        assertFalse(writer.offer(byteArrayOf(2)))
        assertEquals(listOf(1), retired.single().map { it[0].toInt() })
        assertTrue(writer.close().isEmpty())
    }

    @Test fun countBoundLeavesEightControlSlotsAndNeverStartsExtraWorkers() {
        val writer = BoundedPayloadWriter({ true }, {}, {}, {})
        repeat(120) { assertTrue(writer.offer(byteArrayOf(1))) }
        assertFalse(writer.offer(byteArrayOf(1)))
        repeat(8) { assertTrue(writer.offer(byteArrayOf(2), true)) }
        assertFalse(writer.offer(byteArrayOf(2), true))
        assertEquals(128, writer.close().size)
    }
    @Test fun idleCheckProtectsBothQueuedAndActiveFramesDuringBridgeReclaim() {
        lateinit var run: () -> Unit
        lateinit var writer: BoundedPayloadWriter
        writer = BoundedPayloadWriter({ true }, {
            assertFalse(writer.isIdle())
        }, { fail("unexpected failure") }, { run = it }, {
            assertTrue(writer.isIdle())
            writer.close()
        })
        assertTrue(writer.isIdle())
        writer.offer(byteArrayOf(1))
        assertFalse(writer.isIdle())
        run()
        assertFalse(writer.isIdle())
    }

}
