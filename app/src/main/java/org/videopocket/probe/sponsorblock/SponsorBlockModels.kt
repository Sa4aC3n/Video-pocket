package org.videopocket.probe.sponsorblock

import org.json.JSONArray
import org.json.JSONObject

data class SponsorSegment(
    val uuid: String,
    val category: String,
    val start: Double,
    val end: Double
) {
    val duration: Double get() = (end - start).coerceAtLeast(0.0)

    companion object {
        fun fromJson(json: JSONObject): SponsorSegment? {
            val segmentArray = json.optJSONArray("segment") ?: return null
            if (segmentArray.length() < 2) return null
            val start = segmentArray.optDouble(0, -1.0)
            val end = segmentArray.optDouble(1, -1.0)
            if (start < 0 || end <= start) return null

            return SponsorSegment(
                uuid = json.optString("UUID", ""),
                category = json.optString("category", "sponsor"),
                start = start,
                end = end
            )
        }

        fun categoryLabel(category: String, arabic: Boolean): String = when (category.lowercase()) {
            "sponsor" -> if (arabic) "إعلان ترويجي" else "Sponsor"
            "intro" -> if (arabic) "مقدمة" else "Intro"
            "outro" -> if (arabic) "شارة النهاية" else "Outro"
            "selfpromo" -> if (arabic) "ترويج ذاتي" else "Self Promotion"
            "interaction" -> if (arabic) "طلب تفاعل واشتراك" else "Interaction Reminder"
            "preview" -> if (arabic) "معاينة سريعة" else "Preview"
            "music_offtopic" -> if (arabic) "موسيقى غير مرتبطة" else "Non-Music Section"
            else -> if (arabic) "مقطع إعلاني" else "Promotional Segment"
        }

        /**
         * Finds the first active segment that encloses [currentSec].
         */
        fun findSegmentToSkip(currentSec: Double, segments: List<SponsorSegment>): SponsorSegment? {
            return segments.firstOrNull { currentSec >= it.start && currentSec < it.end }
        }

        /**
         * Sorts and merges overlapping/consecutive sponsor segments.
         */
        fun mergeSegments(segments: List<SponsorSegment>): List<SponsorSegment> {
            if (segments.size <= 1) return segments
            val sorted = segments.sortedBy { it.start }
            val merged = mutableListOf<SponsorSegment>()
            var current = sorted[0]

            for (i in 1 until sorted.size) {
                val next = sorted[i]
                if (next.start <= current.end) {
                    current = current.copy(end = maxOf(current.end, next.end))
                } else {
                    merged.add(current)
                    current = next
                }
            }
            merged.add(current)
            return merged
        }
    }
}
