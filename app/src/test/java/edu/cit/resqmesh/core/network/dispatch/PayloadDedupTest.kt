package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.PayloadDispatcher
import edu.cit.resqmesh.core.network.dedup.ExpiringIdCache
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class PayloadDedupTest {
    private val cb = RecordingDispatcherCallback()
    private val dispatcher = PayloadDispatcher(cb)

    private fun send(p: MeshPayload) =
        dispatcher.dispatch("ep", ProtoBuf.encodeToByteArray(MeshPayload.serializer(), p))
    private fun chat(id: String) = MeshPayload(id = id, type = "MESSAGE", senderName = "Alice#A001", text = "hi")

    @Test fun duplicateIdIsDroppedUntilExpiry() {
        var now = 0L
        cb.seen = ExpiringIdCache(maxEntries = 4096, ttlMs = 1000, clock = { now })
        send(chat("m1")); send(chat("m1"))
        assertEquals(listOf("m1"), cb.received)
        now += 1000
        send(chat("m1"))
        assertEquals(listOf("m1", "m1"), cb.received)
    }

    @Test fun idRecordedAsOutgoingIsNotProcessedAgain() {
        cb.seen.markSeen("echo")
        send(chat("echo"))
        assertTrue(cb.received.isEmpty())
    }

    @Test fun sosAndConversationBypassDedup() {
        val sos = MeshPayload(id = "s1", type = "SOS_EVENT", senderName = "Alice#A001")
        val conv = MeshPayload(id = "c1", type = "CONVERSATION", senderName = "Alice#A001")
        repeat(2) { send(sos); send(conv) }
        assertEquals(2, cb.sosPackets.size); assertEquals(2, cb.conversations.size)
    }

    @Test fun liveAudioFromOlderBuildIsDroppedNotStoredOrRelayed() {
        send(MeshPayload(id = "l1", type = "LIVE_AUDIO", senderName = "Alice#A001", liveAudioChunk = byteArrayOf(1)))
        assertTrue(cb.received.isEmpty())
        assertTrue(cb.broadcasts.isEmpty())
    }

    @Test fun capEvictsOldestIdsOnly() {
        cb.seen = ExpiringIdCache(maxEntries = 2, ttlMs = 1000, clock = { 0L })
        listOf("a", "b", "c").forEach { send(chat(it)) }
        send(chat("c")); send(chat("a"))
        assertEquals(listOf("a", "b", "c", "a"), cb.received)
    }
}
