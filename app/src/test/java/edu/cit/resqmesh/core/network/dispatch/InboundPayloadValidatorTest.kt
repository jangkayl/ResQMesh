package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.PayloadDispatcher
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class InboundPayloadValidatorTest {
    private val validator = InboundPayloadValidator()

    private fun encode(p: MeshPayload) = ProtoBuf.encodeToByteArray(MeshPayload.serializer(), p)

    private fun rejected(p: MeshPayload) = validator.validate(p) is InboundPayloadValidator.Result.Rejected
    private fun accepted(p: MeshPayload) = (validator.validate(p) as InboundPayloadValidator.Result.Accepted).payload

    @Test fun ordinaryPayloadIsAcceptedUnchanged() {
        val p = MeshPayload(id = "a", type = "MESSAGE", senderName = "Alice#A001", text = "hi", ttl = 10)
        assertSame(p, accepted(p))
    }

    @Test fun ttlAndHopCountAreClampedButLegacyZeroIsKept() {
        assertEquals(10, accepted(MeshPayload(id = "a", ttl = 99)).ttl)
        assertEquals(0, accepted(MeshPayload(id = "a", ttl = -5)).ttl)
        assertEquals(0, accepted(MeshPayload(id = "a", ttl = 0)).ttl)
        assertEquals(10, accepted(MeshPayload(id = "a", relayHopCount = 500)).relayHopCount)
    }

    @Test fun frameSizedVoiceCiphertextIsNotRejectedForLength() {
        // ~1.1 MiB of audio, JSON/base64 then encrypted/base64 again: well above the old 1 MiB char cap.
        val encrypted = "e".repeat(1_900_000)
        accepted(MeshPayload(id = "v", isPrivate = true, isEncrypted = true, encryptedData = encrypted))
        accepted(MeshPayload(id = "t", text = "t".repeat(1_900_000)))
        val tooLong = "e".repeat(InboundPayloadValidator.MAX_ENCRYPTED + 1)
        assertTrue(rejected(MeshPayload(id = "e", encryptedData = tooLong)))
    }

    @Test fun oversizedFieldsAreRejected() {
        assertTrue(rejected(MeshPayload(id = "x".repeat(129))))
        assertTrue(rejected(MeshPayload(id = "a", senderName = "n".repeat(129))))
        assertTrue(rejected(MeshPayload(id = "a", text = "t".repeat(InboundPayloadValidator.MAX_TEXT + 1))))
        assertTrue(rejected(MeshPayload(id = "a", routePath = List(257) { "n" })))
        assertTrue(rejected(MeshPayload(id = "a", connectedNodeIds = listOf("n".repeat(129)))))
        assertTrue(rejected(MeshPayload(id = "a", imageBytes = ByteArray(2 * 1024 * 1024 + 1))))
    }

    @Test fun limitsAreInclusive() {
        val p = MeshPayload(
            id = "x".repeat(128), text = "t".repeat(InboundPayloadValidator.MAX_TEXT), routePath = List(256) { "n" }
        )
        assertSame(p, accepted(p))
    }

    @Test fun dispatcherDropsRejectedPayloadsBeforeHandlers() {
        val cb = RecordingDispatcherCallback()
        val dispatcher = PayloadDispatcher(cb)
        val bad = MeshPayload(
            id = "m1", type = "MESSAGE", senderName = "Alice#A001",
            text = "t".repeat(InboundPayloadValidator.MAX_TEXT + 1)
        )
        dispatcher.dispatch("ep", ProtoBuf.encodeToByteArray(MeshPayload.serializer(), bad))
        assertTrue(cb.received.isEmpty()); assertEquals(0, cb.seen.size)
    }

    @Test fun goodbyeFromAdmittedPeerDisconnectsAndIsNotRelayed() {
        val cb = RecordingDispatcherCallback().apply { admitted["ep"] = "A001" }
        val bye = MeshPayload(id = "g1", type = "GOODBYE", senderName = "Alice#A001")
        PayloadDispatcher(cb).dispatch("ep", ProtoBuf.encodeToByteArray(MeshPayload.serializer(), bye))
        assertEquals(listOf("ep"), cb.goodbyes)
        assertTrue(cb.broadcasts.isEmpty())
    }

    @Test fun goodbyeFromOtherIdentityOrUnadmittedLinkIsIgnored() {
        val cb = RecordingDispatcherCallback().apply { admitted["ep"] = "A001" }
        val dispatcher = PayloadDispatcher(cb)
        dispatcher.dispatch("ep", encode(MeshPayload(id = "g1", type = "GOODBYE", senderName = "Eve#E001")))
        dispatcher.dispatch("other", encode(MeshPayload(id = "g2", type = "GOODBYE", senderName = "Alice#A001")))
        assertTrue(cb.goodbyes.isEmpty()); assertTrue(cb.broadcasts.isEmpty())
    }
}
