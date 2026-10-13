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
    val height: Int = 1080,
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
    val canResume: Boolean = false
)

class DownloadQueueManager(
    private val application: Application,
    private val engine: ProbeEngine
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>()

    var tasks by mutableStateOf(listOf<DownloadTask>())
        private set

    var maxConcurrent by mutableStateOf(AppSettings.maxConcurrentDownloads)

    val activeTasks: List<DownloadTask>
        get() = tasks.filter { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.MERGING || it.state == DownloadState.SAVING || it.state == DownloadState.RESOLVING }

    val queuedTasks: List<DownloadTask>
        get() = tasks.filter { it.state == DownloadState.QUEUED || it.state == DownloadState.PAUSED }

    val completedTasks: List<DownloadTask>
        get() = tasks.filter { it.state == DownloadState.COMPLETED }

    val failedTasks: List<DownloadTask>
        get() = tasks.filter { it.state == DownloadState.FAILED || it.state == DownloadState.CANCELLED }

    fun enqueue(task: DownloadTask) {
        tasks = listOf(task) + tasks
        processNext()
    }

    fun pause(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        updateTask(taskId) {
            it.copy(state = DownloadState.PAUSED)
        }
        processNext()
    }

    fun resume(taskId: String) {
        updateTask(taskId) {
            it.copy(state = DownloadState.QUEUED)
        }
        processNext()
    }

    fun cancel(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        engine.cancelCurrent()
        PocketNotifier.dismiss(application)
        updateTask(taskId) {
            it.copy(state = DownloadState.CANCELLED)
        }
        processNext()
    }

    fun retry(taskId: String) {
        updateTask(taskId) {
            it.copy(state = DownloadState.QUEUED, progress = 0f, error = null)
        }
        processNext()
    }

    fun removeTask(taskId: String) {
        cancel(taskId)
        tasks = tasks.filter { it.id != taskId }
    }

    fun clearCompleted() {
        tasks = tasks.filter { it.state != DownloadState.COMPLETED }
    }

    private fun updateTask(id: String, transform: (DownloadTask) -> DownloadTask) {
        tasks = tasks.map { if (it.id == id) transform(it) else it }
    }

    private fun processNext() {
        if (activeTasks.size >= maxConcurrent) return
        val next = queuedTasks.firstOrNull { it.state == DownloadState.QUEUED } ?: return

        updateTask(next.id) { it.copy(state = DownloadState.DOWNLOADING) }

        val job = scope.launch {
            runTask(next)
        }
        activeJobs[next.id] = job
    }

    private suspend fun runTask(task: DownloadTask) {
        var lastTime = System.currentTimeMillis()
        var lastBytes = 0L

        try {
            val mediaInfo = task.rawInfo ?: withContext(Dispatchers.IO) {
                engine.inspect(task.url)
            }

            val result: ProbeResult = withContext(Dispatchers.IO) {
                if (task.isClip && task.clipRequest != null) {
                    engine.downloadClip(task.url, mediaInfo, task.clipRequest) { p, stage ->
                        val now = System.currentTimeMillis()
                        val speedStr = calculateSpeed(p, now - lastTime)
                        updateTask(task.id) {
                            it.copy(
                                progress = p,
                                speedText = speedStr,
                                state = if (p >= 85f) DownloadState.TRANSCODING else DownloadState.DOWNLOADING
                            )
                        }
                        PocketNotifier.showProgress(
                            application,
                            percent = p.toInt(),
                            stageText = stage,
                            title = task.title
                        )
                    }
                } else {
                    val action = when {
                        task.isAudioOnly -> ProbeAction.MP3
                        mediaInfo.isPlaylist -> ProbeAction.PLAYLIST_VIDEO
                        else -> ProbeAction.VIDEO
                    }
                    engine.download(task.url, mediaInfo, action, task.height, task.bitrate) { p ->
                        val now = System.currentTimeMillis()
                        val speedStr = calculateSpeed(p, now - lastTime)
                        updateTask(task.id) {
                            it.copy(
                                progress = p,
                                speedText = speedStr,
                                state = if (p >= 90f) DownloadState.SAVING else DownloadState.DOWNLOADING
                            )
                        }
                        PocketNotifier.showProgress(
                            application,
                            percent = p.toInt(),
                            stageText = "Downloading (${p.toInt()}%)",
                            title = task.title
                        )
                    }
                }
            }

            // Successfully downloaded
            updateTask(task.id) {
                it.copy(
                    state = DownloadState.COMPLETED,
                    progress = 100f,
                    outputUri = result.outputUri,
                    bytes = result.bytes,
                    speedText = ""
                )
            }

            // Save to Library
            val libraryItem = PocketLibraryItem(
                id = UUID.randomUUID().toString(),
                title = task.title,
                uriString = result.outputUri ?: "",
                isClip = task.isClip,
                clipRange = task.clipRequest?.let { "${it.startFormatted()} → ${it.endFormatted()}" },
                duration = if (task.isClip && task.clipRequest != null) task.clipRequest.durationFormatted()
                else mediaInfo.duration?.let { ClipRequest.formatSeconds(it) } ?: "Full",
                resolution = if (task.isAudioOnly) "MP3 Audio" else "${task.height}p",
                format = if (task.isAudioOnly) "MP3" else task.format.uppercase(),
                fileSizeBytes = result.bytes,
                createdAt = System.currentTimeMillis(),
                thumbnailUri = mediaInfo.thumbnailUrl,
                platform = task.platform,
                sourceMediaId = mediaInfo.id,
                sourceUrl = task.url
            )
            PocketLibraryManager.addItem(application, libraryItem)

            PocketNotifier.showSuccess(
                application,
                title = "Download Complete ✓",
                message = task.title,
                uriString = result.outputUri
            )

        } catch (e: Exception) {
            val prob = ProbeEngine.classify(e)
            updateTask(task.id) {
                it.copy(
                    state = DownloadState.FAILED,
                    error = prob.name
                )
            }
            PocketNotifier.dismiss(application)
        } finally {
            activeJobs.remove(task.id)
            processNext()
        }
    }

    private fun calculateSpeed(progress: Float, elapsedMs: Long): String {
        return if (progress in 1f..99f) "Active" else ""
    }
}
