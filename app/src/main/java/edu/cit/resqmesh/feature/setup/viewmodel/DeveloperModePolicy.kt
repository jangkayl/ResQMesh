package edu.cit.resqmesh.feature.setup.viewmodel

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Developer mode is available only when the build carries a PIN hash (see `resqmesh.devPin` in
 * local.properties, 8+ ASCII characters in release; debug builds default to 0000).
 * Without one, a stored flag is ignored.
 */
object DeveloperModePolicy {
    fun isConfigured(pinHash: String): Boolean = pinHash.isNotEmpty()

    /** The stored flag also records the hash it was granted under, so rotating the PIN turns it off. */
    fun isEnabled(pinHash: String, storedFlag: Boolean, storedHash: String?): Boolean =
        isConfigured(pinHash) && storedFlag && storedHash == pinHash

    /** Slow by design (PBKDF2): call off the main thread. */
    fun acceptsPin(pinHash: String, salt: String, pin: String?): Boolean {
        if (!isConfigured(pinHash) || pin == null || !isAscii(pin)) return false
        return MessageDigest.isEqual(hash(salt, pin).toByteArray(Charsets.UTF_8), pinHash.toByteArray(Charsets.UTF_8))
    }

    fun isAscii(s: String): Boolean = s.all { it.code in 0..ASCII_MAX }

    /**
     * Must match `devPinHash` in app/build.gradle.kts: lowercase hex PBKDF2WithHmacSHA1 (API 24+),
     * 150,000 iterations, 160 bits. ASCII only, so both sides derive identical bytes.
     */
    fun hash(salt: String, pin: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt.toByteArray(Charsets.US_ASCII), ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            .joinToString("") { "%02x".format(it) }
    }

    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 160
    private const val ASCII_MAX = 127
}
