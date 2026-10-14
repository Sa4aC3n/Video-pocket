package org.videopocket.probe.resolver

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URI
import java.net.HttpURLConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.videopocket.probe.core.LinkPolicy
import org.videopocket.probe.storage.FileNamingEngine

interface SubtitleTranslator {
    suspend fun translate(text: String, fromLang: String, toLang: String): String
}

object SubtitleCenter {

    suspend fun exportSubtitle(
        context: Context,
        title: String,
        subtitle: MediaSubtitle,
        targetFormat: String = "srt" // "srt", "vtt", "txt"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val content = fetchSubtitleContent(subtitle.url)
            val format = targetFormat.lowercase(java.util.Locale.ROOT)
            val formatted = SubtitleFormat.convert(content, format)

            val safeTitle = FileNamingEngine.sanitize(title).take(60)
            val language = FileNamingEngine.sanitize(subtitle.languageCode).take(15)
            val displayName = "${safeTitle}_${language}_${java.util.UUID.randomUUID().toString().take(8)}.$format"

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, when (format) { "vtt" -> "text/vtt"; "srt" -> "application/x-subrip"; else -> "text/plain" })
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/VideoPocket/Subtitles")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(Exception("Failed to create storage entry"))

            try {
                currentCoroutineContext().ensureActive()
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(formatted.toByteArray(Charsets.UTF_8))
                } ?: error("Failed to open output stream")
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) > 0)
                Result.success(uri.toString())
            } catch (failure: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw failure
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchSubtitleContent(url: String): String {
        var current = LinkPolicy.validate(url)
        repeat(6) { redirects ->
            currentCoroutineContext().ensureActive()
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000; instanceFollowRedirects = false
                setRequestProperty("Accept", "text/vtt,application/x-subrip,text/plain")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    check(redirects < 5) { "Too many redirects" }
                    val location = connection.getHeaderField("Location") ?: error("Missing redirect")
                    current = LinkPolicy.validate(URI(current).resolve(location).toString())
                } else {
                    check(code in 200..299) { "Subtitle request failed" }
                    val limit = 2 * 1024 * 1024
                    check(connection.contentLengthLong <= limit) { "Subtitle is too large" }
                    val output = java.io.ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= limit) { "Subtitle is too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                    return output.toString(Charsets.UTF_8.name())
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects")
    }

    fun stripTimestamps(subContent: String): String = SubtitleFormat.convert(subContent, "txt")
    fun convertToVtt(subContent: String): String = SubtitleFormat.convert(subContent, "vtt")
}
