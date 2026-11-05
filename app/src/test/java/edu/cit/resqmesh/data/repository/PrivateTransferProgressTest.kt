package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.network.OutboundFrameEvent
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants
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

    private val route = listOf("Me#A", "Relay#B", "Peer#C")
    private fun completedImage(media: Boolean = true) =
        PrivateTransferProgress("current", 2, route = route, media = media)
        .observe(event(OutboundFrameEvent.Stage.COMPLETED), 10_000)

    @Test fun unchangedRouteAfterHopCustodyWaitsForTheRelayBeforeResendingMedia() {
        val p = completedImage()
        val deadline = 10_000 + MeshDeliveryConstants.PRIVATE_CUSTODY_WAIT_MS
        assertFalse(p.shouldRetry(deadline - 1, 1_000, false, route))
        assertFalse(p.shouldRetry(deadline - 1, 1_000, false, emptyList()))
        assertTrue(p.shouldRetry(deadline, 1_000, false, route))
    }

    @Test fun changedRouteAfterHopCustodyResendsAtOnce() {
        assertTrue(completedImage().shouldRetry(10_001, 1_000, false, listOf("Me#A", "Other#D", "Peer#C")))
    }

    @Test fun textOnAnUnchangedRouteKeepsTheMeasuredReceiptBudget() {
        val p = completedImage(media = false)
        assertFalse(p.shouldRetry(24_999, 1_000, false, route))
        assertTrue(p.shouldRetry(25_000, 1_000, false, route))
    }
}
