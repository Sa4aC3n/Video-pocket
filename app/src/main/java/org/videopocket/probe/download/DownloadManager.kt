package org.videopocket.probe.download

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import org.videopocket.probe.core.*
import org.videopocket.probe.engine.PocketNotifier
import org.videopocket.probe.engine.ProbeEngine
import java.util.UUID
import kotlinx.coroutines.channels.Channel

enum class DownloadState(val label: String, val arabicLabel: String) {
    QUEUED("Queued", "في الانتظار"),
    RESOLVING("Resolving", "جاري الفحص"),
    READY("Ready", "جاهز"),
    DOWNLOADING("Downloading", "جاري التنزيل"),
    PAUSED("Paused", "متوقف مؤقتاً"),
    MERGING("Merging Streams", "دمج المسارات"),
    TRANSCODING("Processing", "معالجة الوسائط"),
    SAVING("Saving to Device", "حفظ في الجهاز"),
    COMPLETED("Completed", "مكتمل"),
    FAILED("Failed", "فشل"),
    CANCELLED("Cancelled", "ملغى");

    companion object {
        fun canTransition(from: DownloadState, to: DownloadState): Boolean {
            if (from == to) return true
            return when (from) {
                QUEUED -> to in setOf(RESOLVING, DOWNLOADING, PAUSED, CANCELLED)
                RESOLVING -> to in setOf(READY, DOWNLOADING, FAILED, CANCELLED)
                READY -> to in setOf(DOWNLOADING, PAUSED, CANCELLED)
                DOWNLOADING -> to in setOf(PAUSED, MERGING, TRANSCODING, SAVING, COMPLETED, FAILED, CANCELLED)
                PAUSED -> to in setOf(QUEUED, DOWNLOADING, CANCELLED)
                MERGING -> to in setOf(TRANSCODING, SAVING, COMPLETED, FAILED, CANCELLED)
                TRANSCODING -> to in setOf(SAVING, COMPLETED, FAILED, CANCELLED)
                SAVING -> to in setOf(COMPLETED, FAILED)
                COMPLETED -> false // Terminal state
                FAILED -> to in setOf(QUEUED, RESOLVING, DOWNLOADING) // Retry
                CANCELLED -> to in setOf(QUEUED, RESOLVING, DOWNLOADING) // Retry
            }
        }
    }
}

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val platform: String,
    val quality: String,
    val format: String,
    val height: Int = -1,
    val bitrate: Int = 192,
    val isAudioOnly: Boolean = false,
    val isClip: Boolean = false,
    val clipRequest: ClipRequest? = null,
    val rawInfo: MediaInfo? = null,
    var progress: Float = 0f,
    var speedText: String = "",
    var etaText: String = "",
    var state: DownloadState = DownloadState.QUEUED,
    var outputUri: String? = null,
    var bytes: Long = 0L,
    var error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val canPause: Boolean = false,
    val canResume: Boolean = false,
    val elapsedMs: Long? = null,
    val validationDetail: String? = null,
    val removeSponsors: Boolean = false
)

/**
 * Computes download speed and ETA from a sliding window of byte observations.
 *
 * Uses a 5-second rolling window so the speed adapts quickly to network changes
 * without jitter from tiny single-sample bursts.
 */
private class SpeedMeter {
    private data class Sample(val timeMs: Long, val bytes: Long)
    private val samples = ArrayDeque<Sample>(20)

    fun record(bytes: Long) {
        val now = System.currentTimeMillis()
        samples.addLast(Sample(now, bytes))
        // Keep only samples within the last 5 seconds
        while (samples.size > 1 && now - samples.first().timeMs > 5_000) samples.removeFirst()
    }

    /** Returns bytes/second, or null if not enough data. */
    fun speedBps(): Long? {
        if (samples.size < 2) return null
        val first = samples.first()
        val last = samples.last()
        val elapsed = last.timeMs - first.timeMs
        if (elapsed < 200) return null // too short to be reliable
        return ((last.bytes - first.bytes) * 1000L / elapsed).coerceAtLeast(0)
    }

    fun reset() = samples.clear()
}

/** Formats bytes/s to a human-readable string. */
private fun formatSpeed(bps: Long): String = when {
    bps >= 1_048_576 -> "%.1f MB/s".format(bps / 1_048_576.0)
    bps >= 1_024     -> "%.0f KB/s".format(bps / 1_024.0)
    else             -> "$bps B/s"
}

/** Formats remaining seconds to mm:ss or hh:mm:ss. */
private fun formatEta(seconds: Long): String = when {
    seconds >= 3_600 -> "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    else             -> "%d:%02d".format(seconds / 60, seconds % 60)
}

class DownloadQueueManager(
    private val application: Application,
    @Suppress("UNUSED_PARAMETER") engine: ProbeEngine
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>()
    private val engines = mutableMapOf<String, ProbeEngine>()
    private var closed = false
    private var recovering = true
    private var persistenceAvailable = true
    private val queueStore = QueueStore(application)
    private val saves = Channel<List<DownloadTask>>(Channel.CONFLATED)
    private val storageScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var persistenceError by mutableStateOf(false)
        private set

    private var concurrency by mutableStateOf(AppSettings.maxConcurrentDownloads)
    var tasks by mutableStateOf(listOf<DownloadTask>())
        private set
    var maxConcurrent: Int
        get() = concurrency
        set(value) { concurrency = value.coerceIn(1, 4); processNext() }

    init {
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { queueStore.load() }
                tasks = (tasks + saved).distinctBy { it.id }
            } catch (_: Exception) { persistenceError = true; persistenceAvailable = false }
            finally { recovering = false; persist(); processNext() }
        }
        storageScope.launch {
            for (snapshot in saves) {
                try { queueStore.save(snapshot) }
                catch (_: Exception) { withContext(Dispatchers.Main) { persistenceError = true } }
            }
            storageScope.cancel()
        }
    }

    private fun persist() {
        if (!recovering && persistenceAvailable) saves.trySend(tasks.toList())
    }

    val activeTasks get() = tasks.filter { it.state in ACTIVE_STATES }
    val queuedTasks get() = tasks.filter { it.state in setOf(DownloadState.QUEUED, DownloadState.PAUSED) }
    val completedTasks get() = tasks.filter { it.state == DownloadState.COMPLETED }
    val failedTasks get() = tasks.filter { it.state in setOf(DownloadState.FAILED, DownloadState.CANCELLED) }

    fun enqueue(task: DownloadTask) {
        if (closed || tasks.any {
            it.id == task.id ||
            it.state in ACTIVE_STATES + setOf(DownloadState.QUEUED, DownloadState.PAUSED) &&
            it.url == task.url && it.height == task.height && it.format == task.format &&
            it.isAudioOnly == task.isAudioOnly && it.bitrate == task.bitrate &&
            it.clipRequest == task.clipRequest
        }) return
        val validated = try { task.copy(url = LinkPolicy.validate(task.url)) }
        catch (_: Exception) { task.copy(state = DownloadState.FAILED, error = Problem.INVALID_URL.name) }
        tasks = listOf(validated) + tasks
        persist()
        processNext()
    }

    // Active transfer resume is not supported by the engine. Only waiting tasks can pause.
    fun pause(taskId: String) {
        updateTask(taskId) { if (it.state == DownloadState.QUEUED) it.copy(state = DownloadState.PAUSED) else it }
    }
    fun resume(taskId: String) {
        updateTask(taskId) { if (it.state == DownloadState.PAUSED) it.copy(state = DownloadState.QUEUED) else it }
        processNext()
    }
    fun cancel(taskId: String) {
        val task = tasks.firstOrNull { it.id == taskId } ?: return
        if (task.state in setOf(DownloadState.COMPLETED, DownloadState.FAILED, DownloadState.CANCELLED)) return
        val engine = engines[taskId]
        if (engine != null && !engine.cancelCurrent() && task.state != DownloadState.RESOLVING) return
        updateTask(taskId) { it.copy(state = DownloadState.CANCELLED, speedText = "", etaText = "") }
        PocketNotifier.dismiss(application, taskId.hashCode())
        processNext()
    }
    fun retry(taskId: String) {
        if (activeJobs.containsKey(taskId)) return
        updateTask(taskId) {
            if (it.state in setOf(DownloadState.FAILED, DownloadState.CANCELLED))
                it.copy(state = DownloadState.QUEUED, rawInfo = null, progress = -1f, error = null,
                    outputUri = null, bytes = 0, elapsedMs = null, validationDetail = null)
            else it
        }
        processNext()
    }
    fun removeTask(taskId: String) {
        cancel(taskId)
        if (!activeJobs.containsKey(taskId)) { tasks = tasks.filterNot { it.id == taskId }; persist() }
    }
    fun clearCompleted() { tasks = tasks.filterNot { it.state == DownloadState.COMPLETED }; persist() }
    fun close() {
        closed = true
        engines.values.forEach { it.cancelCurrent() }
        ServiceController.forceStop(application)
        persist()
        saves.close()
        scope.cancel()
    }

    private fun updateTask(id: String, transform: (DownloadTask) -> DownloadTask) {
        val previous = tasks.firstOrNull { it.id == id }
        tasks = tasks.map { if (it.id == id) transform(it) else it }
        val updated = tasks.firstOrNull { it.id == id }
        if (previous?.state != updated?.state || previous?.outputUri != updated?.outputUri) persist()
    }

    private fun processNext() {
        if (closed || recovering) return
        while (activeJobs.size < maxConcurrent) {
            val next = tasks.asReversed().firstOrNull { it.state == DownloadState.QUEUED } ?: break
            val engine = ProbeEngine(application)
            engines[next.id] = engine
            updateTask(next.id) { it.copy(state = DownloadState.RESOLVING, progress = -1f) }
            val job = scope.launch(start = CoroutineStart.LAZY) { runTask(next, engine) }
            activeJobs[next.id] = job
            job.start()
        }
    }

    private suspend fun runTask(task: DownloadTask, engine: ProbeEngine) {
        ServiceController.acquire(application)
        fun cancelled() = tasks.firstOrNull { it.id == task.id }?.state == DownloadState.CANCELLED
        val speedMeter = SpeedMeter()
        var lastBytesReported = 0L
        try {
            val media = task.rawInfo ?: withContext(Dispatchers.IO) { engine.inspect(task.url) }
            if (cancelled()) throw ProbeFailure(Problem.CANCELED)
            updateTask(task.id) { it.copy(state = DownloadState.DOWNLOADING, title = media.title) }

            val progress: (Float) -> Unit = { percent ->
                scope.launch {
                    if (!cancelled()) {
                        // Estimate downloaded bytes from percent × assumed total
                        val estimatedTotal = tasks.firstOrNull { it.id == task.id }?.bytes
                            ?.takeIf { it > 0 } ?: 0L
                        val currentBytes = if (estimatedTotal > 0 && percent > 0)
                            (estimatedTotal * percent / 100f).toLong()
                        else 0L

                        if (currentBytes > lastBytesReported) {
                            speedMeter.record(currentBytes)
                            lastBytesReported = currentBytes
                        }

                        val speedBps = speedMeter.speedBps()
                        val speedStr = speedBps?.let { formatSpeed(it) } ?: ""
                        val etaStr = if (speedBps != null && speedBps > 0 && percent in 1f..99f) {
                            val remaining = if (estimatedTotal > 0)
                                estimatedTotal - currentBytes
                            else 0L
                            if (remaining > 0) formatEta(remaining / speedBps) else ""
                        } else ""

                        updateTask(task.id) { it.copy(progress = percent, speedText = speedStr, etaText = etaStr) }
                        PocketNotifier.showProgress(
                            application, task.id.hashCode(), task.title, percent.toInt(),
                            buildString {
                                if (AppSettings.isArabic) append("تنزيل") else append("Downloading")
                                if (speedStr.isNotEmpty()) append("  $speedStr")
                                if (etaStr.isNotEmpty()) append("  ETA $etaStr")
                            }
                        )
                    }
                }
            }

            val result = withContext(Dispatchers.IO + NonCancellable) {
                if (cancelled()) throw ProbeFailure(Problem.CANCELED)
                val downloaded = if (task.isClip && task.clipRequest != null) {
                    engine.downloadClip(task.url, media, task.clipRequest) { p, _ -> progress(p) }
                } else {
                    val action = when {
                        task.isAudioOnly && media.isPlaylist -> ProbeAction.PLAYLIST_MP3
                        task.isAudioOnly -> ProbeAction.MP3
                        media.isPlaylist -> ProbeAction.PLAYLIST_VIDEO
                        else -> ProbeAction.VIDEO
                    }
                    engine.downloadWithFormat(
                        task.url, media, action,
                        if (task.height < 0) media.heights.maxOrNull() ?: 0 else task.height,
                        task.bitrate,
                        if (task.isAudioOnly) "auto" else task.format,
                        progress,
                        removeSponsors = task.removeSponsors
                    )
                }
                check(downloaded.passed && !downloaded.outputUri.isNullOrBlank())
                try {
                    LibraryIndexer.index(application, downloaded, media, task.url, task.platform, task.clipRequest)
                } catch (failure: Exception) {
                    downloaded.outputUris.ifEmpty { listOfNotNull(downloaded.outputUri) }.distinct().forEach { uri ->
                        runCatching { application.contentResolver.delete(android.net.Uri.parse(uri), null, null) }
                    }
                    throw failure
                }
                downloaded
            }

            updateTask(task.id) {
                it.copy(
                    state = DownloadState.COMPLETED, progress = 100f,
                    outputUri = result.outputUri, bytes = result.bytes,
                    elapsedMs = result.elapsedMs, validationDetail = result.detail,
                    speedText = "", etaText = ""
                )
            }
            PocketNotifier.showSuccess(
                application, task.id.hashCode(),
                if (AppSettings.isArabic) "اكتمل التنزيل" else "Download complete",
                task.title, result.outputUri
            )
        } catch (failure: Exception) {
            val problem = ProbeEngine.classify(failure)
            updateTask(task.id) {
                it.copy(
                    state = if (cancelled() || problem == Problem.CANCELED || failure is CancellationException)
                        DownloadState.CANCELLED else DownloadState.FAILED,
                    error = problem.name, speedText = "", etaText = ""
                )
            }
            PocketNotifier.dismiss(application, task.id.hashCode())
        } finally {
            speedMeter.reset()
            engines.remove(task.id)
            activeJobs.remove(task.id)
            ServiceController.release(application)
            processNext()
        }
    }

    companion object {
        val ACTIVE_STATES = setOf(
            DownloadState.RESOLVING, DownloadState.DOWNLOADING,
            DownloadState.MERGING, DownloadState.TRANSCODING, DownloadState.SAVING
        )
    }
}
