package org.videopocket.probe.storage

import android.content.Context
import org.videopocket.probe.core.PocketLibraryManager
import java.io.File
import java.util.Locale

object FileNamingEngine {

    fun sanitize(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9._\\-\\u0600-\\u06FF]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "Media" }
    }

    fun generateMediaName(title: String, quality: String, ext: String, author: String? = null): String {
        val cleanTitle = sanitize(title).take(45)
        val cleanQuality = sanitize(quality).take(12)
        val cleanAuthor = author?.let { sanitize(it).take(20) }
        val prefix = if (!cleanAuthor.isNullOrBlank()) "${cleanAuthor}_$cleanTitle" else cleanTitle
        return "${prefix}_${cleanQuality}.${ext.lowercase(Locale.US)}"
    }

    fun generateClipName(title: String, startFormatted: String, endFormatted: String, quality: String, ext: String): String {
        val cleanTitle = sanitize(title).take(35)
        val cleanStart = sanitize(startFormatted)
        val cleanEnd = sanitize(endFormatted)
        val cleanQuality = sanitize(quality).take(10)
        return "${cleanTitle}_clip_${cleanStart}-${cleanEnd}_${cleanQuality}.${ext.lowercase(Locale.US)}"
    }
}

object TempFileManager {

    fun cleanupTemporaryFiles(context: Context): Long {
        var freed = Workspaces.clean(context)
        try {
            val idmDir = File(context.filesDir, "idm_segments")
            if (idmDir.exists()) {
                val before = calculateDirSize(idmDir)
                idmDir.deleteRecursively()
                val after = calculateDirSize(idmDir)
                freed += (before - after).coerceAtLeast(0L)
            }
        } catch (_: Exception) {}
        return freed
    }

    fun getTempFilesSize(context: Context): Long {
        var total = 0L
        try {
            val probeDir = File(context.noBackupFilesDir, "probe-files")
            if (probeDir.exists()) {
                total += calculateDirSize(probeDir)
            }
            val idmDir = File(context.filesDir, "idm_segments")
            if (idmDir.exists()) {
                total += calculateDirSize(idmDir)
            }
        } catch (_: Exception) {}
        return total
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { f ->
            size += if (f.isDirectory) calculateDirSize(f) else f.length()
        }
        return size
    }
}

data class StorageBreakdown(
    val videoBytes: Long,
    val audioBytes: Long,
    val clipBytes: Long,
    val cacheBytes: Long,
    val tempBytes: Long,
    val totalBytes: Long
) {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1000) String.format(Locale.US, "%.2f GB", mb / 1024.0)
        else String.format(Locale.US, "%.1f MB", mb)
    }
}

object StorageManager {

    fun getBreakdown(context: Context): StorageBreakdown {
        val library = PocketLibraryManager.getItems(context)
        var videos = 0L
        var audios = 0L
        var clips = 0L

        for (item in library) {
            val bytes = item.fileSizeBytes
            when {
                item.isClip -> clips += bytes
                org.videopocket.probe.core.MediaTypes.isAudio(item.format) -> audios += bytes
                else -> videos += bytes
            }
        }

        val cacheBytes = calculateDirSize(context.cacheDir)
        val tempBytes = TempFileManager.getTempFilesSize(context)
        val total = videos + audios + clips + cacheBytes + tempBytes

        return StorageBreakdown(
            videoBytes = videos,
            audioBytes = audios,
            clipBytes = clips,
            cacheBytes = cacheBytes,
            tempBytes = tempBytes,
            totalBytes = total
        )
    }

    fun clearCache(context: Context): Long {
        var freed = 0L
        val staleBefore = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        context.cacheDir.listFiles()?.filter { it.lastModified() < staleBefore }?.forEach { file ->
            val before = Workspaces.size(file)
            file.deleteRecursively()
            freed += (before - Workspaces.size(file)).coerceAtLeast(0L)
        }
        return freed
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { f ->
            size += if (f.isDirectory) calculateDirSize(f) else f.length()
        }
        return size
    }
}
