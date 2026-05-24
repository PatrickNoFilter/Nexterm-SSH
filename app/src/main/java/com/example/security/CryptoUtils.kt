package com.example.security

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private val DEFAULT_SALT = "titan-ssh-salt-v1".toByteArray(StandardCharsets.UTF_8)

    // A hardcoded fallback secret key for seamless out-of-the-box local encryption.
    // If user specifies a custom master key, we use that for deriving the AES key instead.
    private fun deriveKey(passcode: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest((passcode + "titan_salt_secure").toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(hashedBytes, ALGORITHM)
    }

    /**
     * Encrypts plain text using AES-256 CBC with a secure random IV.
     * Returns a string consisting of base64(IV) + ":" + base64(ciphertext).
     */
    fun encrypt(plainText: String, keyPhrase: String = "titan_default_key"): String? {
        if (plainText.isEmpty()) return plainText
        return try {
            val keySpec = deriveKey(keyPhrase)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            
            val base64Iv = Base64.encodeToString(iv, Base64.NO_WRAP)
            val base64Cipher = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            "$base64Iv:$base64Cipher"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypts text previously encrypted with encrypt().
     */
    fun decrypt(encryptedPayload: String?, keyPhrase: String = "titan_default_key"): String? {
        if (encryptedPayload.isNullOrEmpty()) return encryptedPayload
        return try {
            val parts = encryptedPayload.split(":")
            if (parts.size != 2) return null // Old plain text or bad format
            
            val keySpec = deriveKey(keyPhrase)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
