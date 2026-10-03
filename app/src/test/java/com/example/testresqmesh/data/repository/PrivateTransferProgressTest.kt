package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.network.OutboundFrameEvent
import org.junit.Assert.*
import org.junit.Test

class PrivateTransferProgressTest {
    private fun event(stage: OutboundFrameEvent.Stage, id: String = "current", duration: Long = 0) =
        OutboundFrameEvent(id, "message", stage, "TRANSFER", 1, 1, 8192, transferMs = duration)

    @Test fun queuedVoiceDoesNotConsumeTheRecipientReceiptDeadline() {
        val p = PrivateTransferProgress("current", 3)
        assertFalse(p.shouldRetry(120_000, 1_000, retained = true))
        assertTrue(p.shouldRetry(61_000, 1_000, retained = false))
    }

    @Test fun receiptBudgetStartsAtCompletionAndAllowsMeasuredRelayTime() {
        val p = PrivateTransferProgress("current", 3).observe(event(OutboundFrameEvent.Stage.COMPLETED, duration = 20_000), 50_000)
        assertFalse(p.shouldRetry(144_999, 1_000, false))
        assertTrue(p.shouldRetry(145_000, 1_000, false))
    }

    @Test fun lateEventsCannotResetOrFailTheCurrentTransmission() {
        val p = PrivateTransferProgress("current", 1).observe(event(OutboundFrameEvent.Stage.COMPLETED), 1_000)
        assertEquals(p, p.observe(event(OutboundFrameEvent.Stage.FAILED, "old"), 2_000))
        assertEquals(p, p.observe(event(OutboundFrameEvent.Stage.QUEUED), 2_000))
        assertEquals(p, p.observe(event(OutboundFrameEvent.Stage.COMPLETED), 3_000))
        assertTrue(p.shouldRetry(16_000, 1_000, false))
    }

    @Test fun aRejectedCurrentTransferCanRetryWithoutWaitingForRecipientTimeout() {
        val p = PrivateTransferProgress("current", 1).observe(event(OutboundFrameEvent.Stage.FAILED), 2_000)
        assertTrue(p.shouldRetry(2_000, 1_000, false))
    }
}
