package edu.cit.resqmesh.core.logging.trace

import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.PacketTraceEmitter
import edu.cit.resqmesh.core.network.TraceDropLimiter
import edu.cit.resqmesh.core.network.TransferProgress
import edu.cit.resqmesh.core.network.TransferTrace
import edu.cit.resqmesh.core.network.TransferTraceEmitter
import edu.cit.resqmesh.core.network.VoiceClassifier
import edu.cit.resqmesh.core.network.VoiceKind
import edu.cit.resqmesh.feature.devtools.model.TraceTimeline
import edu.cit.resqmesh.feature.devtools.ui.progressLabel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransferTraceTest {
    @Before fun setUp() {
        TraceGate.set(true)
        PacketTraceEmitter.limiter = TraceDropLimiter(500L) { 0L }
    }

    @After fun tearDown() {
        TraceGate.set(false)
        PacketTraceEmitter.limiter = TraceDropLimiter()
    }

    @Test fun progressEmitsAtStartTenthsAndEndOnly() {
        val emitted = (0..18).filter { TransferProgress.shouldEmit(it, 18) }
        assertTrue(0 in emitted && 18 in emitted)
        assertTrue(emitted.size <= 12)
        assertEquals(listOf(0, 1, 2), (0..2).filter { TransferProgress.shouldEmit(it, 3) })
        assertFalse(TransferProgress.shouldEmit(0, 0))
    }

    @Test fun progressRecordedAsDoneOverTotal() {
        TransferTraceEmitter.transfer(
            TransferTrace("PEER1234", "tid1", TraceDir.OUT, TraceStage.STARTED, 18_000, 12, 18)
        )
        val t = TraceStore.controlSnapshot().single()
        assertEquals("12/18", progressLabel(t))
        assertEquals("PEER1234", t.nextHop)
    }

    @Test fun voiceClassificationIsHonest() {
        val voice = MeshPayload(id = "a", audioBytes = byteArrayOf(1))
        assertEquals(VoiceKind.VOICE, VoiceClassifier.classify(voice))
        val priv = MeshPayload(id = "a", isPrivate = true, isEncrypted = true)
        assertEquals(VoiceKind.UNKNOWN, VoiceClassifier.classify(priv))
        assertEquals(VoiceKind.UNKNOWN, VoiceClassifier.classify(null))
        assertEquals(VoiceKind.NOT_VOICE, VoiceClassifier.classify(MeshPayload(id = "a")))
    }

    @Test fun privateTransferGoesToVoiceRingAsContentUnknown() {
        val p = MeshPayload(id = "m1", isPrivate = true, isEncrypted = true)
        TransferTraceEmitter.transfer(
            TransferTrace(
                "PEER1234", "tid2", TraceDir.IN, TraceStage.RECEIVED, 100, 2, 2, messageId = "m1", payload = p
            )
        )
        assertEquals(TraceType.MEDIA_TRANSFER, TraceStore.voiceSnapshot().single().type)
    }

    @Test fun publicImageTransferStaysOutOfVoiceRing() {
        val p = MeshPayload(id = "m2", text = "x")
        TransferTraceEmitter.transfer(
            TransferTrace("P", "tid3", TraceDir.IN, TraceStage.RECEIVED, 10, 1, 1, payload = p)
        )
        assertTrue(TraceStore.voiceSnapshot().isEmpty())
    }

    @Test fun voiceFailuresAreRateLimited() {
        repeat(20) {
            TransferTraceEmitter.voice(
                TraceDir.IN, TraceStage.DROPPED, TraceScope.UNKNOWN, "m", TraceDropReason.MEDIA_REJECT_CORRUPT, "ep"
            )
        }
        assertEquals(1, TraceStore.voiceSnapshot().size)
    }

    @Test fun replayedPiecesRecordNothing() {
        assertEquals(1, TransferTraceEmitter.inboundHeld("P", "t9", 0, 18))
        assertEquals(-1, TransferTraceEmitter.inboundHeld("P", "t9", 0, 18))
        assertEquals(2, TransferTraceEmitter.inboundHeld("P", "t9", 5, 18))
        fun progress(done: Int) = TransferTraceEmitter.transfer(
            TransferTrace("P", "t9", TraceDir.IN, TraceStage.STARTED, 100, done, 18)
        )
        progress(2); progress(2); progress(1)
        assertEquals(1, TraceStore.voiceSnapshot().size)
    }

    @Test fun gateOffClearsTraceState() {
        TransferTraceEmitter.inboundHeld("P", "t10", 3, 18)
        TraceGate.set(false)
        TraceGate.set(true)
        assertEquals(1, TransferTraceEmitter.inboundHeld("P", "t10", 3, 18))
    }

    @Test fun senderKnownVoiceNoteLabelsItsTransferRows() {
        TransferTraceEmitter.voice(
            TraceDir.OUT, TraceStage.QUEUED, TraceScope.PRIVATE, "msg-voice-1", TraceDropReason.NONE, "local"
        )
        val enc = MeshPayload(id = "x", isPrivate = true, isEncrypted = true)
        TransferTraceEmitter.transfer(
            TransferTrace(
                "P", "t11", TraceDir.OUT, TraceStage.QUEUED, 100, 0, 5, messageId = "msg-voice-1", payload = enc
            )
        )
        TransferTraceEmitter.transfer(
            TransferTrace("P", "t12", TraceDir.OUT, TraceStage.QUEUED, 100, 0, 5, messageId = "other", payload = enc)
        )
        val types = TraceStore.voiceSnapshot().filter { it.packetKey != "msg-voi" }.map { it.type }
        assertTrue(TraceType.VOICE_NOTE in types)
        assertTrue(TraceType.MEDIA_TRANSFER in types)
    }

    @Test fun pieceRowsCarryNoSender() {
        val p = MeshPayload(id = "m", senderNodeId = "AAAA1111BBBB", audioBytes = byteArrayOf(1))
        TransferTraceEmitter.transfer(
            TransferTrace("P", "t13", TraceDir.IN, TraceStage.RECEIVED, 10, 1, 1, payload = p)
        )
        assertEquals(null, TraceStore.controlSnapshot().single().sourceId)
    }

    @Test fun exportCarriesNumericRouteAndProgressFields() {
        val t = PacketTrace(
            1, 1, TraceStage.STARTED, TraceDir.OUT, TraceScope.PRIVATE, TraceType.PIECE, "k", null, null,
            null, null, null, 3, 10, "TRANSFER", routeStep = 2, progress = 12, total = 18
        )
        val csv = edu.cit.resqmesh.feature.devtools.export.TraceExporter.csv(listOf(t), emptyList(), emptyList())
        assertTrue(csv.contains("route_step,progress,total"))
        assertTrue(csv.lines().any { it.contains(",3,2,12,18,10,") })
        val text = edu.cit.resqmesh.feature.devtools.export.TraceExporter.text(listOf(t), emptyList(), emptyList())
        assertTrue(text.contains("3  2  12  18  10"))
    }

    private var n = 0L
    @Suppress("LongParameterList")
    private fun ev(
        type: TraceType, dir: TraceDir, stage: TraceStage, key: String = "k1", hop: Int? = null,
        step: Int? = null, scope: TraceScope = TraceScope.PUBLIC, drop: TraceDropReason = TraceDropReason.NONE
    ) = PacketTrace(++n, n, stage, dir, scope, type, key, null, null, null, hop, null, 3, 1, null, drop, step)

    @Test fun timelineIsOrderedJoinsReceiptsAndKeepsFailures() {
        val events = listOf(
            ev(TraceType.DELIVERED, TraceDir.IN, TraceStage.RECEIVED),
            ev(TraceType.MESSAGE, TraceDir.OUT, TraceStage.QUEUED),
            ev(TraceType.MESSAGE, TraceDir.OUT, TraceStage.COMPLETED, step = 2, scope = TraceScope.PRIVATE),
            ev(TraceType.MESSAGE, TraceDir.IN, TraceStage.DROPPED, drop = TraceDropReason.DUPLICATE),
            ev(TraceType.MESSAGE, TraceDir.OUT, TraceStage.FAILED, key = "other")
        )
        val t = TraceTimeline.build(events.shuffled(), "k1")
        assertEquals(listOf(events[0], events[1], events[2], events[3]).sortedBy { it.seq }, t.map { it.event })
        assertTrue(t.first { it.event.type == TraceType.DELIVERED }.isReceipt)
        assertTrue(t.last().isFailure)
        assertTrue(t.last().label.startsWith("Dropped (DUPLICATE)"))
        assertTrue(t.any { it.label.startsWith("Relayed") })
        assertTrue(TraceTimeline.build(events, "").isEmpty())
    }

    @Test fun timelineIsCappedToNewest() {
        val events = (1..10).map { ev(TraceType.MESSAGE, TraceDir.OUT, TraceStage.QUEUED) }
        val t = TraceTimeline.build(events, "k1", cap = 4)
        assertEquals(events.takeLast(4), t.map { it.event })
    }
}
