package org.videopocket.probe.beam

import android.net.Uri
import java.security.MessageDigest

/**
 * Data structures for the Pocket Beam P2P Local WiFi Transfer Engine.
 */
data class BeamSession(
    val sessionId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val ipAddress: String,
    val port: Int,
    val token: String,
    val sha256Checksum: String? = null
) {
    /**
     * Direct browser-accessible URL for any device on the same local network.
     */
    val webDownloadUrl: String
        get() = "http://$ipAddress:$port/download?token=$token"

    /**
     * Web inspection landing page URL.
     */
    val webLandingUrl: String
        get() = "http://$ipAddress:$port/beam"

    /**
     * Compact URI for Video Pocket peer-to-peer scanning.
     */
    val beamUriString: String
        get() = "beam://$ipAddress:$port/$token?name=${Uri.encode(fileName)}&size=$fileSizeBytes&mime=${Uri.encode(mimeType)}${if (sha256Checksum != null) "&sha=$sha256Checksum" else ""}"

    companion object {
        fun parseBeamUri(uriString: String): BeamSession? {
            return try {
                val clean = uriString.trim()
                if (clean.startsWith("http://") || clean.startsWith("https://")) {
                    val uri = Uri.parse(clean)
                    val ip = uri.host ?: return null
                    val port = if (uri.port > 0) uri.port else 8990
                    val token = uri.getQueryParameter("token") ?: ""
                    val name = uri.getQueryParameter("name") ?: "received_video.mp4"
                    val size = uri.getQueryParameter("size")?.toLongOrNull() ?: 0L
                    val mime = uri.getQueryParameter("mime") ?: "video/mp4"
                    val sha = uri.getQueryParameter("sha")
                    return BeamSession(
                        sessionId = token.ifBlank { "beam-${System.currentTimeMillis()}" },
                        fileName = name,
                        fileSizeBytes = size,
                        mimeType = mime,
                        ipAddress = ip,
                        port = port,
                        token = token,
                        sha256Checksum = sha
                    )
                }

                if (clean.startsWith("beam://")) {
                    val uri = Uri.parse(clean)
                    val ip = uri.host ?: return null
                    val port = if (uri.port > 0) uri.port else 8990
                    val token = uri.path?.removePrefix("/") ?: ""
                    val name = uri.getQueryParameter("name") ?: "received_video.mp4"
                    val size = uri.getQueryParameter("size")?.toLongOrNull() ?: 0L
                    val mime = uri.getQueryParameter("mime") ?: "video/mp4"
                    val sha = uri.getQueryParameter("sha")
                    return BeamSession(
                        sessionId = token.ifBlank { "beam-${System.currentTimeMillis()}" },
                        fileName = name,
                        fileSizeBytes = size,
                        mimeType = mime,
                        ipAddress = ip,
                        port = port,
                        token = token,
                        sha256Checksum = sha
                    )
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }
}

sealed class BeamState {
    object Idle : BeamState()
    data class Serving(val session: BeamSession) : BeamState()
    data class Transferring(val progress: BeamProgress) : BeamState()
    data class Success(val fileName: String, val sizeBytes: Long, val savedUri: Uri?, val sha256Verified: Boolean) : BeamState()
    data class Error(val message: String) : BeamState()
}

data class BeamProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val percent: Float
)

object BeamHashUtil {
    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
