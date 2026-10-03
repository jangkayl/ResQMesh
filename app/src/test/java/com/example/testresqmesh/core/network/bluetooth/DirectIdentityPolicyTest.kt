package com.example.testresqmesh.core.network.bluetooth

import com.example.testresqmesh.core.network.MeshPayload
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class DirectIdentityPolicyTest {
    private val valid get() = MeshPayload(type = "SYSTEM", senderName = "Peer#$id", senderNodeId = id,
        publicKey = Base64.getEncoder().encodeToString(key.public.encoded), identityProtocol = 1)
    private fun validate(payload: MeshPayload) = DirectIdentityPolicy.validate(payload) { Base64.getDecoder().decode(it) }

    @Test fun validKeyIdentityAndLegacyEnvelopeAreAccepted() {
        assertEquals(id, validate(valid))
        assertEquals(id, validate(valid.copy(senderNodeId = "", identityProtocol = 0)))
    }
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test fun identityMetadataRoundTripsAndAbsentWireFieldsUseLegacyDefaults() {
        // A legacy protobuf envelope with only field 2 (type = SYSTEM).
        val legacy = ProtoBuf.decodeFromByteArray<MeshPayload>(byteArrayOf(0x12, 0x06) + "SYSTEM".toByteArray())
        assertEquals("SYSTEM", legacy.type)
        assertEquals(0, legacy.identityProtocol)
        assertEquals("", legacy.identityExchangeId)

        val payload = valid.copy(identityExchangeId = "12345678-1234-1234-1234-123456789abc")
        val decoded = ProtoBuf.decodeFromByteArray<MeshPayload>(ProtoBuf.encodeToByteArray(payload))
        assertEquals(payload.identityProtocol, decoded.identityProtocol)
        assertEquals(payload.identityExchangeId, decoded.identityExchangeId)
        assertEquals(id, validate(decoded))
    }
    @Test fun inconsistentClaimMalformedKeyAndRelayedIdentityAreRejected() {
        val other = if (id == "FFFF") "0000" else "FFFF"
        assertNull(validate(valid.copy(senderNodeId = other)))
        assertNull(validate(valid.copy(senderName = "Peer#$other", senderNodeId = other)))
        assertNull(validate(valid.copy(publicKey = "not a key")))
        assertNull(validate(valid.copy(routePath = listOf("Relay#0000"))))
        assertNull(validate(valid.copy(relayHopCount = 1)))
        assertNull(validate(valid.copy(identityProtocol = 2)))
        assertNull(validate(valid.copy(identityExchangeId = "invalid")))
    }
    @Test fun configuredLinksAllowOnlyDirectIdentityAndItsAcknowledgement() {
        assertTrue(DirectIdentityPolicy.isBootstrap(valid))
        assertTrue(DirectIdentityPolicy.isBootstrap(MeshPayload(type = LinkIdentityAdmission.ACK)))
        for (type in listOf("MESSAGE", "CONVERSATION", "SEEN", "DELIVERED", "SOS_EVENT", "TRANSFER_PIECE", "PING")) {
            assertFalse(DirectIdentityPolicy.isBootstrap(MeshPayload(type = type)))
        }
        assertFalse(DirectIdentityPolicy.isBootstrap(valid.copy(routePath = listOf("Relay"))))
        assertFalse(DirectIdentityPolicy.isBootstrap(valid.copy(relayHopCount = 1)))
    }
    @Test fun deniedAdmittedLinkAllowsOnlyEncryptedBlockTeardownTraffic() {
        for (type in listOf("BLOCK_REQUEST", "BLOCK_ACK")) {
            val sealed = MeshPayload(type = type, isPrivate = true, isEncrypted = true, encryptedData = "sealed", encryptedKey = "wrapped")
            assertTrue(DirectIdentityPolicy.allowsFrame(sealed, admitted = true, blocked = true))
            assertFalse(DirectIdentityPolicy.allowsFrame(sealed, admitted = false, blocked = true))
            assertFalse(DirectIdentityPolicy.allowsFrame(sealed.copy(isEncrypted = false), admitted = true, blocked = true))
            assertFalse(DirectIdentityPolicy.allowsFrame(sealed.copy(encryptedKey = ""), admitted = true, blocked = true))
        }
        val message = MeshPayload(type = "MESSAGE")
        assertFalse(DirectIdentityPolicy.allowsFrame(message, admitted = true, blocked = true))
        assertFalse(DirectIdentityPolicy.allowsFrame(message, admitted = false, blocked = false))
        assertTrue(DirectIdentityPolicy.allowsFrame(message, admitted = true, blocked = false))
    }
    companion object {
        private val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val id = MessageDigest.getInstance("SHA-256").digest(key.public.encoded).take(2).joinToString("") { "%02X".format(it) }
    }
}
