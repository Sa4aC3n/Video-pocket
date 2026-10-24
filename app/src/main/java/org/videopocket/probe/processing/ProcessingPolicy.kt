package org.videopocket.probe.processing

import kotlin.math.floor

enum class ProcessingMode { COMPRESS, TRIM, EXTRACT_AUDIO, CONVERT }

/**
 * Compression presets.
 *
 * - HIGH_QUALITY : 1080p max, CRF 20 — best visual quality
 * - BALANCED     : 720p max,  CRF 26 — good quality / size trade-off (default)
 * - SMALL_FILE   : 540p max,  CRF 30 — smallest file, lower quality
 * - SMART        : analysed at runtime by [CompressionPlanner]; picks the best settings
 *                  based on the source codec, bitrate, and resolution. Falls back to BALANCED.
 */
enum class CompressionPreset(val maxEdge: Int, val crf: Int) {
    HIGH_QUALITY(1920, 20),
    BALANCED(1280, 26),
    SMALL_FILE(960, 30),
    /** Placeholder — resolved to one of the above by [CompressionPlanner.resolve]. */
    SMART(1280, 26)
}

data class ProcessingRequest(
    val uri: String,
    val title: String,
    val mode: ProcessingMode = ProcessingMode.COMPRESS,
    val preset: CompressionPreset = CompressionPreset.BALANCED,
    val format: String = "mp4",
    val startSeconds: Double = 0.0,
    val endSeconds: Double? = null,
    val targetBytes: Long? = null
)

data class ProcessingCapabilities(val encoders: Set<String>, val muxers: Set<String>) {
    fun videoEncoder(format: String, preset: CompressionPreset): String? = when (format) {
        "webm" -> "libvpx-vp9".takeIf { it in encoders }
        "mp4", "mkv" -> {
            val order = if (preset == CompressionPreset.SMALL_FILE) listOf("libx265", "libx264") else listOf("libx264", "libx265")
            order.firstOrNull { it in encoders }
        }
        else -> null
    }
    fun audioEncoder(format: String): String? = when (format) {
        "mp3"              -> "libmp3lame"
        "m4a", "mp4", "mkv" -> "aac"
        "webm", "opus"     -> "libopus"
        "wav"              -> "pcm_s16le"
        else               -> null
    }?.takeIf { it in encoders }
    fun muxer(format: String) = when (format) {
        "mkv"  -> "matroska"
        "m4a"  -> "ipod"
        "opus" -> "ogg"
        else   -> format
    }
    fun formats(audioOnly: Boolean): List<String> =
        (if (audioOnly) listOf("mp3", "m4a", "opus", "wav") else listOf("mp4", "webm", "mkv"))
            .filter { muxer(it) in muxers && audioEncoder(it) != null && (audioOnly || videoEncoder(it, CompressionPreset.BALANCED) != null) }
}

object ProcessingPolicy {
    fun dimensions(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        require(width >= 2 && height >= 2 && maxEdge >= 2)
        val scale = minOf(1.0, maxEdge.toDouble() / maxOf(width, height))
        return (floor(width * scale / 2).toInt() * 2).coerceAtLeast(2) to
            (floor(height * scale / 2).toInt() * 2).coerceAtLeast(2)
    }
    fun duration(request: ProcessingRequest, sourceSeconds: Double): Double {
        require(sourceSeconds.isFinite() && sourceSeconds > 0)
        require(request.startSeconds.isFinite() && request.startSeconds >= 0)
        val end = request.endSeconds ?: sourceSeconds
        require(end.isFinite() && end <= sourceSeconds && end > request.startSeconds)
        require(request.mode == ProcessingMode.TRIM || (request.startSeconds == 0.0 && request.endSeconds == null))
        return end - request.startSeconds
    }
    fun videoBitrate(targetBytes: Long, seconds: Double, hasAudio: Boolean): Long {
        require(targetBytes > 0 && seconds.isFinite() && seconds > 0)
        val bitrate = (targetBytes.toDouble() * 8 * 0.90 / seconds).toLong() - if (hasAudio) 128_000 else 0
        require(bitrate in 64_000..100_000_000) { "Requested size cannot preserve a usable bitrate" }
        return bitrate
    }
    fun progress(line: String, durationUs: Long): Float? {
        if (!line.startsWith("out_time_us=") || durationUs <= 0) return null
        val timestamp = line.substringAfter('=').toLongOrNull()?.takeIf { it >= 0 } ?: return null
        return (timestamp.toDouble() / durationUs * 100).coerceIn(0.0, 99.0).toFloat()
    }
    fun parseCodecs(output: String): Set<String> = output.lineSequence().mapNotNull { line ->
        Regex("^\\s*[VAS][A-Z.]{5}\\s+([a-zA-Z0-9_]+)\\s").find(line)?.groupValues?.get(1)
    }.toSet()
    fun parseMuxers(output: String): Set<String> = output.lineSequence().mapNotNull { line ->
        Regex("^\\s*E\\s+([a-zA-Z0-9_,]+)\\s").find(line)?.groupValues?.get(1)
    }.flatMap { it.split(',') }.toSet()
}
