package org.videopocket.probe.resolver

import org.junit.Assert.*
import org.junit.Test

class SubtitleFormatTest {
    @Test fun vttToSrtPreservesArabicTextAndActualTiming() {
        val result = SubtitleFormat.convert("WEBVTT\n\ncue-name\n00:01.250 --> 00:02.500 align:start\nمرحبا بالعالم\n", "srt")
        assertEquals("1\n00:00:01,250 --> 00:00:02,500\nمرحبا بالعالم\n", result)
    }
    @Test fun srtToVttUsesCorrectHeaderAndDecimalSeparators() {
        val result = SubtitleFormat.convert("1\r\n00:00:01,001 --> 00:00:02,005\r\nHello\r\n", "vtt")
        assertEquals("WEBVTT\n\n00:00:01.001 --> 00:00:02.005\nHello\n", result)
    }
    @Test fun rejectsUnsupportedResponsesAndInvalidTiming() {
        listOf("{\"events\":[]}", "<html>Sign in</html>", "00:00:03.000 --> 00:00:02.000\nBad").forEach {
            assertThrows(IllegalArgumentException::class.java) { SubtitleFormat.convert(it, "srt") }
        }
    }
    @Test fun textExportOmitsCueMetadataAndNotes() {
        assertEquals("Hello\nWorld", SubtitleFormat.convert("WEBVTT\n\nNOTE private note\nNot a cue\n\n00:00.000 --> 00:01.000\nHello\n\n00:01.000 --> 00:02.000\nWorld", "txt"))
    }
}
