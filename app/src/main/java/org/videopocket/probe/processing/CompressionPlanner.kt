package org.videopocket.probe.processing

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat

/**
 * Analyses a source video file and resolves the [CompressionPreset.SMART] preset
 * to a concrete preset + format pair, and decides whether compression is worthwhile.
 *
 * Decision logic (matches the spec from the Phase 0.4.0 roadmap):
 *
 *  1. Read basic metadata (codec, bitrate, resolution, fps) via [MediaExtractor].
 *  2. If the video bitrate is already low (≤ the SMALL_FILE CRF target for its resolution),
 *     return [SmartDecision.AlreadyEfficient] — no compression needed.
 *  3. Otherwise select:
 *     - HIGH_QUALITY if source is ≥ 1080p
 *     - BALANCED     if source is 720p – 1079p
 *     - SMALL_FILE   if source is < 720p
 *
 * All decisions include a human-readable explanation for display in the UI.
 */
object CompressionPlanner {

    /**
     * Result of a smart analysis.
     */
    sealed class SmartDecision {
        /** The source is already efficiently compressed — no action needed. */
        data class AlreadyEfficient(
            val reason: String,
            val reasonAr: String
        ) : SmartDecision()

        /** Compress using the suggested preset. */
        data class Compress(
            val preset: CompressionPreset,
            val reason: String,
            val reasonAr: String
        ) : SmartDecision()

        /** Analysis failed (unreadable file, no video track, etc.). Fall back to BALANCED. */
        data class Fallback(val cause: String) : SmartDecision()
    }

    /**
     * Performs a quick analysis of the file at [uri] (must be a content:// URI readable by
     * [context]'s ContentResolver) and returns a [SmartDecision].
     *
     * This method must be called **off the main thread** (it does I/O).
     */
    fun analyse(context: Context, uri: String): SmartDecision {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, android.net.Uri.parse(uri), null)
            val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            val videoTrack = tracks.firstOrNull {
                it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return SmartDecision.Fallback("no_video_track")

            val width = runCatching { videoTrack.getInteger(MediaFormat.KEY_WIDTH) }.getOrDefault(0)
            val height = runCatching { videoTrack.getInteger(MediaFormat.KEY_HEIGHT) }.getOrDefault(0)
            val maxEdge = maxOf(width, height)

            // Bitrate in bits/s — not always present
            val bitrateBps = runCatching { videoTrack.getInteger(MediaFormat.KEY_BIT_RATE).toLong() }
                .getOrNull()
                ?: runCatching { videoTrack.getLong(MediaFormat.KEY_BIT_RATE) }.getOrDefault(0L)

            // Frame rate — some tracks expose it
            val fps = runCatching { videoTrack.getInteger(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(0)

            // Codec MIME
            val mime = videoTrack.getString(MediaFormat.KEY_MIME) ?: ""

            // Heuristic: "efficient" means the current bitrate is already below the threshold
            // we would set for SMALL_FILE at this resolution.
            // Threshold: ~0.07 bits per pixel per frame (generous for H.265/AV1).
            val effectiveFps = if (fps > 0) fps else 30
            val pixelCount = maxOf(width.toLong() * height, 1L)
            val thresholdBps = (pixelCount * effectiveFps * 0.07).toLong()

            // Also treat already-HEVC/VP9/AV1 with low bitrate as efficient
            val isModernCodec = mime.contains("hevc", ignoreCase = true) ||
                mime.contains("vp9", ignoreCase = true) ||
                mime.contains("av01", ignoreCase = true)

            if (bitrateBps in 1..(thresholdBps) && isModernCodec) {
                return SmartDecision.AlreadyEfficient(
                    reason = "This video is already efficiently compressed (${mime.substringAfterLast('/')}, " +
                        "${bitrateBps / 1000} kbps). No further compression needed.",
                    reasonAr = "الفيديو مضغوط بالفعل بكفاءة (${mime.substringAfterLast('/')}, " +
                        "${bitrateBps / 1000} كيلوبت/ث). لا حاجة لضغط إضافي."
                )
            }

            // Pick preset based on resolution
            val recommended = when {
                maxEdge >= 1080 -> CompressionPreset.HIGH_QUALITY
                maxEdge >= 720  -> CompressionPreset.BALANCED
                else            -> CompressionPreset.SMALL_FILE
            }

            val resLabel = if (height > 0) "${height}p" else "${maxEdge}px"
            SmartDecision.Compress(
                preset = recommended,
                reason = "Source is $resLabel${if (bitrateBps > 0) ", ${bitrateBps / 1000} kbps" else ""}. " +
                    "Recommended: ${recommended.name.replace('_', ' ').lowercase()}.",
                reasonAr = "المصدر $resLabel${if (bitrateBps > 0) "، ${bitrateBps / 1000} كيلوبت/ث" else ""}. " +
                    "الموصى به: ${presetNameAr(recommended)}."
            )
        } catch (e: Exception) {
            SmartDecision.Fallback(e.message ?: "unknown")
        } finally {
            extractor.release()
        }
    }

    private fun presetNameAr(preset: CompressionPreset) = when (preset) {
        CompressionPreset.HIGH_QUALITY -> "جودة عالية"
        CompressionPreset.BALANCED    -> "متوازن"
        CompressionPreset.SMALL_FILE  -> "حجم صغير"
        CompressionPreset.SMART       -> "ذكي"
    }

    /**
     * Resolves [CompressionPreset.SMART] to a concrete preset.
     * If the file cannot be analysed, falls back to [CompressionPreset.BALANCED].
     *
     * Must be called **off the main thread**.
     */
    fun resolve(context: Context, uri: String, requested: CompressionPreset): CompressionPreset {
        if (requested != CompressionPreset.SMART) return requested
        return when (val decision = analyse(context, uri)) {
            is SmartDecision.Compress       -> decision.preset
            is SmartDecision.AlreadyEfficient -> CompressionPreset.BALANCED // will be caught by NO_SIZE_REDUCTION check
            is SmartDecision.Fallback        -> CompressionPreset.BALANCED
        }
    }
}
