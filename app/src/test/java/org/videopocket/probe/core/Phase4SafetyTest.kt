package org.videopocket.probe.core

import org.junit.Assert.*
import org.junit.Test

class Phase4SafetyTest {
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
