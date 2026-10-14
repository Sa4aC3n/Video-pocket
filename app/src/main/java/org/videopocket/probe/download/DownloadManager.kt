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
        if (closed || tasks.any { it.id == task.id }) return
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
        // Keep the slot until the blocking process exits and its files are cleaned up.
        processNext()
    }
    fun retry(taskId: String) {
        if (activeJobs.containsKey(taskId)) return
        updateTask(taskId) {
            if (it.state in setOf(DownloadState.FAILED, DownloadState.CANCELLED))
                it.copy(state = DownloadState.QUEUED, rawInfo = null, progress = -1f, error = null, outputUri = null, bytes = 0)
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
        fun cancelled() = tasks.firstOrNull { it.id == task.id }?.state == DownloadState.CANCELLED
        try {
            val media = task.rawInfo ?: withContext(Dispatchers.IO) { engine.inspect(task.url) }
            if (cancelled()) throw ProbeFailure(Problem.CANCELED)
            updateTask(task.id) { it.copy(state = DownloadState.DOWNLOADING) }
            val progress: (Float) -> Unit = { percent ->
                scope.launch {
                    if (!cancelled()) {
                        updateTask(task.id) { it.copy(progress = percent, speedText = "", etaText = "") }
                        PocketNotifier.showProgress(application, task.id.hashCode(), task.title, percent.toInt(),
                            if (AppSettings.isArabic) "تنزيل ومعالجة" else "Downloading and processing")
                    }
                }
            }
            // Finish indexing after publication even if the owning screen is destroyed.
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
                    engine.downloadWithFormat(task.url, media, action, if (task.height < 0) media.defaultHeight else task.height, task.bitrate, task.format, progress)
                }
                check(downloaded.passed && !downloaded.outputUri.isNullOrBlank())
                LibraryIndexer.index(application, downloaded, media, task.url, task.platform, task.clipRequest)
                downloaded
            }
            updateTask(task.id) { it.copy(state = DownloadState.COMPLETED, progress = 100f, outputUri = result.outputUri, bytes = result.bytes) }
            PocketNotifier.showSuccess(application, task.id.hashCode(),
                if (AppSettings.isArabic) "اكتمل التنزيل" else "Download complete", task.title, result.outputUri)
        } catch (failure: Exception) {
            val problem = ProbeEngine.classify(failure)
            updateTask(task.id) { it.copy(state = if (cancelled() || problem == Problem.CANCELED || failure is CancellationException)
                DownloadState.CANCELLED else DownloadState.FAILED, error = problem.name) }
            PocketNotifier.dismiss(application, task.id.hashCode())
        } finally {
            engines.remove(task.id)
            activeJobs.remove(task.id)
            processNext()
        }
    }
    companion object {
        val ACTIVE_STATES = setOf(DownloadState.RESOLVING, DownloadState.DOWNLOADING, DownloadState.MERGING, DownloadState.TRANSCODING, DownloadState.SAVING)
    }
}
