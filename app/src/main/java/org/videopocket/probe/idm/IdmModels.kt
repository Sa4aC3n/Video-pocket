package org.videopocket.probe.idm

import java.util.UUID

enum class IdmState(val label: String, val arabicLabel: String) {
    QUEUED("Queued", "في الانتظار"),
    SCHEDULED("Scheduled", "مجدول"),
    CONNECTING("Connecting", "جاري الاتصال"),
    DOWNLOADING("Downloading", "جاري التنزيل"),
    PAUSED("Paused", "متوقف مؤقتاً"),
    MERGING("Assembling Segments", "تجميع الأجزاء"),
    COMPLETED("Completed", "مكتمل"),
    FAILED("Failed", "فشل"),
    CANCELLED("Cancelled", "ملغى")
}

enum class IdmCategory(val label: String, val arabicLabel: String, val extensions: List<String>) {
    VIDEO("Video", "فيديو", listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "3gp", "ts")),
    AUDIO("Audio", "صوت", listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma")),
    COMPRESSED("Archive", "ملفات مضغوطة", listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")),
    DOCUMENT("Document", "مستندات", listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub")),
    PROGRAM("App / Program", "تطبيقات وبرامج", listOf("apk", "exe", "msi", "dmg", "iso", "bin")),
    OTHER("Other", "أخرى", emptyList());

    companion object {
        fun detect(filename: String): IdmCategory {
            val ext = filename.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extensions.contains(ext) } ?: OTHER
        }
    }
}

data class IdmSegment(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    var downloadedBytes: Long = 0L,
    var isCompleted: Boolean = false
) {
    val totalBytes: Long get() = (endByte - startByte + 1).coerceAtLeast(0)
    val progressPercent: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes * 100f).coerceIn(0f, 100f) else 0f
}

data class IdmTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    var filename: String,
    var totalBytes: Long = 0L,
    var downloadedBytes: Long = 0L,
    var segmentsCount: Int = 4,
    var segments: List<IdmSegment> = emptyList(),
    var state: IdmState = IdmState.QUEUED,
    var speedBytesPerSec: Long = 0L,
    var etaSeconds: Long = 0L,
    var scheduledAtMs: Long? = null,
    var category: IdmCategory = IdmCategory.OTHER,
    var outputPath: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    var errorMessage: String? = null,
    var supportsRange: Boolean = true
) {
    val progressPercent: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes * 100f).coerceIn(0f, 100f) else 0f

    fun formattedSpeed(): String = when {
        speedBytesPerSec >= 1_048_576 -> "%.2f MB/s".format(speedBytesPerSec / 1_048_576.0)
        speedBytesPerSec >= 1_024     -> "%.1f KB/s".format(speedBytesPerSec / 1_024.0)
        speedBytesPerSec > 0          -> "$speedBytesPerSec B/s"
        else                          -> ""
    }

    fun formattedEta(): String = when {
        etaSeconds >= 3600 -> "%d:%02d:%02d".format(etaSeconds / 3600, (etaSeconds % 3600) / 60, etaSeconds % 60)
        etaSeconds > 0     -> "%02d:%02d".format(etaSeconds / 60, etaSeconds % 60)
        else               -> ""
    }

    fun formattedTotalSize(): String = formatBytes(totalBytes)
    fun formattedDownloadedSize(): String = formatBytes(downloadedBytes)

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024         -> "%.0f KB".format(bytes / 1_024.0)
            bytes > 0              -> "$bytes B"
            else                   -> "—"
        }
    }
}
