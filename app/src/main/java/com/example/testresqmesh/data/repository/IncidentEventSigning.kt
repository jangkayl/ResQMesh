package com.example.testresqmesh.data.repository

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/** Verifies continuity of a device-held incident identity, not a person's credentials. */
interface IncidentEventSigning {
    val publicKey: String
    fun sign(event: DomainEventEntity): String
    fun verify(event: DomainEventEntity, publicKey: String): Boolean
}

class KeystoreIncidentEventSigning : IncidentEventSigning {
    private val pair: KeyPair by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(ALIAS, null) as? java.security.PrivateKey
        val certificate = store.getCertificate(ALIAS)
        if (key != null && certificate != null) KeyPair(certificate.publicKey, key)
        else KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build())
        }.generateKeyPair()
    }

    override val publicKey: String
        get() = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)

    override fun sign(event: DomainEventEntity): String = Base64.encodeToString(
        Signature.getInstance("SHA256withECDSA").apply {
            initSign(pair.private)
            update(canonicalEventBytes(event))
        }.sign(), Base64.NO_WRAP
    )

    override fun verify(event: DomainEventEntity, publicKey: String): Boolean = runCatching {
        val key = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.decode(publicKey, Base64.NO_WRAP))
        )
        Signature.getInstance("SHA256withECDSA").apply {
            initVerify(key)
            update(canonicalEventBytes(event))
        }.verify(Base64.decode(event.signature ?: return false, Base64.NO_WRAP))
    }.getOrDefault(false)

    companion object {
        private const val ALIAS = "resqmesh_incident_signing_p256_v1"

        fun fingerprint(publicKey: String): String = runCatching {
            MessageDigest.getInstance("SHA-256")
                .digest(Base64.decode(publicKey, Base64.NO_WRAP))
                .joinToString("") { "%02x".format(it) }
        }.getOrDefault("")

        private fun canonicalEventBytes(event: DomainEventEntity): ByteArray {
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use { data ->
                listOf(event.eventId, event.entityId, event.entityType, event.eventType,
                    event.actorId, event.actorName, event.payloadJson).forEach { value ->
                    val bytes = value.toByteArray(Charsets.UTF_8)
                    data.writeInt(bytes.size)
                    data.write(bytes)
                }
                data.writeLong(event.logicalVersion)
                data.writeLong(event.timestamp)
            }
            return out.toByteArray()
        }
    }
}
