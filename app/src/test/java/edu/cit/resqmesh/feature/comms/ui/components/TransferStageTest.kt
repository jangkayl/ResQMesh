package edu.cit.resqmesh.feature.comms.ui.components

import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.network.SendProgress
import edu.cit.resqmesh.core.network.SendProgressBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferStageTest {

    @Test
    fun liveStagesFollowPieceProgress() {
        assertEquals(TransferStage.Waiting, transferStage(message(), progress(SendProgress.State.QUEUED)))
        assertEquals(TransferStage.Sending, transferStage(message(), progress(SendProgress.State.SENDING)))
        assertEquals(TransferStage.Stalled, transferStage(message(), progress(SendProgress.State.STALLED)))
        assertEquals(TransferStage.Failed, transferStage(message(), progress(SendProgress.State.FAILED)))
    }

    @Test
    fun hopCustodyNeverClaimsMoreThanTheNextPhone() {
        val done = progress(SendProgress.State.HOP_COMPLETE)
        assertEquals(TransferStage.ReachedPhone, transferStage(message(), done))
        assertEquals(TransferStage.ReachedNextPhone, transferStage(message(isHopped = true), done))
        assertEquals(TransferStage.ReachedNearby, transferStage(message(isPrivate = false), done))
    }

    @Test
    fun receiptsAndIncomingMessagesHideProgress() {
        val sending = progress(SendProgress.State.SENDING)
        assertNull(transferStage(message(deliveredTo = listOf("Ari")), sending))
        assertNull(transferStage(message(seenBy = listOf("Ari")), sending))
        assertNull(transferStage(message(isMine = false), sending))
        assertNull(transferStage(message(), null))
    }

    @Test
    fun boardCombinesHopsWithTheMostUrgentState() {
        val combined = SendProgressBoard.combine(listOf(
            SendProgress(32, 32, SendProgress.State.HOP_COMPLETE, updatedAt = 1),
            SendProgress(10, 32, SendProgress.State.STALLED, resent = 2, reprobes = 1, updatedAt = 2)
        ))
        assertEquals(SendProgress.State.STALLED, combined.state)
        assertEquals(42, combined.acked)
        assertEquals(64, combined.total)
        assertEquals(65, combined.percent)
        assertEquals(1, combined.reprobes)
        assertEquals(SendProgress.State.FAILED, SendProgressBoard.combine(listOf(progress(SendProgress.State.FAILED))).state)
    }

    private fun progress(state: SendProgress.State) = SendProgress(12, 32, state)

    private fun message(
        isMine: Boolean = true,
        isPrivate: Boolean = true,
        isHopped: Boolean = false,
        deliveredTo: List<String> = emptyList(),
        seenBy: List<String> = emptyList()
    ) = ChatMessage(
        id = "m1", senderName = "Me", text = "", imageBase64 = "img", audioBase64 = null,
        isMine = isMine, isPrivate = isPrivate, isHopped = isHopped, deliveredTo = deliveredTo, seenBy = seenBy
    )
}
