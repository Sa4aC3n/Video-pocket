package org.videopocket.probe.sponsorblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object SponsorBlockClient {
    private const val API_BASE = "https://sponsor.ajay.app/api/skipSegments"
    private const val CONNECT_TIMEOUT_MS = 4000
    private const val READ_TIMEOUT_MS = 4000

    private val cache = ConcurrentHashMap<String, List<SponsorSegment>>()

    private val YOUTUBE_ID_REGEX = Pattern.compile(
        """(?:youtube\.com\/(?:[^\/]+\/.+\/|(?:v|e(?:mbed)?)\/|.*[?&]v=)|youtu\.be\/|youtube\.com\/shorts\/)([a-zA-Z0-9_-]{11})"""
    )
    private val STANDALONE_ID_REGEX = Pattern.compile("""^[a-zA-Z0-9_-]{11}$""")

    /**
     * Extracts YouTube 11-character video ID from a URL or raw ID string.
     */
    fun extractVideoId(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()
        if (STANDALONE_ID_REGEX.matcher(trimmed).matches()) {
            return trimmed
        }
        val matcher = YOUTUBE_ID_REGEX.matcher(trimmed)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Queries SponsorBlock API for the given video ID.
     * Safe against offline / 404: returns empty list if no segments found or network error.
     */
    suspend fun getSegments(videoId: String?): List<SponsorSegment> = withContext(Dispatchers.IO) {
        val id = videoId?.let { extractVideoId(it) } ?: return@withContext emptyList()

        cache[id]?.let { return@withContext it }

        val categories = """["sponsor","intro","outro","selfpromo","interaction","preview"]"""
        val encodedCat = URLEncoder.encode(categories, "UTF-8")
        val endpoint = "$API_BASE?videoID=$id&categories=$encodedCat"

        val segments = try {
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "VideoPocket/3.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }

            val code = connection.responseCode
            if (code == 404) {
                // Video has no sponsor segments registered in SponsorBlock
                emptyList()
            } else if (code in 200..299) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonText)
                val list = mutableListOf<SponsorSegment>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    SponsorSegment.fromJson(obj)?.let { list.add(it) }
                }
                SponsorSegment.mergeSegments(list)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        cache[id] = segments
        segments
    }

    fun clearCache() {
        cache.clear()
    }
}
