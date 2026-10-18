package edu.cit.resqmesh.core.network.bluetooth

import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.MeshPayload
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

/** Key/claim consistency only; this does not prove possession or first-contact authenticity. */
object DirectIdentityPolicy {
    fun validate(payload: MeshPayload, decode: (String) -> ByteArray): String? = runCatching {
        if (payload.type != "SYSTEM" || payload.routePath.isNotEmpty() || payload.relayHopCount != 0 ||
            payload.isPrivate || payload.isEncrypted || payload.publicKey.length !in 1..4096 ||
            NodeIdentity.isPlaceholder(payload.senderName) || payload.identityProtocol !in 0..LinkIdentityAdmission.VERSION) return null
        if (payload.identityExchangeId.isNotEmpty() &&
            (payload.identityProtocol != LinkIdentityAdmission.VERSION ||
                runCatching { UUID.fromString(payload.identityExchangeId).toString() == payload.identityExchangeId.lowercase() }.getOrDefault(false).not())) return null
        val nameId = NodeIdentity.idOf(payload.senderName) ?: return null
        val claimed = payload.senderNodeId.ifBlank { nameId }.uppercase()
        if (!claimed.matches(Regex("[0-9A-F]{4}")) || claimed != nameId) return null
        claimed.takeIf { keyMatchesNodeId(claimed, payload.publicKey, decode) }
    }.getOrNull()

    /** RSA >= 2048 whose SHA-256 prefix equals [nodeId]; never throws. */
    fun keyMatchesNodeId(nodeId: String, publicKey: String, decode: (String) -> ByteArray): Boolean = runCatching {
        if (publicKey.length !in 1..4096) return@runCatching false
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decode(publicKey))) as? RSAPublicKey
            ?: return@runCatching false
        if (key.modulus.bitLength() < 2048) return@runCatching false
        val derived = MessageDigest.getInstance("SHA-256").digest(key.encoded).take(2).joinToString("") { "%02X".format(it) }
        nodeId.uppercase() == derived
    }.getOrDefault(false)

    fun isBootstrap(payload: MeshPayload) =
        (payload.type == "SYSTEM" && payload.routePath.isEmpty() && payload.relayHopCount == 0) || payload.type == LinkIdentityAdmission.ACK

    fun allowsFrame(payload: MeshPayload, admitted: Boolean, blocked: Boolean): Boolean {
        if (isBootstrap(payload)) return true
        if (!admitted) return false
        if (!blocked) return true
        // Existing block policy persists denial before sending the encrypted teardown exchange.
        return payload.type in setOf("BLOCK_REQUEST", "BLOCK_ACK") && payload.isPrivate && payload.isEncrypted &&
            !payload.encryptedData.isNullOrBlank() && !payload.encryptedKey.isNullOrBlank()
    }
}
