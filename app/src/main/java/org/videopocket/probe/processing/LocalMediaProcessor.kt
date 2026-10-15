package org.videopocket.probe.processing

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.StatFs
import com.yausername.ffmpeg.FFmpeg
import org.videopocket.probe.core.*
import org.videopocket.probe.engine.ProbeEngine
import org.videopocket.probe.storage.Workspaces
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ProcessingFailure(val code: String) : Exception(code)
data class ProcessingUpdate(val percent: Float?, val stage: String)
data class ProcessingResult(val media: SavedMedia, val inputBytes: Long, val encoder: String)

/** Blocking, local-only engine. Call on IO; bundled runtime extraction makes no network requests. */
class LocalMediaProcessor(private val context: Context) {
    private val cancelled = AtomicBoolean(false)
    private val lock = Any()
    @Volatile private var process: Process? = null
    @Volatile private var publishing = false
    private val binary get() = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

    fun cancel(): Boolean = synchronized(lock) {
        if (publishing) return@synchronized false
        cancelled.set(true)
        process?.destroyForcibly()
        true
    }
    private fun checkCancelled() {
        if (cancelled.get() || Thread.currentThread().isInterrupted) throw ProcessingFailure("CANCELLED")
    }
    private fun initialize() {
        if (!NativeRuntimePolicy.supportsPageSize(android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)))
            throw ProcessingFailure("PAGE_SIZE_UNSUPPORTED")
        BundledFfmpeg.initialize(context)
        if (!binary.isFile || !binary.canExecute()) throw ProcessingFailure("ENGINE_UNAVAILABLE")
    }
    private fun execute(args: List<String>, directory: File, timeoutSeconds: Long = 3600, line: (String) -> Unit = {}): String {
        checkCancelled()
        val builder = ProcessBuilder(listOf(binary.absolutePath) + listOf("-hide_banner", "-nostdin") + args)
            .directory(directory).redirectErrorStream(true)
        builder.environment().apply {
            put("LD_LIBRARY_PATH", BundledFfmpeg.libraryPath(context))
            put("TMPDIR", directory.absolutePath)
        }
        val child = synchronized(lock) { checkCancelled(); builder.start().also { process = it } }
        val timedOut = AtomicBoolean(false)
        val noSpace = AtomicBoolean(false)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        val watchdog = WATCHDOG.scheduleWithFixedDelay({
            if (System.nanoTime() > deadline) { timedOut.set(true); child.destroyForcibly() }
            if (runCatching { StatFs(directory.path).availableBytes < RESERVE }.getOrDefault(false)) {
                noSpace.set(true); child.destroyForcibly()
            }
        }, 1, 1, TimeUnit.SECONDS)
        val log = StringBuilder()
        try {
            child.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { text ->
                    checkCancelled()
                    if (log.length < 256_000) log.append(text.take(4096)).append('\n')
                    line(text)
                }
            }
            val exit = child.waitFor()
            checkCancelled()
            if (timedOut.get()) throw ProcessingFailure("TIMEOUT")
            if (noSpace.get()) throw ProcessingFailure("SPACE")
            if (exit != 0) throw ProcessingFailure("ENCODING_FAILED")
            return log.toString()
        } finally {
            watchdog.cancel(false)
            child.destroyForcibly()
            synchronized(lock) { if (process === child) process = null }
        }
    }

    /** A listed encoder must also successfully encode a tiny local sample before it is offered. */
    fun capabilities(): ProcessingCapabilities {
        initialize()
        cachedCapabilities?.let { return it }
        val folder = Workspaces.create(context, "capabilities-${UUID.randomUUID()}")
        try {
            val advertised = ProcessingPolicy.parseCodecs(execute(listOf("-encoders"), folder, 20))
            val muxers = ProcessingPolicy.parseMuxers(execute(listOf("-muxers"), folder, 20))
            val verified = mutableSetOf<String>()
            for (encoder in listOf("libx264", "libx265", "libvpx-vp9", "aac", "libopus", "libmp3lame", "pcm_s16le")) {
                if (encoder !in advertised) continue
                checkCancelled()
                val video = encoder in setOf("libx264", "libx265", "libvpx-vp9")
                val source = if (video) "color=c=black:s=64x64:r=10" else "anullsrc=r=44100:cl=stereo"
                val args = mutableListOf("-f", "lavfi", "-i", source, "-t", "0.1", if (video) "-an" else "-vn", if (video) "-c:v" else "-c:a", encoder, "-threads", "2")
                if (encoder == "libx265") args.addAll(listOf("-x265-params", "pools=1:frame-threads=1"))
                args.addAll(listOf("-f", "null", "-"))
                try { execute(args, folder, 20); verified.add(encoder) }
                catch (error: ProcessingFailure) { if (error.code in setOf("CANCELLED", "SPACE")) throw error }
            }
            return ProcessingCapabilities(verified, muxers).also { cachedCapabilities = it }
        } finally { Workspaces.release(folder) }
    }

    private data class Source(val seconds: Double, val width: Int, val height: Int, val audio: Boolean, val video: Boolean)
    private fun inspect(file: File): Source {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            if (extractor.drmInitData != null) throw ProcessingFailure("DRM_UNSUPPORTED")
            val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            val video = tracks.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            val audio = tracks.any { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            val duration = tracks.filter { it.containsKey(MediaFormat.KEY_DURATION) }.maxOfOrNull { it.getLong(MediaFormat.KEY_DURATION) } ?: 0
            if (duration <= 0) throw ProcessingFailure("DURATION_UNAVAILABLE")
            if (video?.containsKey(MediaFormat.KEY_COLOR_TRANSFER) == true && video.getInteger(MediaFormat.KEY_COLOR_TRANSFER) in
                setOf(MediaFormat.COLOR_TRANSFER_ST2084, MediaFormat.COLOR_TRANSFER_HLG)) throw ProcessingFailure("HDR_UNSUPPORTED")
            var width = video?.getInteger(MediaFormat.KEY_WIDTH) ?: 0
            var height = video?.getInteger(MediaFormat.KEY_HEIGHT) ?: 0
            if (video?.containsKey(MediaFormat.KEY_ROTATION) == true && video.getInteger(MediaFormat.KEY_ROTATION) in setOf(90, 270)) {
                val temp = width; width = height; height = temp
            }
            return Source(duration / 1_000_000.0, width, height, audio, video != null)
        } finally { extractor.release() }
    }

    fun run(request: ProcessingRequest, onProgress: (ProcessingUpdate) -> Unit): ProcessingResult {
        checkCancelled()
        val capabilities = capabilities()
        val uri = Uri.parse(request.uri)
        if (uri.scheme != "content") throw ProcessingFailure("INVALID_SOURCE")
        val folder = Workspaces.create(context, "processing-${UUID.randomUUID()}")
        try {
            onProgress(ProcessingUpdate(null, "COPYING"))
            val input = File(folder, "input.media")
            context.contentResolver.openInputStream(uri)?.use { source ->
                input.outputStream().use { destination ->
                    val buffer = ByteArray(256 * 1024)
                    var count: Int
                    while (source.read(buffer).also { count = it } >= 0) {
                        checkCancelled()
                        if (StatFs(folder.path).availableBytes < RESERVE + count * 3L) throw ProcessingFailure("SPACE")
                        destination.write(buffer, 0, count)
                    }
                }
            } ?: throw ProcessingFailure("SOURCE_UNAVAILABLE")
            if (input.length() <= 0) throw ProcessingFailure("SOURCE_UNAVAILABLE")
            val source = inspect(input)
            val seconds = try { ProcessingPolicy.duration(request, source.seconds) }
                catch (_: IllegalArgumentException) { throw ProcessingFailure("INVALID_RANGE") }
            val audioOnly = request.mode == ProcessingMode.EXTRACT_AUDIO
            if (audioOnly && !source.audio || !audioOnly && !source.video) throw ProcessingFailure("MISSING_TRACK")
            val format = if (request.mode == ProcessingMode.COMPRESS) "mp4" else request.format.lowercase(Locale.ROOT)
            if (format !in capabilities.formats(audioOnly)) throw ProcessingFailure("FORMAT_UNAVAILABLE")
            val encoder = if (audioOnly) capabilities.audioEncoder(format)!! else capabilities.videoEncoder(format, request.preset)!!
            val output = File(folder, "output.$format")
            val videoSize = if (source.video) ProcessingPolicy.dimensions(source.width, source.height,
                if (request.mode == ProcessingMode.COMPRESS) request.preset.maxEdge else maxOf(source.width, source.height)) else 0 to 0
            var targetBitrate = request.targetBytes?.let {
                try { ProcessingPolicy.videoBitrate(it, seconds, source.audio) }
                catch (_: IllegalArgumentException) { throw ProcessingFailure("TARGET_TOO_SMALL") }
            }
            if (audioOnly && request.targetBytes != null) throw ProcessingFailure("TARGET_UNSUPPORTED")
            for (attempt in 0..1) {
                checkCancelled()
                onProgress(ProcessingUpdate(null, if (attempt == 0) "ENCODING" else "OPTIMIZING"))
                val args = mutableListOf("-y", "-protocol_whitelist", "file,pipe", "-i", input.path)
                if (request.mode == ProcessingMode.TRIM) args.addAll(listOf("-ss", String.format(Locale.US, "%.6f", request.startSeconds), "-t", String.format(Locale.US, "%.6f", seconds)))
                args.addAll(listOf("-map_metadata", "-1", "-map_chapters", "-1"))
                if (audioOnly) {
                    args.addAll(listOf("-map", "0:a:0", "-vn", "-c:a", encoder))
                } else {
                    args.addAll(listOf("-map", "0:v:0", "-map", "0:a:0?", "-c:v", encoder,
                        "-vf", "scale=${videoSize.first}:${videoSize.second}", "-pix_fmt", "yuv420p", "-threads", "2"))
                    if (encoder in setOf("libx264", "libx265")) args.addAll(listOf("-preset", "veryfast"))
                    if (encoder == "libx265") args.addAll(listOf("-x265-params", "pools=1:frame-threads=1"))
                    if (targetBitrate != null) args.addAll(listOf("-b:v", targetBitrate.toString(), "-maxrate", targetBitrate.toString(), "-bufsize", (targetBitrate * 2).toString()))
                    else args.addAll(listOf("-crf", request.preset.crf.toString()))
                    if (encoder == "libvpx-vp9" && targetBitrate == null) args.addAll(listOf("-b:v", "0"))
                    if (source.audio) args.addAll(listOf("-c:a", capabilities.audioEncoder(format)!!))
                }
                if (source.audio && format != "wav") args.addAll(listOf("-b:a", "128k"))
                if (format in setOf("mp4", "m4a")) args.addAll(listOf("-movflags", "+faststart"))
                args.addAll(listOf("-progress", "pipe:1", "-nostats", "-f", capabilities.muxer(format), output.path))
                var lastUpdate = 0L
                execute(args, folder) { line ->
                    val progress = ProcessingPolicy.progress(line, (seconds * 1_000_000).toLong())
                    val now = System.nanoTime()
                    if (progress != null && now - lastUpdate > 250_000_000L) {
                        lastUpdate = now
                        onProgress(ProcessingUpdate(progress, if (attempt == 0) "ENCODING" else "OPTIMIZING"))
                    }
                }
                if (request.targetBytes == null || output.length() <= request.targetBytes) break
                if (attempt == 1) throw ProcessingFailure("TARGET_NOT_REACHED")
                targetBitrate = ((targetBitrate ?: 0) * request.targetBytes.toDouble() / output.length() * 0.85).toLong()
                if (targetBitrate < 64_000) throw ProcessingFailure("TARGET_TOO_SMALL")
            }
            checkCancelled()
            onProgress(ProcessingUpdate(null, "VALIDATING"))
            val actual = inspect(output)
            if (output.length() <= 0 || audioOnly && (!actual.audio || actual.video) || !audioOnly && !actual.video || source.audio && !actual.audio ||
                kotlin.math.abs(actual.seconds - seconds) > maxOf(1.0, seconds * 0.05)) throw ProcessingFailure("INVALID_OUTPUT")
            if (request.mode == ProcessingMode.COMPRESS && output.length() >= input.length()) throw ProcessingFailure("NO_SIZE_REDUCTION")
            synchronized(lock) { checkCancelled(); publishing = true }
            onProgress(ProcessingUpdate(null, "SAVING"))
            val savedUri = ProbeEngine(context).publishWithName(output, if (audioOnly) ProbeAction.MP3 else ProbeAction.VIDEO,
                request.title + "_" + request.mode.name.lowercase(Locale.ROOT))
            // Original URI was opened for read only. Only our own new row may be rolled back.
            try {
                val media = SavedMedia.read(context, savedUri)
                PocketLibraryManager.addItem(context, PocketLibraryItem(
                    id = UUID.randomUUID().toString(), title = request.title + " · " + request.mode.name,
                    uriString = savedUri, isClip = request.mode == ProcessingMode.TRIM,
                    clipRange = if (request.mode == ProcessingMode.TRIM) "${ClipRequest.formatSeconds(request.startSeconds)} → ${ClipRequest.formatSeconds(request.endSeconds!!)}" else null,
                    duration = media.durationMs?.let { ClipRequest.formatSeconds(it / 1000.0) } ?: "—",
                    resolution = media.height?.let { "${it}p" } ?: if (audioOnly) "Audio" else "—",
                    format = media.extension, fileSizeBytes = media.bytes, platform = "Local"
                ))
                onProgress(ProcessingUpdate(100f, "COMPLETE"))
                return ProcessingResult(media, input.length(), encoder)
            } catch (error: Exception) {
                runCatching { context.contentResolver.delete(Uri.parse(savedUri), null, null) }
                throw error
            }
        } finally { Workspaces.release(folder) }
    }
    companion object {
        private const val RESERVE = 64L * 1024 * 1024
        @Volatile private var cachedCapabilities: ProcessingCapabilities? = null
        private val WATCHDOG = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "media-watchdog").apply { isDaemon = true } }
    }
}
