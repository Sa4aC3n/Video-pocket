package org.videopocket.probe.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object VaultCryptoEngine {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "VideoPocketVaultMasterKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val BUFFER_SIZE = 64 * 1024

    private val secureRandom = SecureRandom()

    // Test secret key fallback when AndroidKeyStore is not present in runtime (e.g. JVM unit tests)
    @Volatile
    private var testSecretKey: SecretKey? = null

    fun setTestKey(secretKey: SecretKey?) {
        testSecretKey = secretKey
    }

    private fun getOrCreateKey(): SecretKey {
        testSecretKey?.let { return it }

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(false) // We supply our own cryptographically secure IV
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Throwable) {
            // JVM fallback for unit tests or environments where AndroidKeyStore is missing
            testSecretKey ?: synchronized(this) {
                testSecretKey ?: run {
                    val keyGen = KeyGenerator.getInstance("AES")
                    keyGen.init(256)
                    val k = keyGen.generateKey()
                    testSecretKey = k
                    k
                }
            }
        }
    }

    /**
     * Encrypts the input stream and writes:
     * [12 bytes IV] + [AES-GCM Ciphertext + 16 bytes Auth Tag]
     * to the output stream.
     * Returns total bytes written to the output stream.
     */
    fun encrypt(input: InputStream, output: OutputStream): Long {
        val key = getOrCreateKey()
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        // Write IV as header
        output.write(iv)
        var totalWritten = iv.size.toLong()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val cipherOut = CipherOutputStream(output, cipher)
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            cipherOut.write(buffer, 0, read)
            totalWritten += read
        }
        cipherOut.flush()
        // CipherOutputStream does not automatically account for tag expansion in written count,
        // but the tag is 16 bytes.
        return totalWritten + (GCM_TAG_LENGTH_BITS / 8)
    }

    /**
     * Reads [12 bytes IV] from the input stream and decrypts the remaining ciphertext.
     * Throws [java.security.GeneralSecurityException] if data was tampered with or key is invalid.
     * Returns total plaintext bytes written to output.
     */
    fun decrypt(input: InputStream, output: OutputStream): Long {
        val key = getOrCreateKey()
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        var ivBytesRead = 0
        while (ivBytesRead < GCM_IV_LENGTH_BYTES) {
            val r = input.read(iv, ivBytesRead, GCM_IV_LENGTH_BYTES - ivBytesRead)
            if (r == -1) throw IllegalArgumentException("Ciphertext too short: missing IV header")
            ivBytesRead += r
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val cipherIn = CipherInputStream(input, cipher)
        val buffer = ByteArray(BUFFER_SIZE)
        var totalDecrypted = 0L
        var read: Int
        while (cipherIn.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            totalDecrypted += read
        }
        output.flush()
        return totalDecrypted
    }
}
