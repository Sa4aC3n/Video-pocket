package org.videopocket.probe.beam

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance embedded HTTP server for zero-internet local P2P media streaming & transfer.
 * Operates over local Wi-Fi, Portable Hotspots, or Wi-Fi Direct.
 */
class PocketBeamServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    var activeSession: BeamSession? = null
        private set

    /**
     * Resolves the primary local IPv4 address of the active Wi-Fi or Hotspot network interface.
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Priority 1: Common Wi-Fi and hotspot interfaces
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase(Locale.ROOT)
                val isWifiOrAp = name.contains("wlan") || name.contains("ap") || name.contains("swlan") || name.contains("rndis")
                if (!isWifiOrAp) continue

                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }

            // Priority 2: Any non-loopback site-local IPv4
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address && addr.isSiteLocalAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    /**
     * Starts serving the given media file over local HTTP.
     */
    fun startServing(
        fileUri: Uri,
        fileName: String,
        mimeType: String,
        fileSize: Long,
        onProgress: ((BeamProgress) -> Unit)? = null
    ): BeamSession {
        stopServing()

        val port = findAvailablePort(8990)
        val server = ServerSocket(port)
        serverSocket = server
        isRunning.set(true)

        val token = UUID.randomUUID().toString().substring(0, 8)
        val ip = getLocalIpAddress()

        val session = BeamSession(
            sessionId = "beam-${System.currentTimeMillis()}",
            fileName = fileName,
            fileSizeBytes = fileSize,
            mimeType = mimeType.ifBlank { "video/mp4" },
            ipAddress = ip,
            port = port,
            token = token
        )
        activeSession = session

        serverJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isRunning.get() && !server.isClosed) {
                try {
                    val client = server.accept()
                    activeSockets.add(client)
                    launch(Dispatchers.IO) {
                        handleClient(client, fileUri, session, onProgress)
                    }
                } catch (e: SocketException) {
                    break
                } catch (_: Exception) {}
            }
        }

        return session
    }

    private fun handleClient(
        socket: Socket,
        fileUri: Uri,
        session: BeamSession,
        onProgress: ((BeamProgress) -> Unit)?
    ) {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 30000

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase(Locale.ROOT)
            val path = parts[1]

            // Parse headers
            val headers = mutableMapOf<String, String>()
            var line: String? = reader.readLine()
            while (!line.isNullOrBlank()) {
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val k = line.substring(0, colon).trim().lowercase(Locale.ROOT)
                    val v = line.substring(colon + 1).trim()
                    headers[k] = v
                }
                line = reader.readLine()
            }

            val out = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)

            when {
                path.startsWith("/beam") || path == "/" -> {
                    serveLandingPage(out, session)
                }
                path.startsWith("/download") || path.startsWith("/stream") -> {
                    serveFileStream(out, fileUri, session, headers, onProgress)
                }
                else -> {
                    val response = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
                    out.write(response.toByteArray())
                    out.flush()
                }
            }
        } catch (_: Exception) {
        } finally {
            activeSockets.remove(socket)
            runCatching { socket.close() }
        }
    }

    private fun serveLandingPage(out: OutputStream, session: BeamSession) {
        val sizeMb = "%.1f MB".format(session.fileSizeBytes / (1024.0 * 1024.0))
        val html = """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>⚡ Pocket Beam — النقل الفضائي</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                    body { background: #0F172A; color: #F8FAFC; display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
                    .card { background: #1E293B; border: 1px solid #334155; border-radius: 24px; padding: 32px; max-width: 480px; width: 100%; text-align: center; box-shadow: 0 20px 40px rgba(0,0,0,0.5); }
                    .badge { display: inline-block; background: #6366F1; color: white; padding: 6px 16px; border-radius: 9999px; font-size: 13px; font-weight: bold; margin-bottom: 20px; }
                    h1 { font-size: 22px; margin-bottom: 12px; color: #FFFFFF; }
                    .meta { background: #0F172A; border-radius: 16px; padding: 16px; margin: 20px 0; font-size: 14px; color: #94A3B8; text-align: right; }
                    .meta div { display: flex; justify-content: space-between; margin-bottom: 8px; }
                    .meta div:last-child { margin-bottom: 0; }
                    .meta strong { color: #E2E8F0; }
                    .btn { display: block; width: 100%; background: linear-gradient(135deg, #4F46E5, #7C3AED); color: white; text-decoration: none; padding: 16px; border-radius: 16px; font-size: 16px; font-weight: bold; box-shadow: 0 8px 20px rgba(99, 102, 241, 0.4); transition: transform 0.1s; }
                    .btn:active { transform: scale(0.98); }
                    .info { margin-top: 20px; font-size: 12px; color: #64748B; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="badge">⚡ نقل فضائي فائق السرعة</div>
                    <h1>ملف جاهز للاستلام الفوري</h1>
                    <div class="meta">
                        <div><span>اسم الملف:</span> <strong>${session.fileName}</strong></div>
                        <div><span>الحجم:</span> <strong>$sizeMb</strong></div>
                        <div><span>النوع:</span> <strong>${session.mimeType}</strong></div>
                    </div>
                    <a href="/download?token=${session.token}" class="btn" download="${session.fileName}">⬇️ تنزيل المقطع الآن (بدون إنترنت)</a>
                    <div class="info">يتم النقل مباشرة عبر شبكة الواي فاي المحلية بسرعة 40-70 ميجابايت/ثانية.</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val responseBytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${responseBytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(responseBytes)
        out.flush()
    }

    private fun serveFileStream(
        out: OutputStream,
        fileUri: Uri,
        session: BeamSession,
        headers: Map<String, String>,
        onProgress: ((BeamProgress) -> Unit)?
    ) {
        val totalLength = session.fileSizeBytes
        val rangeHeader = headers["range"]

        var startByte = 0L
        var endByte = (totalLength - 1).coerceAtLeast(0L)
        var isPartial = false

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.removePrefix("bytes=").split("-")
            val s = range.getOrNull(0)?.toLongOrNull()
            val e = range.getOrNull(1)?.toLongOrNull()
            if (s != null) {
                startByte = s
                if (e != null) endByte = e
                isPartial = true
            }
        }

        val contentLength = (endByte - startByte + 1).coerceAtLeast(0L)
        val statusCode = if (isPartial) "206 Partial Content" else "200 OK"

        val headerBuilder = StringBuilder()
        headerBuilder.append("HTTP/1.1 $statusCode\r\n")
        headerBuilder.append("Content-Type: ${session.mimeType}\r\n")
        headerBuilder.append("Content-Length: $contentLength\r\n")
        headerBuilder.append("Content-Disposition: attachment; filename=\"${session.fileName}\"\r\n")
        headerBuilder.append("Accept-Ranges: bytes\r\n")
        if (isPartial) {
            headerBuilder.append("Content-Range: bytes $startByte-$endByte/$totalLength\r\n")
        }
        headerBuilder.append("Connection: close\r\n\r\n")

        out.write(headerBuilder.toString().toByteArray())
        out.flush()

        val inputStream = context.contentResolver.openInputStream(fileUri) ?: return
        inputStream.use { input ->
            if (startByte > 0) {
                input.skip(startByte)
            }

            val buffer = ByteArray(64 * 1024)
            var bytesRemaining = contentLength
            var bytesTransferred = 0L
            val startTime = System.currentTimeMillis()
            var lastProgressUpdate = 0L

            while (bytesRemaining > 0 && isRunning.get()) {
                val toRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) break

                out.write(buffer, 0, read)
                bytesRemaining -= read
                bytesTransferred += read

                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate >= 250) {
                    lastProgressUpdate = now
                    val elapsedSec = (now - startTime) / 1000.0
                    val speed = if (elapsedSec > 0) (bytesTransferred / elapsedSec).toLong() else 0L
                    val percent = if (totalLength > 0) (bytesTransferred.toFloat() / totalLength.toFloat()) else 1f
                    onProgress?.invoke(
                        BeamProgress(
                            bytesTransferred = bytesTransferred,
                            totalBytes = totalLength,
                            speedBytesPerSec = speed,
                            percent = percent.coerceIn(0f, 1f)
                        )
                    )
                }
            }
            out.flush()
        }
    }

    private fun findAvailablePort(startPort: Int): Int {
        for (p in startPort..(startPort + 50)) {
            try {
                ServerSocket(p).use { return p }
            } catch (_: Exception) {}
        }
        return 0 // System assigns free ephemeral port
    }

    fun stopServing() {
        isRunning.set(false)
        serverJob?.cancel()
        serverJob = null
        for (s in activeSockets) {
            runCatching { s.close() }
        }
        activeSockets.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeSession = null
    }
}
