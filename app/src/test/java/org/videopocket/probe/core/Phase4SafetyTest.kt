package org.videopocket.probe.core

import org.junit.Assert.*
import org.junit.Test

class Phase4SafetyTest {
    @Test fun privateAndAmbiguousIpLiteralsCannotBePublicSources() {
        listOf("http://127.0.0.1/a", "http://2130706433/a", "http://0177.0.0.1/a", "http://10.0.0.1/a",
            "http://192.168.1.1/a", "http://[::ffff:127.0.0.1]/a", "http://[fd00::1]/a").forEach {
            assertThrows(it, ProbeFailure::class.java) { LinkPolicy.validate(it) }
        }
        assertEquals("https://93.184.216.34/video", LinkPolicy.validate("https://93.184.216.34/video"))
    }

    @Test fun incompatibleNativePageSizesAreRejectedBeforeInitialization() {
        assertTrue(NativeRuntimePolicy.supportsPageSize(4096))
        assertFalse(NativeRuntimePolicy.supportsPageSize(16384))
        assertFalse(NativeRuntimePolicy.supportsPageSize(65536))
        assertFalse(NativeRuntimePolicy.supportsPageSize(-1))
    }

    @Test fun unknownHeightCannotPretendToBeRequestedResolution() {
        val info = MediaInfo("unknown", null, listOf(MediaFormat("x", "mp4", 0, true, true, null)))
        assertEquals(0, info.defaultHeight)
        assertTrue(info.heights.isEmpty())
        assertThrows(ProbeFailure::class.java) { FormatPolicy.select(info, ProbeAction.VIDEO, 1080) }
        assertEquals("x", FormatPolicy.select(info, ProbeAction.VIDEO, 0))
    }

    @Test fun realEightKIsNotHidden() {
        val info = MediaInfo("8k", null, listOf(MediaFormat("8k", "webm", 4320, true, false, null)))
        assertEquals(listOf(4320), info.heights)
    }

    @Test fun nonFiniteNegativeAndMalformedTimesAreRejected() {
        listOf("NaN", "Infinity", "-1", "1:60", "1:-2", "1.5:20", "1:2:3:4").forEach {
            assertNull(it, ClipRequest.parseTimestamp(it))
        }
        assertEquals(62.5, ClipRequest.parseTimestamp("1:02.5")!!, 0.001)
    }
}
