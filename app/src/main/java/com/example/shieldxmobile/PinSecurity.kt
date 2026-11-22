package com.example.shieldxmobile

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Salted PBKDF2-HMAC-SHA256 helper for the local 4-digit app PIN.
 * A 4-digit PIN still has a small search space; the hash slows offline guessing,
 * but the PIN should not be treated as a replacement for device authentication.
 */
object PinSecurity {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BYTES = 32
    private const val SALT_LENGTH_BYTES = 16
    private const val HMAC_ALGORITHM = "HmacSHA256"

    fun createSalt(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return salt.toHex()
    }

    fun hashPin(pin: String, saltHex: String): String {
        require(pin.matches(Regex("^[0-9]{4}$"))) { "PIN must contain exactly four digits." }
        val salt = saltHex.hexToBytes()
        require(salt.size == SALT_LENGTH_BYTES) { "PIN salt is invalid." }

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(pin.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))

        // PBKDF2 block index 1, big-endian.
        val block = salt + byteArrayOf(0, 0, 0, 1)
        var u = mac.doFinal(block)
        val derived = u.copyOf()

        for (iteration in 2..ITERATIONS) {
            u = mac.doFinal(u)
            for (index in derived.indices) {
                derived[index] = (derived[index].toInt() xor u[index].toInt()).toByte()
            }
        }

        return derived.copyOf(KEY_LENGTH_BYTES).toHex()
    }

    fun verifyPin(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        if (!pin.matches(Regex("^[0-9]{4}$"))) return false
        return try {
            val actual = hashPin(pin, saltHex).hexToBytes()
            val expected = expectedHashHex.hexToBytes()
            MessageDigest.isEqual(actual, expected)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0 && all { it in "0123456789abcdefABCDEF" }) {
            "Invalid hexadecimal value."
        }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
