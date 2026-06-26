package com.wahyuzero.replyforge.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM encryption utility for sensitive fields stored in Room DB.
 *
 * Uses PBKDF2-derived key (not Keystore) for broad device compatibility.
 * Each encryption generates a random 12-byte IV prepended to ciphertext.
 * Backward compatible: plaintext values without "enc:" prefix pass through decrypt() unchanged.
 *
 * Trade-off: Not as secure as Android Keystore, but prevents casual DB inspection.
 */
object CryptoUtils {

    private const val ENCRYPT_PREFIX = "enc:"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val KEY_ALGORITHM = "AES"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KDF_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256

    // Fixed salt + password for key derivation (not ideal, but acceptable for personal-use app)
    private val SALT = byteArrayOf(0x52, 0x46, 0x6F, 0x72, 0x67, 0x65, 0x53, 0x61)
    private val PASSPHRASE = "r3plyF0rg3_k3y_d3r1v4t10n_2024".toCharArray()

    private val secretKey: SecretKey by lazy {
        val spec = PBEKeySpec(PASSPHRASE, SALT, KDF_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        SecretKeySpec(factory.generateSecret(spec).encoded, KEY_ALGORITHM)
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        if (plainText.startsWith(ENCRYPT_PREFIX)) return plainText // already encrypted

        return try {
            val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Combine IV + cipherText, base64 encode, add prefix
            val combined = iv + cipherText
            ENCRYPT_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // If encryption fails, return original — better than crashing
            plainText
        }
    }

    fun decrypt(storedValue: String): String {
        if (storedValue.isEmpty()) return storedValue
        if (!storedValue.startsWith(ENCRYPT_PREFIX)) return storedValue // plaintext (backward compat)

        return try {
            val data = Base64.decode(storedValue.removePrefix(ENCRYPT_PREFIX), Base64.NO_WRAP)
            if (data.size <= IV_LENGTH) return storedValue

            val iv = data.copyOfRange(0, IV_LENGTH)
            val cipherText = data.copyOfRange(IV_LENGTH, data.size)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails (corrupted data, wrong key), return as-is
            storedValue
        }
    }
}
