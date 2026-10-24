package org.videopocket.probe.processing

import org.junit.Assert.*
import org.junit.Test

class ProcessingPolicyTest {
    @Test fun keepsAspectRatioAndEvenDimensionsWithoutUpscaling() {
        assertEquals(1280 to 720, ProcessingPolicy.dimensions(1920, 1080, 1280))
        assertEquals(720 to 1280, ProcessingPolicy.dimensions(1080, 1920, 1280))
        assertEquals(640 to 360, ProcessingPolicy.dimensions(640, 361, 1920))
        assertThrows(IllegalArgumentException::class.java) { ProcessingPolicy.dimensions(0, 100, 1280) }
    }
    @Test fun onlyEnablesFormatsWithVerifiedEncodersAndMuxers() {
        val c = ProcessingCapabilities(setOf("libx264", "aac"), setOf("mp4", "ipod", "webm"))
        assertEquals(listOf("mp4"), c.formats(false))
        assertEquals(listOf("m4a"), c.formats(true))
        assertNull(c.videoEncoder("webm", CompressionPreset.BALANCED))
        assertEquals(emptyList<String>(), ProcessingCapabilities(emptySet(), setOf("mp4")).formats(false))
    }
    @Test fun compactPresetAdaptsToAvailableCodec() {
        val c = ProcessingCapabilities(setOf("libx264", "libx265"), setOf("mp4"))
        assertEquals("libx265", c.videoEncoder("mp4", CompressionPreset.SMALL_FILE))
        assertEquals("libx264", c.videoEncoder("mp4", CompressionPreset.HIGH_QUALITY))
        assertEquals("libx264", c.copy(encoders = setOf("libx264")).videoEncoder("mp4", CompressionPreset.SMALL_FILE))
    }
    @Test fun parsesRealProgressAndKeepsCompletionForPublication() {
        assertEquals(50f, ProcessingPolicy.progress("out_time_us=5000000", 10000000)!!, 0.01f)
        assertEquals(99f, ProcessingPolicy.progress("out_time_us=11000000", 10000000)!!, 0.01f)
        assertNull(ProcessingPolicy.progress("out_time_us=N/A", 100))
        assertNull(ProcessingPolicy.progress("out_time_us=-1", 100))
        assertNull(ProcessingPolicy.progress("frame=1", 100))
        assertNull(ProcessingPolicy.progress("out_time_us=1", 0))
    }
    @Test fun clipRangeMustStayInsideKnownSource() {
        val r = ProcessingRequest("content://test", "clip", ProcessingMode.TRIM, startSeconds = 3.25, endSeconds = 8.75)
        assertEquals(5.5, ProcessingPolicy.duration(r, 10.0), 0.001)
        listOf(r.copy(startSeconds = Double.NaN), r.copy(endSeconds = 11.0), r.copy(endSeconds = 2.0), r.copy(startSeconds = -1.0)).forEach {
            assertThrows(IllegalArgumentException::class.java) { ProcessingPolicy.duration(it, 10.0) }
        }
        assertThrows(IllegalArgumentException::class.java) { ProcessingPolicy.duration(r, Double.POSITIVE_INFINITY) }
    }
    @Test fun rejectsImpossibleSizeBudgetAndAccountsForAudio() {
        assertThrows(IllegalArgumentException::class.java) { ProcessingPolicy.videoBitrate(1024, 600.0, true) }
        val video = ProcessingPolicy.videoBitrate(10_000_000, 60.0, false)
        assertEquals(video - 128_000, ProcessingPolicy.videoBitrate(10_000_000, 60.0, true))
    }
    @Test fun audioFiltersConfiguredCorrectlyForVoiceClarifierAndSilenceStripper() {
        assertEquals("afftdn=nf=-25,dynaudnorm=f=150:g=15", ProcessingPolicy.audioFilter(ProcessingMode.VOICE_CLARIFY))
        assertEquals("silenceremove=stop_periods=-1:stop_duration=1:stop_threshold=-40dB", ProcessingPolicy.audioFilter(ProcessingMode.STRIP_SILENCE))
        assertNull(ProcessingPolicy.audioFilter(ProcessingMode.COMPRESS))
        assertNull(ProcessingPolicy.audioFilter(ProcessingMode.TRIM))
        assertNull(ProcessingPolicy.audioFilter(ProcessingMode.EXTRACT_AUDIO))
        assertNull(ProcessingPolicy.audioFilter(ProcessingMode.CONVERT))
    }

    @Test fun audioEnhancementModesPreserveSourceDurationInPlanner() {
        val clarifyReq = ProcessingRequest("content://test", "audio", ProcessingMode.VOICE_CLARIFY)
        assertEquals(120.0, ProcessingPolicy.duration(clarifyReq, 120.0), 0.001)

        val silenceReq = ProcessingRequest("content://test", "audio", ProcessingMode.STRIP_SILENCE)
        assertEquals(300.0, ProcessingPolicy.duration(silenceReq, 300.0), 0.001)
    }

    @Test fun parsesFfmpegCapabilitiesWithoutInventingAliases() {
        val encoders = ProcessingPolicy.parseCodecs(" V....D libx264 H.264\n A..... aac AAC\n ------\n")
        assertEquals(setOf("libx264", "aac"), encoders)
        assertEquals(setOf("matroska", "webm", "mp4"), ProcessingPolicy.parseMuxers("  E matroska,webm Matroska\n  E mp4 MP4\n"))
    }
}
