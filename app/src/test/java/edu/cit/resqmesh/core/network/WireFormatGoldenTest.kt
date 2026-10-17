package edu.cit.resqmesh.core.network

import edu.cit.resqmesh.core.network.bluetooth.state.MeshFrameCodec
import edu.cit.resqmesh.data.repository.SosEvent
import edu.cit.resqmesh.data.repository.SosProtocol
import edu.cit.resqmesh.data.repository.SosSyncPacket
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Freezes the bytes that cross BLE and the on-disk transfer journal so package moves or
 * renames cannot silently change the wire. Update a golden only for a deliberate protocol change.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class WireFormatGoldenTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun <T> check(serializer: KSerializer<T>, sample: T, golden: String) {
        val encoded = ProtoBuf.encodeToByteArray(serializer, sample)
        assertEquals(golden, hex(encoded))
        val decoded = ProtoBuf.decodeFromByteArray(serializer, unhex(golden))
        assertEquals(golden, hex(ProtoBuf.encodeToByteArray(serializer, decoded)))
    }

    @Test fun meshPayloadMinimalMessage() {
        val sample = MeshPayload(id = "msg-1", type = "MESSAGE", senderName = "Alice", targetName = "Bob", text = "hello",
            senderNodeId = "node-a", targetNodeId = "node-b", ttl = 5, createdAt = 1_700_000_000_000L)
        check(MeshPayload.serializer(), sample, GOLDEN_MESSAGE)
        val decoded = ProtoBuf.decodeFromByteArray(MeshPayload.serializer(), unhex(GOLDEN_MESSAGE))
        assertEquals("Alice", decoded.senderName)
        assertEquals("node-b", decoded.targetNodeId)
        assertEquals(5, decoded.ttl)
        assertEquals(1_700_000_000_000L, decoded.createdAt)
    }

    @Test fun meshPayloadFullyPopulated() {
        val sample = MeshPayload(id = "id-9", type = "SOS", senderName = "S", targetName = "T", text = "help",
            imageBytes = byteArrayOf(1, 2), audioBytes = byteArrayOf(3), isPrivate = true, isEncrypted = true,
            encryptedData = "ed", encryptedKey = "ek", locationLat = 14.5, locationLng = 121.0,
            routePath = listOf("a", "b"), directedRoute = listOf("c"), returnRoute = listOf("d"),
            isSOS = true, channelId = "2", isSOSCancel = true, publicKey = "pk",
            connectedNodes = listOf("n1"), targetMessageId = "tm", reader = "r", liveAudioChunk = byteArrayOf(9),
            senderNodeId = "sn", targetNodeId = "tn", connectedNodeIds = listOf("c1"),
            directedRouteNodeIds = listOf("d1"), returnRouteNodeIds = listOf("r1"), relayHopCount = 2, ttl = 7,
            topologySequence = 42L, conversationKind = "PRIVATE", sosId = "SOS-1", createdAt = 1_700_000_000_123L,
            identityProtocol = 1, identityExchangeId = "ix", transferProtocol = 1)
        check(MeshPayload.serializer(), sample, GOLDEN_FULL)
    }

    @Test fun transferPiece() {
        val sample = TransferPiece("t1", "dg", 1024, 3, byteArrayOf(1, 2, 3), complete = true, priority = true,
            reset = false, status = true, bitmap = byteArrayOf(5), requestId = "rq")
        check(TransferPiece.serializer(), sample, GOLDEN_PIECE)
    }

    @Test fun transferRecord() {
        val sample = TransferRecord("peer", "tid", "dg", 2048, true, "pid", "mid", true, 99_000L,
            durableRelay = true, complete = true, stored = true, response = byteArrayOf(7),
            relayTargetsSet = true, relayTargets = listOf("x"), forwardedPeers = listOf("y"),
            applicationStored = true, requiresRelayRoster = true)
        check(TransferRecord.serializer(), sample, GOLDEN_RECORD)
    }

    @Test fun sosSyncPacket() {
        val sample = SosSyncPacket("DIGEST", digest = "dd", cursor = 1, next = 2, events = listOf(sosEvent()), complete = true)
        check(SosSyncPacket.serializer(), sample, GOLDEN_SYNC)
    }

    @Test fun sosEventJson() {
        assertEquals(GOLDEN_SOS_JSON, SosProtocol.json.encodeToString(sosEvent()))
        assertEquals(sosEvent(), SosProtocol.json.decodeFromString<SosEvent>(GOLDEN_SOS_JSON))
    }

    @Test fun meshFrameLengthPrefix() {
        val frame = MeshFrameCodec.encode(byteArrayOf(0x0a, 0x0b, 0x0c))!!
        assertEquals("000000030a0b0c", hex(frame))
        val result = MeshFrameCodec.append(ByteArray(0), unhex("000000030a0b0c"))
        val accepted = result as MeshFrameCodec.AppendResult.Accepted
        assertEquals(1, accepted.payloads.size)
        assertEquals("0a0b0c", hex(accepted.payloads[0]))
        assertEquals(0, accepted.remainder.size)
    }

    private fun sosEvent() = SosEvent("ev-1", "SOS-abc", "node-1", "Alice", "key", "MEDICAL", 2L, false,
        1_700_000_000_000L, 1_700_000_001_000L, 14.5, 121.0, 5.0f, 1_700_000_000_500L, "sig")

    private companion object {
        const val GOLDEN_MESSAGE =
            "0a056d73672d311a05416c6963652203426f622a0568656c6c6fca01066e6f64652d61d201066e6f64652d62f80105980280d095ffbc31"
        const val GOLDEN_FULL =
            "0a0469642d391203534f531a01532201542a0468656c70320201023a010340014801520265645a02656b610000000000002d40690000000000405e407201617201627a01638201016488010192010132980101a20102706baa01026e31b20102746dba010172c2010109ca0102736ed20102746eda01026331e201026431ea01027231f00102f8010780022a8a020750524956415445920205534f532d319802fbd095ffbc31a00201aa02026978b00201"
        const val GOLDEN_PIECE =
            "0a0274311202646718800820032a030102033001380148015201055a027271"
        const val GOLDEN_RECORD =
            "0a047065657212037469641a026467208010280132037069643a036d6964400148b885065001580160016a010770017a017882010179880101900101"
        const val GOLDEN_SYNC =
            "0a0644494745535412026464180120022a610a0465762d311207534f532d6162631a066e6f64652d312205416c6963652a036b657932074d45444943414c380240004880d095ffbc3150e8d795ffbc31590000000000002d40610000000000405e406d0000a04070f4d395ffbc317a037369673001"
        const val GOLDEN_SOS_JSON =
            "{\"eventId\":\"ev-1\",\"sosId\":\"SOS-abc\",\"originNodeId\":\"node-1\",\"originName\":\"Alice\",\"signingKey\":\"key\",\"emergencyType\":\"MEDICAL\",\"revision\":2,\"ended\":false,\"createdAt\":1700000000000,\"updatedAt\":1700000001000,\"latitude\":14.5,\"longitude\":121.0,\"accuracyMeters\":5.0,\"locationCapturedAt\":1700000000500,\"signature\":\"sig\"}"
    }
}
