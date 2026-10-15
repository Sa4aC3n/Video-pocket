package org.videopocket.probe.core

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale
import java.util.UUID

data class SavedMedia(
    val uri: String,
    val name: String,
    val bytes: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val mime: String?
) {
    val extension: String get() = name.substringAfterLast('.', "").uppercase(Locale.ROOT).ifBlank { "Unknown" }
    val isAudio: Boolean get() = mime?.startsWith("audio/") == true

    companion object {
        /** Unknown metadata stays unknown; never copy requested quality or aggregate playlist size. */
        fun read(context: Context, uriString: String): SavedMedia {
            val uri = Uri.parse(uriString)
            val resolver = context.contentResolver
            var name = "Media"
            var bytes = 0L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0) ?: name
                    if (!c.isNull(1)) bytes = c.getLong(1).coerceAtLeast(0L)
                }
            }
            var duration: Long? = null
            var width: Int? = null
            var height: Int? = null
            var contentMime: String? = null
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(context, uri)
                contentMime = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.takeIf { it.isNotBlank() }
                if (contentMime == "video/mp4" && reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" &&
                    reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") contentMime = "audio/mp4"
                duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
                width = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.takeIf { it > 0 }
                height = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?.takeIf { it > 0 }
                if (reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) in setOf("90", "270")) {
                    val oldWidth = width; width = height; height = oldWidth
                }
            } catch (_: Exception) {
                // Some containers are playable externally but not readable by the platform retriever.
            } finally { reader.release() }
            return SavedMedia(uriString, name, bytes, duration, width, height, contentMime ?: resolver.getType(uri))
        }
    }
}

object LibraryIndexer {
    fun index(context: Context, result: ProbeResult, source: MediaInfo, sourceUrl: String?, platform: String?, clip: ClipRequest? = null) {
        check(result.passed)
        val uris = result.outputUris.ifEmpty { listOfNotNull(result.outputUri) }.distinct()
        check(uris.isNotEmpty()) { "No saved media" }
        val items = uris.map { uri ->
            val media = SavedMedia.read(context, uri)
            PocketLibraryItem(
                id = UUID.randomUUID().toString(),
                title = if (source.isPlaylist) media.name.substringBeforeLast('.') else source.title,
                uriString = uri, isClip = clip != null,
                clipRange = clip?.let { "${it.startFormatted()} → ${it.endFormatted()}" },
                duration = media.durationMs?.let { ClipRequest.formatSeconds(it / 1000.0) } ?: "—",
                resolution = if (media.isAudio) "Audio" else media.height?.let { "${it}p" } ?: "—",
                format = media.extension, fileSizeBytes = media.bytes,
                thumbnailUri = if (source.isPlaylist) null else source.thumbnailUrl,
                platform = platform, sourceMediaId = if (source.isPlaylist) null else source.id,
                sourceUrl = sourceUrl, mimeType = media.mime
            )
        }
        PocketLibraryManager.addItems(context, items)
    }
}
