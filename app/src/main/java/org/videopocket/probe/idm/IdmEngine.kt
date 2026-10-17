package org.videopocket.probe.idm

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance IDM-style multi-segment (chunked) parallel download engine.
 *
 * Capabilities:
 * - Detects server Range support & Content-Length.
 * - Extracts real filename from Content-Disposition header or URL path.
 * - Divides files into 2–8 parallel segments using HTTP Range requests.
 * - Supports real pause & resume with zero loss (resumes from exact byte offsets).
 * - Real-time speed calculation and ETA countdown using a sliding window.
 * - Sequential chunk merging into public Downloads / target location.
 * - Non-range fallback for servers that don't support partial downloads.
 */
class IdmEngine(private val context: Context) {

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val cancelledFlags = ConcurrentHashMap<String, AtomicBoolean>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val workingDir: File
        get() = File(context.filesDir, "idm_segments").apply { if (!exists()) mkdirs() }

    fun taskWorkingDir(taskId: String): File =
        File(workingDir, taskId).apply { if (!exists()) mkdirs() }

    /**
     * Inspects a URL to determine Content-Length, server range support, and true filename.
     */
    suspend fun inspect(rawUrl: String): IdmTask = withContext(Dispatchers.IO) {
        val url = URL(rawUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            conn.connect()
            var length = conn.contentLengthLong
            val acceptRanges = conn.getHeaderField("Accept-Ranges")
            val disposition = conn.getHeaderField("Content-Disposition")
            var filename = extractFilename(disposition, rawUrl)

            // If HEAD didn't yield length or was rejected (e.g. 405), fallback to a 1-byte GET range probe
            if (conn.responseCode in 400..499 || length <= 0) {
                conn.disconnect()
                val getConn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Range", "bytes=0-0")
                }
                getConn.connect()
                val contentRange = getConn.getHeaderField("Content-Range")
                if (contentRange != null && contentRange.contains("/")) {
                    val totalStr = contentRange.substringAfterLast("/")
                    length = totalStr.toLongOrNull() ?: -1L
                } else if (length <= 0) {
                    length = getConn.contentLengthLong
                }
                if (filename.isBlank() || filename == "download") {
                    filename = extractFilename(getConn.getHeaderField("Content-Disposition"), rawUrl)
                }
                getConn.disconnect()
            }

            val supportsRange = acceptRanges?.contains("bytes", ignoreCase = true) == true ||
                    conn.getHeaderField("Content-Range") != null ||
                    length > 0

            val category = IdmCategory.detect(filename)

            IdmTask(
                url = rawUrl,
                filename = filename,
                totalBytes = length.coerceAtLeast(0L),
                supportsRange = supportsRange && length > 0,
                category = category
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Starts or resumes a segmented download.
     */
    fun start(
        task: IdmTask,
        onUpdate: (IdmTask) -> Unit,
        onComplete: (IdmTask, File) -> Unit,
        onError: (IdmTask, Throwable) -> Unit
    ) {
        val cancelled = AtomicBoolean(false)
        cancelledFlags[task.id] = cancelled

        val job = scope.launch {
            try {
                task.state = IdmState.CONNECTING
                onUpdate(task)

                val dir = taskWorkingDir(task.id)

                // Initialize segments if not already set up
                if (task.segments.isEmpty()) {
                    if (task.supportsRange && task.totalBytes > 0) {
                        val numSegments = task.segmentsCount.coerceIn(1, 8)
                        val chunkSize = task.totalBytes / numSegments
                        val segList = mutableListOf<IdmSegment>()
                        for (i in 0 until numSegments) {
                            val start = i * chunkSize
                            val end = if (i == numSegments - 1) task.totalBytes - 1 else (i + 1) * chunkSize - 1
                            segList.add(IdmSegment(index = i, startByte = start, endByte = end))
                        }
                        task.segments = segList
                    } else {
                        // Single segment fallback
                        task.segments = listOf(
                            IdmSegment(index = 0, startByte = 0, endByte = task.totalBytes.coerceAtLeast(0) - 1)
                        )
                    }
                }

                task.state = IdmState.DOWNLOADING
                onUpdate(task)

                // Track speed using sliding window
                val speedTracker = SlidingSpeedTracker()

                if (task.supportsRange && task.segments.size > 1) {
                    // Multi-segment parallel download
                    coroutineScope {
                        task.segments.map { segment ->
                            launch {
                                downloadSegment(task.url, segment, dir, cancelled) { deltaBytes ->
                                    synchronized(task) {
                                        task.downloadedBytes = task.segments.sumOf { it.downloadedBytes }
                                        speedTracker.add(deltaBytes)
                                        task.speedBytesPerSec = speedTracker.speedBps()
                                        if (task.speedBytesPerSec > 0 && task.totalBytes > task.downloadedBytes) {
                                            task.etaSeconds = (task.totalBytes - task.downloadedBytes) / task.speedBytesPerSec
                                        }
                                    }
                                    onUpdate(task)
                                }
                            }
                        }.joinAll()
                    }
                } else {
                    // Single stream download
                    val singleSeg = task.segments.first()
                    downloadSegment(task.url, singleSeg, dir, cancelled) { deltaBytes ->
                        task.downloadedBytes = singleSeg.downloadedBytes
                        speedTracker.add(deltaBytes)
                        task.speedBytesPerSec = speedTracker.speedBps()
                        if (task.speedBytesPerSec > 0 && task.totalBytes > task.downloadedBytes) {
                            task.etaSeconds = (task.totalBytes - task.downloadedBytes) / task.speedBytesPerSec
                        }
                        onUpdate(task)
                    }
                }

                if (cancelled.get()) {
                    task.state = IdmState.PAUSED
                    task.speedBytesPerSec = 0
                    task.etaSeconds = 0
                    onUpdate(task)
                    return@launch
                }

                // All segments completed — Merge files
                task.state = IdmState.MERGING
                task.speedBytesPerSec = 0
                task.etaSeconds = 0
                onUpdate(task)

                val destFile = mergeSegments(task, dir)
                task.outputPath = destFile.absolutePath
                task.state = IdmState.COMPLETED
                task.downloadedBytes = destFile.length()
                if (task.totalBytes <= 0) task.totalBytes = destFile.length()
                onUpdate(task)
                onComplete(task, destFile)

            } catch (e: CancellationException) {
                task.state = IdmState.PAUSED
                task.speedBytesPerSec = 0
                onUpdate(task)
            } catch (t: Throwable) {
                if (cancelled.get()) {
                    task.state = IdmState.PAUSED
                } else {
                    task.state = IdmState.FAILED
                    task.errorMessage = t.message ?: t.javaClass.simpleName
                    onError(task, t)
                }
                onUpdate(task)
            } finally {
                activeJobs.remove(task.id)
                cancelledFlags.remove(task.id)
            }
        }

        activeJobs[task.id] = job
    }

    /**
     * Downloads an individual segment using HTTP Range.
     */
    private fun downloadSegment(
        rawUrl: String,
        segment: IdmSegment,
        dir: File,
        cancelled: AtomicBoolean,
        onProgress: (bytesRead: Int) -> Unit
    ) {
        val partFile = File(dir, "segment_${segment.index}.part")
        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        // If part file is already complete
        if (segment.totalBytes in 1..existingBytes) {
            segment.downloadedBytes = segment.totalBytes
            segment.isCompleted = true
            return
        }

        segment.downloadedBytes = existingBytes

        val conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            if (segment.endByte > 0) {
                val start = segment.startByte + existingBytes
                setRequestProperty("Range", "bytes=$start-${segment.endByte}")
            }
        }

        conn.connect()
        val responseCode = conn.responseCode
        if (responseCode !in 200..299 && responseCode != 416) {
            throw IllegalStateException("HTTP $responseCode from server for segment ${segment.index}")
        }

        val raf = RandomAccessFile(partFile, "rw")
        raf.seek(existingBytes)

        val buffer = ByteArray(64 * 1024) // 64 KB buffer for high throughput
        val input = conn.inputStream

        try {
            while (!cancelled.get()) {
                val count = input.read(buffer)
                if (count == -1) break
                raf.write(buffer, 0, count)
                existingBytes += count
                segment.downloadedBytes = existingBytes
                onProgress(count)

                if (segment.endByte > 0 && existingBytes >= segment.totalBytes) {
                    segment.isCompleted = true
                    break
                }
            }
            if (existingBytes >= segment.totalBytes || segment.totalBytes <= 0) {
                segment.isCompleted = true
            }
        } finally {
            raf.close()
            input.close()
            conn.disconnect()
        }
    }

    /**
     * Merges individual segment files into the final destination file.
     */
    private fun mergeSegments(task: IdmTask, dir: File): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir

        val dest = File(publicDownloads, task.filename)
        val finalFile = if (dest.exists()) {
            val base = task.filename.substringBeforeLast('.')
            val ext = task.filename.substringAfterLast('.', "")
            File(publicDownloads, "${base}_${System.currentTimeMillis()}.${ext}")
        } else dest

        finalFile.outputStream().use { out ->
            for (i in 0 until task.segments.size) {
                val partFile = File(dir, "segment_$i.part")
                if (partFile.exists()) {
                    partFile.inputStream().use { input ->
                        input.copyTo(out, bufferSize = 128 * 1024)
                    }
                    partFile.delete()
                }
            }
        }

        dir.deleteRecursively()
        return finalFile
    }

    fun pause(taskId: String) {
        cancelledFlags[taskId]?.set(true)
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
    }

    fun cancel(taskId: String) {
        pause(taskId)
        taskWorkingDir(taskId).deleteRecursively()
    }

    private fun extractFilename(disposition: String?, url: String): String {
        if (!disposition.isNullOrBlank()) {
            val filenameMatch = Regex("filename\\*?=['\"]?(?:UTF-8'')?([^'\";\\n]+)['\"]?", RegexOption.IGNORE_CASE)
                .find(disposition)
            if (filenameMatch != null) {
                val raw = filenameMatch.groupValues[1]
                return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw).trim()
            }
        }

        val path = URL(url).path
        val lastSegment = path.substringAfterLast('/').substringBefore('?')
        return if (lastSegment.isNotBlank()) {
            runCatching { URLDecoder.decode(lastSegment, "UTF-8") }.getOrDefault(lastSegment)
        } else {
            "download_${System.currentTimeMillis()}"
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 VideoPocket/0.3.0"
    }
}

/**
 * Sliding window speed meter for smooth, accurate transfer rate readings.
 */
class SlidingSpeedTracker(private val windowDurationMs: Long = 3000L) {
    private data class Entry(val timestamp: Long, val bytes: Int)
    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun add(bytes: Int) {
        val now = System.currentTimeMillis()
        entries.addLast(Entry(now, bytes))
        prune(now)
    }

    @Synchronized
    fun speedBps(): Long {
        val now = System.currentTimeMillis()
        prune(now)
        if (entries.size < 2) return 0L
        val totalBytes = entries.sumOf { it.bytes.toLong() }
        val durationMs = (entries.last().timestamp - entries.first().timestamp).coerceAtLeast(100L)
        return (totalBytes * 1000L / durationMs).coerceAtLeast(0L)
    }

    private fun prune(now: Long) {
        while (entries.isNotEmpty() && (now - entries.first().timestamp) > windowDurationMs) {
            entries.removeFirst()
        }
    }
}
