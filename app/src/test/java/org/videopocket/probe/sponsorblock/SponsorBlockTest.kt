package org.videopocket.probe.sponsorblock

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SponsorBlockTest {

    @Test
    fun extractVideoIdHandlesAllYouTubeFormats() {
        // Standard watch URL
        assertEquals("dQw4w9WgXcQ", SponsorBlockClient.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", SponsorBlockClient.extractVideoId("http://youtube.com/watch?v=dQw4w9WgXcQ&t=42s"))

        // Short link
        assertEquals("dQw4w9WgXcQ", SponsorBlockClient.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", SponsorBlockClient.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=10"))

        // Shorts URL
        assertEquals("dQw4w9WgXcQ", SponsorBlockClient.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))

        // Embed URL
        assertEquals("dQw4w9WgXcQ", SponsorBlockClient.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))

        // Direct standalone 11-char ID
        assertEquals("dQw4w9WgXcQ", SponsorBlockClient.extractVideoId("dQw4w9WgXcQ"))

        // Non-YouTube and invalid inputs
        assertNull(SponsorBlockClient.extractVideoId("https://soundcloud.com/artist/track"))
        assertNull(SponsorBlockClient.extractVideoId("https://example.com/video.mp4"))
        assertNull(SponsorBlockClient.extractVideoId(""))
        assertNull(SponsorBlockClient.extractVideoId(null))
        assertNull(SponsorBlockClient.extractVideoId("short_id"))
    }

    @Test
    fun jsonParsingCreatesValidSponsorSegment() {
        val validJson = JSONObject().apply {
            put("UUID", "seg-abc-123")
            put("category", "sponsor")
            put("segment", JSONArray().apply {
                put(14.5)
                put(45.2)
            })
        }

        val segment = SponsorSegment.fromJson(validJson)
        assertNotNull(segment)
        assertEquals("seg-abc-123", segment?.uuid)
        assertEquals("sponsor", segment?.category)
        assertEquals(14.5, segment?.start ?: 0.0, 0.001)
        assertEquals(45.2, segment?.end ?: 0.0, 0.001)
        assertEquals(30.7, segment?.duration ?: 0.0, 0.001)

        // Invalid: end <= start
        val invertedJson = JSONObject().apply {
            put("UUID", "seg-inv")
            put("category", "intro")
            put("segment", JSONArray().apply {
                put(50.0)
                put(20.0)
            })
        }
        assertNull(SponsorSegment.fromJson(invertedJson))

        // Invalid: missing array
        val missingJson = JSONObject().apply {
            put("UUID", "seg-missing")
        }
        assertNull(SponsorSegment.fromJson(missingJson))
    }

    @Test
    fun categoryLabelProvidesArabicAndEnglishTranslations() {
        assertEquals("إعلان ترويجي", SponsorSegment.categoryLabel("sponsor", arabic = true))
        assertEquals("Sponsor", SponsorSegment.categoryLabel("sponsor", arabic = false))

        assertEquals("مقدمة", SponsorSegment.categoryLabel("intro", arabic = true))
        assertEquals("شارة النهاية", SponsorSegment.categoryLabel("outro", arabic = true))
        assertEquals("ترويج ذاتي", SponsorSegment.categoryLabel("selfpromo", arabic = true))
        assertEquals("طلب تفاعل واشتراك", SponsorSegment.categoryLabel("interaction", arabic = true))
        assertEquals("معاينة سريعة", SponsorSegment.categoryLabel("preview", arabic = true))
        assertEquals("موسيقى غير مرتبطة", SponsorSegment.categoryLabel("music_offtopic", arabic = true))

        // Default fallback
        assertEquals("مقطع إعلاني", SponsorSegment.categoryLabel("custom_sponsor_type", arabic = true))
        assertEquals("Promotional Segment", SponsorSegment.categoryLabel("custom_sponsor_type", arabic = false))
    }

    @Test
    fun findSegmentToSkipDetectsEnclosingInterval() {
        val segments = listOf(
            SponsorSegment("1", "intro", 0.0, 15.0),
            SponsorSegment("2", "sponsor", 45.0, 75.0),
            SponsorSegment("3", "outro", 120.0, 140.0)
        )

        // Inside intro
        val skipIntro = SponsorSegment.findSegmentToSkip(10.5, segments)
        assertNotNull(skipIntro)
        assertEquals("1", skipIntro?.uuid)

        // Exactly at start
        val skipSponsorStart = SponsorSegment.findSegmentToSkip(45.0, segments)
        assertNotNull(skipSponsorStart)
        assertEquals("2", skipSponsorStart?.uuid)

        // Exactly at end (segment has finished, should not skip)
        val atEnd = SponsorSegment.findSegmentToSkip(75.0, segments)
        assertNull(atEnd)

        // Normal content between segments
        val inBetween = SponsorSegment.findSegmentToSkip(30.0, segments)
        assertNull(inBetween)

        // After all segments
        val atEndContent = SponsorSegment.findSegmentToSkip(200.0, segments)
        assertNull(atEndContent)
    }

    @Test
    fun mergeSegmentsCombinesOverlappingAndTouchingRanges() {
        val overlapping = listOf(
            SponsorSegment("b", "sponsor", 15.0, 30.0),
            SponsorSegment("a", "sponsor", 10.0, 20.0),
            SponsorSegment("c", "interaction", 30.0, 40.0),
            SponsorSegment("d", "outro", 80.0, 95.0)
        )

        val merged = SponsorSegment.mergeSegments(overlapping)
        assertEquals(2, merged.size)

        // First merged interval: [10.0, 40.0]
        assertEquals(10.0, merged[0].start, 0.001)
        assertEquals(40.0, merged[0].end, 0.001)

        // Second distinct interval: [80.0, 95.0]
        assertEquals(80.0, merged[1].start, 0.001)
        assertEquals(95.0, merged[1].end, 0.001)
    }

    @Test
    fun mergeSegmentsHandlesEmptyAndSingleLists() {
        assertTrue(SponsorSegment.mergeSegments(emptyList()).isEmpty())

        val single = listOf(SponsorSegment("single", "sponsor", 5.0, 15.0))
        val result = SponsorSegment.mergeSegments(single)
        assertEquals(1, result.size)
        assertEquals(5.0, result[0].start, 0.001)
        assertEquals(15.0, result[0].end, 0.001)
    }
}
