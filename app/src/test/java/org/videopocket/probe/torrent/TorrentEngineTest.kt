package org.videopocket.probe.torrent

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class TorrentEngineTest {

    @Test
    fun testBase32ToHexConversion() {
        // Test standard Base32 info hash
        // "JBSWY3DPEBLW64TMMQ======" is "HelloWorld!!"
        val base32 = "JBSWY3DPEBLW64TMMQ"
        val hex = TorrentEngine.base32ToHex(base32)
        assertEquals("48656C6C6F576F726C642121", hex)

        // 32-character SHA-1 Base32 hash
        val b32Hash = "NNXG4ZZPOEQG64TMMQQQ222222222222"
        val converted = TorrentEngine.base32ToHex(b32Hash)
        assertEquals(40, converted.length)
    }

    @Test
    fun testHexToBytesAndUrlEncoded() {
        val hex = "3B10B054238E7D2A5789F2A9634F1EC5F7957388"
        val bytes = TorrentEngine.hexToBytes(hex)
        assertEquals(20, bytes.size)

        val encoded = TorrentEngine.hexToUrlEncoded(hex)
        assertTrue(encoded.isNotEmpty())
        assertTrue(encoded.contains("%"))
    }

    @Test
    fun testBencodeIntegerAndString() {
        val intData = "i42e".toByteArray(Charsets.US_ASCII)
        val decodedInt = BencodeParser.decode(intData)
        assertEquals(42L, decodedInt)

        val strData = "11:video_pocket".toByteArray(Charsets.US_ASCII)
        val decodedStr = BencodeParser.decode(strData)
        assertEquals("video_pocket", decodedStr)
    }

    @Test
    fun testBencodeListAndDictionary() {
        val listData = "l4:spam4:eggsi99ee".toByteArray(Charsets.US_ASCII)
        val decodedList = BencodeParser.decode(listData) as List<*>
        assertEquals(3, decodedList.size)
        assertEquals("spam", decodedList[0])
        assertEquals("eggs", decodedList[1])
        assertEquals(99L, decodedList[2])

        val dictData = "d3:cow3:moo4:spami42ee".toByteArray(Charsets.US_ASCII)
        val decodedDict = BencodeParser.decode(dictData) as Map<*, *>
        assertEquals("moo", decodedDict["cow"])
        assertEquals(42L, decodedDict["spam"])
    }

    @Test
    fun testExtractInfoDictionaryAndSha1() {
        // Construct a realistic synthetic torrent bytes
        val torrentContent = "d8:announce27:http://tracker.example.com/4:infod6:lengthi1048576e4:name8:test.mp412:piece lengthi262144e6:pieces20:12345678901234567890ee"
        val torrentBytes = torrentContent.toByteArray(Charsets.US_ASCII)

        val extracted = BencodeParser.extractInfoDictionaryBytes(torrentBytes)
        val extractedStr = String(extracted, Charsets.US_ASCII)
        assertTrue(extractedStr.startsWith("d6:lengthi1048576e"))
        assertTrue(extractedStr.endsWith("ee"))

        val sha1 = MessageDigest.getInstance("SHA-1").digest(extracted)
        val hex = sha1.joinToString("") { "%02X".format(it) }
        assertEquals(40, hex.length)
    }
}
