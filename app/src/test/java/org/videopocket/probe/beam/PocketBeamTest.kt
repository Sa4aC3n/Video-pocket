package org.videopocket.probe.beam

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class PocketBeamTest {

    @Test
    fun testBeamSessionUriRoundTrip() {
        val original = BeamSession(
            sessionId = "session-123",
            fileName = "Quantum Physics Lecture.mp4",
            fileSizeBytes = 52_428_800L,
            mimeType = "video/mp4",
            ipAddress = "192.168.1.105",
            port = 8990,
            token = "ABCDEF12",
            sha256Checksum = "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"
        )

        val uriString = original.beamUriString
        val parsed = BeamSession.parseBeamUri(uriString)

        assertNotNull(parsed)
        assertEquals(original.ipAddress, parsed?.ipAddress)
        assertEquals(original.port, parsed?.port)
        assertEquals(original.token, parsed?.token)
        assertEquals(original.fileName, parsed?.fileName)
        assertEquals(original.fileSizeBytes, parsed?.fileSizeBytes)
        assertEquals(original.mimeType, parsed?.mimeType)
        assertEquals(original.sha256Checksum, parsed?.sha256Checksum)
    }

    @Test
    fun testHttpLandingUrlParsing() {
        val httpUrl = "http://10.0.0.15:8990/beam?token=XYZ999&name=AudioTrack.mp3&size=5242880&mime=audio%2Fmpeg"
        val parsed = BeamSession.parseBeamUri(httpUrl)

        assertNotNull(parsed)
        assertEquals("10.0.0.15", parsed?.ipAddress)
        assertEquals(8990, parsed?.port)
        assertEquals("XYZ999", parsed?.token)
        assertEquals("AudioTrack.mp3", parsed?.fileName)
        assertEquals(5242880L, parsed?.fileSizeBytes)
        assertEquals("audio/mpeg", parsed?.mimeType)
    }

    @Test
    fun testSha256HexFormatting() {
        val input = "Video Pocket: Ultra Fast P2P Transfer 2026".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input)
        val hex = BeamHashUtil.bytesToHex(hashBytes)

        assertEquals(64, hex.length)
        assertTrue(hex.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun testQrCodeGenerationProducesValidMatrix() {
        val qr = QrCodeEncoder.encodeText("http://192.168.1.1:8990/beam")

        assertTrue(qr.size >= 21) // QR Model 2 version 1 is 21x21, version 2 is 25x25
        // Top-left finder pattern center must be black
        assertTrue(qr.getModule(3, 3))
        // Top-right finder pattern center must be black
        assertTrue(qr.getModule(qr.size - 4, 3))
        // Bottom-left finder pattern center must be black
        assertTrue(qr.getModule(3, qr.size - 4))
    }
}
