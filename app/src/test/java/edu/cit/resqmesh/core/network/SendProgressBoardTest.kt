package edu.cit.resqmesh.core.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SendProgressBoardTest {
    @Before fun reset() = SendProgressBoard.clear()
    @After fun cleanUp() = SendProgressBoard.clear()

    private fun report(transfer: String, acked: Int, state: SendProgress.State, at: Long, peer: String = "B") =
        SendProgressBoard.update("m", peer, SendProgress(acked, 32, state, updatedAt = at, transferId = transfer))

    private val shown get() = SendProgressBoard.progress.value["m"]!!

    @Test fun newTransmissionReplacesTheOlderEntryInsteadOfSumming() {
        report("t1", 32, SendProgress.State.HOP_COMPLETE, 1)
        assertFalse(shown.restarted)
        report("t2", 4, SendProgress.State.SENDING, 2)
        assertEquals(4, shown.acked)
        assertEquals(32, shown.total)
        assertTrue(shown.restarted)
    }

    @Test fun lateReportFromAReplacedTransmissionIsIgnored() {
        report("t1", 10, SendProgress.State.SENDING, 1)
        report("t2", 2, SendProgress.State.SENDING, 2)
        report("t1", 0, SendProgress.State.FAILED, 3)
        assertEquals(SendProgress.State.SENDING, shown.state)
        assertEquals(2, shown.acked)
    }

    @Test fun samePublicFrameToSeveralPeersStillCombines() {
        report("t1", 10, SendProgress.State.SENDING, 1, peer = "B")
        report("t1", 20, SendProgress.State.SENDING, 2, peer = "C")
        assertEquals(30, shown.acked)
        assertEquals(64, shown.total)
        assertFalse(shown.restarted)
    }

    @Test fun removeForgetsTheMessage() {
        report("t1", 10, SendProgress.State.SENDING, 1)
        SendProgressBoard.remove("m")
        assertNull(SendProgressBoard.progress.value["m"])
    }
}
