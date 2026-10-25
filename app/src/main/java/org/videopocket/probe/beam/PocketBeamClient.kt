package org.videopocket.probe.beam

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-speed P2P client to pull media from a PocketBeamServer over local Wi-Fi / Hotspot.
 * Automatically saves and indexes into MediaStore.Downloads (Download/VideoPocket).
 */
class PocketBeamClient(private val context: Context) {

    private val isCancelled = AtomicBoolean(false)

    fun cancel() {
        isCancelled.set(true)
    }

    suspend fun receiveFile(
        session: BeamSession,
        onProgress: (BeamProgress) -> Unit
    ): Result<Uri> = withContext(Dispatchers.IO) {
        isCancelled.set(false)
        var targetUri: Uri? = null

        try {
            val url = URL(session.webDownloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 30000
                setRequestProperty("User-Agent", "VideoPocket-Beam/3.0")
                setRequestProperty("Accept", "*/*")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Beam server responded with HTTP $responseCode"))
            }

            val totalBytes = connection.contentLengthLong.let { if (it > 0) it else session.fileSizeBytes }
            val sha256Expected = connection.getHeaderField("X-Sha256-Checksum") ?: session.sha256Checksum

            // 1. Prepare MediaStore insertion
            val cleanName = session.fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "beam_${System.currentTimeMillis()}.mp4" }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, cleanName)
                put(MediaStore.Downloads.MIME_TYPE, session.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VideoPocket")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(Exception("Failed to allocate destination in MediaStore"))

            // 2. Stream and calculate running SHA-256
            val digest = MessageDigest.getInstance("SHA-256")
            val input: InputStream = connection.inputStream
            val output: OutputStream = resolver.openOutputStream(targetUri)
                ?: return@withContext Result.failure(Exception("Failed to open output stream in MediaStore"))

            input.use { inStream ->
                output.use { outStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesReceived = 0L
                    val startTime = System.currentTimeMillis()
                    var lastProgressUpdate = 0L

                    while (!isCancelled.get()) {
                        val read = inStream.read(buffer)
                        if (read <= 0) break

                        outStream.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesReceived += read

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate >= 200) {
                            lastProgressUpdate = now
                            val elapsedSec = (now - startTime) / 1000.0
                            val speed = if (elapsedSec > 0) (bytesReceived / elapsedSec).toLong() else 0L
                            val percent = if (totalBytes > 0) (bytesReceived.toFloat() / totalBytes.toFloat()) else 0.5f

                            onProgress(
                                BeamProgress(
                                    bytesTransferred = bytesReceived,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed,
                                    percent = percent.coerceIn(0f, 1f)
                                )
                            )
                        }
                    }
                }
            }

            if (isCancelled.get()) {
                targetUri?.let { resolver.delete(it, null, null) }
                return@withContext Result.failure(Exception("Transfer cancelled by user"))
            }

            // 3. Mark completed in MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(targetUri, completeValues, null, null)
            }

            // 4. SHA-256 Verification
            val actualHash = BeamHashUtil.bytesToHex(digest.digest())
            if (!sha256Expected.isNullOrBlank() && !actualHash.equals(sha256Expected, ignoreCase = true)) {
                return@withContext Result.failure(Exception("SHA-256 integrity mismatch! File may be corrupt."))
            }

            // 5. Index with MediaScanner
            runCatching {
                val path = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VideoPocket/$cleanName").absolutePath
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(session.mimeType), null)
            }

            onProgress(
                BeamProgress(
                    bytesTransferred = totalBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSec = 0L,
                    percent = 1f
                )
            )

            Result.success(targetUri)
        } catch (e: Exception) {
            targetUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            Result.failure(e)
        }
    }
}
