package org.videopocket.probe.core

import java.net.URI

enum class ProbeAction { INSPECT, VIDEO, MERGE, MP3, PLAYLIST_VIDEO, PLAYLIST_MP3, CLIP }
enum class Problem { INVALID_URL, UNSUPPORTED, LOGIN_REQUIRED, REMOVED, RESTRICTED, LIVE_OR_PLAYLIST, SPACE, NETWORK, CONVERSION, ENGINE, CANCELED }
class ProbeFailure(val problem: Problem, val reason: String? = null) : Exception(reason ?: problem.name)
data class MediaFormat(
    val id: String,
    val extension: String,
    val height: Int,
    val video: Boolean,
    val audio: Boolean,
    val bytes: Long?,
    val url: String? = null
)
data class PlaylistEntry(val id: String, val title: String, val duration: Double? = null, val url: String? = null)
data class SubtitleTrack(
    val lang: String,
    val name: String,
    val ext: String,
    val url: String,
    val isAuto: Boolean = false
)

data class MediaInfo(
    val title: String,
    val duration: Double?,
    val formats: List<MediaFormat>,
    val isPlaylist: Boolean = false,
    val playlistCount: Int = 0,
    val entries: List<PlaylistEntry> = emptyList(),
    val thumbnailUrl: String? = null,
    val streamUrl: String? = null,
    val uploader: String? = null,
    val extractor: String? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val id: String? = null
) {
    val heights get() = formats.filter { it.video && it.height > 0 }.map { it.height }.distinct().sortedDescending()
    val defaultHeight get() = heights.firstOrNull { it <= 1080 } ?: heights.minOrNull() ?: 0
}
data class ProbeResult(val action: ProbeAction, val elapsedMs: Long, val bytes: Long, val outputUri: String?, val passed: Boolean, val detail: String, val outputUris: List<String> = emptyList())

enum class QualityTier(val label: String, val category: String) {
    LD_144("144p", "SD"),
    LD_240("240p", "SD"),
    SD_360("360p", "SD"),
    SD_480("480p", "SD"),
    HD_720("720p", "HD"),
    FHD_1080("1080p", "FHD"),
    QHD_1440("1440p", "2K"),
    UHD_2160("2160p", "4K"),
    UHD_4320("4320p", "8K");

    companion object {
        fun fromHeight(height: Int): QualityTier = when {
            height <= 144 -> LD_144
            height <= 240 -> LD_240
            height <= 360 -> SD_360
            height <= 480 -> SD_480
            height <= 720 -> HD_720
            height <= 1080 -> FHD_1080
            height <= 1440 -> QHD_1440
            height <= 2160 -> UHD_2160
            else -> UHD_4320
        }
    }
}

data class ClipRequest(
    val startSeconds: Double,
    val endSeconds: Double,
    val height: Int = 1080,
    val format: String = "mp4", // "mp4", "webm"
    val isBestQuality: Boolean = false,
    val isAudioOnly: Boolean = false
) {
    val durationSeconds: Double get() = (endSeconds - startSeconds).coerceAtLeast(0.0)

    fun startFormatted(): String = formatSeconds(startSeconds)
    fun endFormatted(): String = formatSeconds(endSeconds)
    fun durationFormatted(): String = formatSeconds(durationSeconds)

    companion object {
        fun formatSeconds(seconds: Double): String {
            val totalSec = seconds.toLong().coerceAtLeast(0L)
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
            else String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }

        fun formatSecondsFull(seconds: Double): String {
            val totalSec = seconds.toLong().coerceAtLeast(0L)
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            val millis = ((seconds - totalSec) * 1000).toInt().coerceIn(0, 999)
            return if (millis > 0) String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", h, m, s, millis)
            else String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
        }

        fun parseTimestamp(text: String): Double? {
            val clean = text.trim()
            if (clean.isBlank()) return null
            val parts = clean.split(":")
            if (parts.size !in 1..3) return null
            val values = parts.map { it.toDoubleOrNull() ?: return null }
            if (values.any { !it.isFinite() || it < 0.0 }) return null
            if (values.drop(1).any { it >= 60.0 }) return null
            if (values.dropLast(1).any { it % 1.0 != 0.0 }) return null
            return values.fold(0.0) { total, value -> total * 60 + value }.takeIf { it.isFinite() }
        }
    }
}

data class PocketLibraryItem(
    val id: String,
    val title: String,
    val uriString: String,
    val isClip: Boolean,
    val clipRange: String? = null,
    val duration: String,
    val resolution: String,
    val format: String,
    val fileSizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val thumbnailUri: String? = null,
    val platform: String? = null,
    val isFavorite: Boolean = false,
    val collections: List<String> = emptyList(),
    val sourceMediaId: String? = null,
    val sourceUrl: String? = null
)

object LinkPolicy {
    fun validate(input: String): String {
        val text = input.trim()
        val uri = try { URI(text) } catch (_: Exception) { throw ProbeFailure(Problem.INVALID_URL) }
        if (text.length > 8192 || text.any { it.isWhitespace() || it.isISOControl() } ||
            uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null)
            throw ProbeFailure(Problem.INVALID_URL)
        val host = uri.host.lowercase().trim('[', ']').trimEnd('.')
        if (host == "localhost" || host.endsWith(".localhost") || host in setOf("::1", "0.0.0.0", "::") ||
            host.startsWith("127.") || host.startsWith("169.254.")) throw ProbeFailure(Problem.INVALID_URL)
        return text
    }
}

object FormatPolicy {
    // No fallback to a lower resolution: make a missing requested format visible.
    fun select(info: MediaInfo, action: ProbeAction, height: Int): String {
        fun safe(id: String): String {
            if (!id.matches(Regex("[A-Za-z0-9_.-]+"))) throw ProbeFailure(Problem.UNSUPPORTED)
            return id
        }
        val audio = info.formats.filter { it.audio && !it.video }.maxByOrNull { it.bytes ?: 0 }
        val videos = info.formats.filter { it.video && it.height == height }
        return when (action) {
            ProbeAction.INSPECT -> "best"
            ProbeAction.MP3 -> safe((audio ?: info.formats.lastOrNull { it.audio }
                ?: throw ProbeFailure(Problem.UNSUPPORTED)).id)
            ProbeAction.MERGE -> {
                val video = videos.lastOrNull { !it.audio } ?: throw ProbeFailure(Problem.UNSUPPORTED)
                safe(video.id) + "+" + safe((audio ?: throw ProbeFailure(Problem.UNSUPPORTED)).id)
            }
            ProbeAction.VIDEO -> {
                val combined = videos.lastOrNull { it.audio }
                if (combined != null) safe(combined.id)
                else {
                    val video = videos.lastOrNull() ?: throw ProbeFailure(Problem.UNSUPPORTED)
                    if (audio != null) safe(video.id) + "+" + safe(audio.id) else safe(video.id)
                }
            }
            ProbeAction.PLAYLIST_VIDEO -> "bestvideo[height<=$height]+bestaudio/best[height<=$height]/best"
            ProbeAction.PLAYLIST_MP3 -> "bestaudio/best"
            ProbeAction.CLIP -> if (height == 0) "bestvideo+bestaudio/best" else "bestvideo[height<=$height]+bestaudio/best[height<=$height]/best"
        }
    }
}
