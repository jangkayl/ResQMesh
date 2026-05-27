package com.example.testresqmesh.core.network

import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private val keyPair: KeyPair
    private const val RSA_ALGO = "RSA/ECB/PKCS1Padding"
    private const val AES_ALGO = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    init {
        // Generate Ephemeral RSA 2048-bit Key Pair on startup
        // Ephemeral keys guarantee Forward Secrecy and Plausible Deniability.
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        keyPair = generator.generateKeyPair()
    }

    fun getMyPublicKeyBase64(): String {
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    private fun getPublicKeyFromString(base64PublicKey: String): PublicKey {
        val byteKey = Base64.decode(base64PublicKey.toByteArray(), Base64.DEFAULT)
        val X509publicKey = X509EncodedKeySpec(byteKey)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePublic(X509publicKey)
    }

    fun encryptHybrid(payloadString: String, targetPublicKeyBase64: String): Pair<String, String>? {
        try {
            // 1. Generate one-time 256-bit AES key
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val aesKey: SecretKey = keyGen.generateKey()

            // 2. Encrypt the payload with AES GCM
            val aesCipher = Cipher.getInstance(AES_ALGO)
            val iv = ByteArray(GCM_IV_LENGTH)
            java.security.SecureRandom().nextBytes(iv)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
            
            val encryptedPayload = aesCipher.doFinal(payloadString.toByteArray(Charsets.UTF_8))
            
            // Prepend IV to the encrypted payload for extraction during decryption
            val payloadWithIv = ByteArray(GCM_IV_LENGTH + encryptedPayload.size)
            System.arraycopy(iv, 0, payloadWithIv, 0, GCM_IV_LENGTH)
            System.arraycopy(encryptedPayload, 0, payloadWithIv, GCM_IV_LENGTH, encryptedPayload.size)

            val base64Payload = Base64.encodeToString(payloadWithIv, Base64.NO_WRAP)

            // 3. Encrypt the AES key with the Target's RSA Public Key
            val targetPubKey = getPublicKeyFromString(targetPublicKeyBase64)
            val rsaCipher = Cipher.getInstance(RSA_ALGO)
            rsaCipher.init(Cipher.ENCRYPT_MODE, targetPubKey)
            val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)
            val base64EncryptedAesKey = Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP)

            return Pair(base64Payload, base64EncryptedAesKey)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun decryptHybrid(base64PayloadWithIv: String, base64EncryptedAesKey: String): String? {
        try {
            // 1. Decrypt the AES Key using OUR RSA Private Key
            val rsaCipher = Cipher.getInstance(RSA_ALGO)
            rsaCipher.init(Cipher.DECRYPT_MODE, keyPair.private)
            val decryptedAesBytes = rsaCipher.doFinal(Base64.decode(base64EncryptedAesKey, Base64.DEFAULT))
            val aesKey = SecretKeySpec(decryptedAesBytes, 0, decryptedAesBytes.size, "AES")

            // 2. Extract IV and encrypted payload
            val payloadWithIv = Base64.decode(base64PayloadWithIv, Base64.DEFAULT)
            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(payloadWithIv, 0, iv, 0, GCM_IV_LENGTH)
            val encryptedPayload = ByteArray(payloadWithIv.size - GCM_IV_LENGTH)
            System.arraycopy(payloadWithIv, GCM_IV_LENGTH, encryptedPayload, 0, encryptedPayload.size)

            // 3. Decrypt the payload using the unlocked AES key
            val aesCipher = Cipher.getInstance(AES_ALGO)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
            val decryptedPayloadBytes = aesCipher.doFinal(encryptedPayload)

            return String(decryptedPayloadBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
