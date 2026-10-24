package org.videopocket.probe.vault

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.util.Random

class VaultCryptoEngineTest {

    @Test
    fun encryptionAndDecryptionRoundtripPreservesExactBytes() {
        val originalText = "Video Pocket: Top Secret AES-256-GCM Encrypted Video File. مرحبا بالعالم العربي!"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        val encryptedStream = ByteArrayOutputStream()
        val writtenEncrypted = VaultCryptoEngine.encrypt(ByteArrayInputStream(originalBytes), encryptedStream)
        val ciphertext = encryptedStream.toByteArray()

        assertTrue(ciphertext.size > originalBytes.size)
        assertTrue(writtenEncrypted > 0)

        val decryptedStream = ByteArrayOutputStream()
        val decryptedCount = VaultCryptoEngine.decrypt(ByteArrayInputStream(ciphertext), decryptedStream)
        val decryptedText = decryptedStream.toString(Charsets.UTF_8.name())

        assertEquals(originalBytes.size.toLong(), decryptedCount)
        assertEquals(originalText, decryptedText)
    }

    @Test
    fun largePayloadStreamRoundtrip() {
        val random = Random(42)
        val largeBytes = ByteArray(128 * 1024)
        random.nextBytes(largeBytes)

        val encryptedOut = ByteArrayOutputStream()
        VaultCryptoEngine.encrypt(ByteArrayInputStream(largeBytes), encryptedOut)
        val ciphertext = encryptedOut.toByteArray()

        val decryptedOut = ByteArrayOutputStream()
        VaultCryptoEngine.decrypt(ByteArrayInputStream(ciphertext), decryptedOut)

        assertArrayEquals(largeBytes, decryptedOut.toByteArray())
    }

    @Test
    fun tamperedCiphertextFailsAuthentication() {
        val originalBytes = "Sensitive Confidential Video Content".toByteArray()
        val encOut = ByteArrayOutputStream()
        VaultCryptoEngine.encrypt(ByteArrayInputStream(originalBytes), encOut)
        val ciphertext = encOut.toByteArray()

        // Flip a byte in the middle of ciphertext
        val tampered = ciphertext.clone()
        tampered[tampered.size / 2] = (tampered[tampered.size / 2].toInt() xor 0xFF).toByte()

        val decOut = ByteArrayOutputStream()
        assertThrows(Exception::class.java) {
            VaultCryptoEngine.decrypt(ByteArrayInputStream(tampered), decOut)
        }
    }

    @Test
    fun uniqueIvPerEncryptionProducesDifferentCiphertexts() {
        val plaintext = "Same Plaintext Message".toByteArray()

        val enc1 = ByteArrayOutputStream()
        VaultCryptoEngine.encrypt(ByteArrayInputStream(plaintext), enc1)

        val enc2 = ByteArrayOutputStream()
        VaultCryptoEngine.encrypt(ByteArrayInputStream(plaintext), enc2)

        val c1 = enc1.toByteArray()
        val c2 = enc2.toByteArray()

        // IVs (first 12 bytes) must not match
        assertFalse(c1.take(12) == c2.take(12))
        // Full ciphertexts must not match
        assertFalse(c1.contentEquals(c2))
    }

    @Test
    fun vaultItemJsonSerializationRoundtrip() {
        val item = VaultItem(
            id = "test-uuid-1234",
            title = "Family Vacation 2026",
            extension = "mp4",
            mimeType = "video/mp4",
            originalBytes = 52_428_800L,
            durationMs = 185_000L,
            encryptedAt = 1770000000000L,
            encryptedFileName = "enc_test-uuid-1234.bin"
        )

        val json = item.toJson()
        val restored = VaultItem.fromJson(json)

        assertEquals(item.id, restored.id)
        assertEquals(item.title, restored.title)
        assertEquals(item.extension, restored.extension)
        assertEquals(item.mimeType, restored.mimeType)
        assertEquals(item.originalBytes, restored.originalBytes)
        assertEquals(item.durationMs, restored.durationMs)
        assertEquals(item.encryptedAt, restored.encryptedAt)
        assertEquals(item.encryptedFileName, restored.encryptedFileName)
    }
}
