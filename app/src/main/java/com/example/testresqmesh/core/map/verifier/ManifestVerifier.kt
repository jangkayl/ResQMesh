package com.example.testresqmesh.core.map.verifier

import com.example.testresqmesh.core.utils.AppLogger
import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifier interface for cryptographic digital signatures.
 */
interface SignatureVerifier {
    fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
}

/**
 * Standard Ed25519 signature verifier using standard Java / Android Security providers.
 * Supported on Android 11+ (API 30+).
 */
class Ed25519SignatureVerifier : SignatureVerifier {
    companion object {
        // ASN.1 DER prefix for Ed25519 public keys (OID 1.3.101.112)
        private val ED25519_X509_HEADER = byteArrayOf(
            0x30.toByte(), 0x2a.toByte(), 0x30.toByte(), 0x05.toByte(),
            0x06.toByte(), 0x03.toByte(), 0x2b.toByte(), 0x65.toByte(),
            0x70.toByte(), 0x03.toByte(), 0x21.toByte(), 0x00.toByte()
        )
    }

    override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return try {
            val fullKeyBytes = if (publicKey.size == 32) {
                ED25519_X509_HEADER + publicKey
            } else {
                publicKey
            }
            val keySpec = X509EncodedKeySpec(fullKeyBytes)
            val keyFactory = KeyFactory.getInstance("Ed25519")
            val pubKey = keyFactory.generatePublic(keySpec)

            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(pubKey)
            sig.update(data)
            sig.verify(signature)
        } catch (e: Exception) {
            AppLogger.d("ManifestVerifier", "Ed25519 signature verification failed: ${e.message}")
            false
        }
    }
}

/**
 * Universal ECDSA (SHA256withECDSA, NIST P-256) verifier supported across ALL Android versions (API 14+).
 */
class EcdsaSignatureVerifier : SignatureVerifier {
    override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return try {
            val keySpec = X509EncodedKeySpec(publicKey)
            val keyFactory = KeyFactory.getInstance("EC")
            val pubKey = keyFactory.generatePublic(keySpec)

            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pubKey)
            sig.update(data)
            sig.verify(signature)
        } catch (e: Exception) {
            AppLogger.d("ManifestVerifier", "ECDSA signature verification failed: ${e.message}")
            false
        }
    }
}

/**
 * Composite verifier that seamlessly checks ECDSA (universal) first, followed by Ed25519.
 */
class CompositeSignatureVerifier(
    private val ecdsaVerifier: SignatureVerifier = EcdsaSignatureVerifier(),
    private val ed25519Verifier: SignatureVerifier = Ed25519SignatureVerifier()
) : SignatureVerifier {
    override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        if (ecdsaVerifier.verify(data, signature, publicKey)) return true
        return ed25519Verifier.verify(data, signature, publicKey)
    }
}

/**
 * Verifies package manifest signatures and package file integrity.
 */
class ManifestVerifier(
    private val signatureVerifier: SignatureVerifier = CompositeSignatureVerifier()
) {

    /**
     * Verifies that the manifest bytes match the digital signature using the specified public key.
     */
    fun verifyManifestSignature(
        manifestBytes: ByteArray,
        signatureBytes: ByteArray,
        publicKeyBytes: ByteArray
    ): Boolean {
        if (manifestBytes.isEmpty() || signatureBytes.isEmpty() || publicKeyBytes.isEmpty()) {
            return false
        }
        return signatureVerifier.verify(manifestBytes, signatureBytes, publicKeyBytes)
    }

    /**
     * Computes the SHA-256 hash of the package file and verifies it matches [expectedSha256Hex].
     */
    fun verifyPackageHash(packageFile: File, expectedSha256Hex: String): Boolean {
        if (!packageFile.exists() || !packageFile.isFile) {
            return false
        }
        val computedHash = computeSha256(packageFile) ?: return false
        return computedHash.equals(expectedSha256Hex.trim(), ignoreCase = true)
    }

    /**
     * Streams file contents through SHA-256 digest without loading entire file into memory.
     */
    fun computeSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            AppLogger.d("ManifestVerifier", "Failed to compute SHA-256 for file: ${e.message}")
            null
        }
    }
}
