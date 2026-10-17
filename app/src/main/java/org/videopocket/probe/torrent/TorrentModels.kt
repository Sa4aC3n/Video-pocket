package org.videopocket.probe.torrent

import java.util.UUID

enum class TorrentState(val label: String, val arabicLabel: String) {
    QUEUED("Queued", "في الانتظار"),
    FETCHING_METADATA("Fetching Metadata", "جاري جلب بيانات التورنت"),
    CONNECTING("Connecting to Peers", "جاري الاتصال بالنظراء"),
    DOWNLOADING("Downloading", "جاري التنزيل"),
    SEEDING("Seeding", "مشاركة (سيدنج)"),
    PAUSED("Paused", "متوقف مؤقتاً"),
    COMPLETED("Completed", "مكتمل"),
    FAILED("Failed", "فشل")
}

data class TorrentFileEntry(
    val path: String,
    val length: Long,
    var downloaded: Long = 0L
)

data class TorrentTask(
    val id: String = UUID.randomUUID().toString(),
    val magnetUri: String? = null,
    val torrentFilePath: String? = null,
    var infoHash: String = "",
    var name: String = "Torrent",
    var totalBytes: Long = 0L,
    var downloadedBytes: Long = 0L,
    var uploadedBytes: Long = 0L,
    var state: TorrentState = TorrentState.QUEUED,
    var downloadSpeedBps: Long = 0L,
    var uploadSpeedBps: Long = 0L,
    var etaSeconds: Long = 0L,
    var seedsCount: Int = 0,
    var peersCount: Int = 0,
    var trackers: List<String> = emptyList(),
    var files: List<TorrentFileEntry> = emptyList(),
    var outputPath: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    var errorMessage: String? = null
) {
    val progressPercent: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes * 100f).coerceIn(0f, 100f) else 0f

    fun formattedDownSpeed(): String = formatSpeed(downloadSpeedBps)
    fun formattedUpSpeed(): String = formatSpeed(uploadSpeedBps)

    fun formattedEta(): String = when {
        etaSeconds >= 3600 -> "%d:%02d:%02d".format(etaSeconds / 3600, (etaSeconds % 3600) / 60, etaSeconds % 60)
        etaSeconds > 0     -> "%02d:%02d".format(etaSeconds / 60, etaSeconds % 60)
        else               -> ""
    }

    fun formattedTotalSize(): String = formatBytes(totalBytes)
    fun formattedDownloadedSize(): String = formatBytes(downloadedBytes)

    companion object {
        fun formatSpeed(bps: Long): String = when {
            bps >= 1_048_576 -> "%.2f MB/s".format(bps / 1_048_576.0)
            bps >= 1_024     -> "%.1f KB/s".format(bps / 1_024.0)
            bps > 0          -> "$bps B/s"
            else             -> "0 B/s"
        }

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024         -> "%.0f KB".format(bytes / 1_024.0)
            bytes > 0              -> "$bytes B"
            else                   -> "—"
        }
    }
}
