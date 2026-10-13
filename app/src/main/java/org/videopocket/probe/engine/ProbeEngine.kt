package org.videopocket.probe.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.StatFs
import android.provider.MediaStore
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import org.videopocket.probe.core.*
import org.videopocket.probe.R
import java.io.File
import java.util.UUID

enum class EngineState {
    NOT_INITIALIZED,
    INITIALIZING,
    READY,
    FAILED
}

interface MediaInspector { fun inspect(url: String): MediaInfo }
interface MediaDownloader {
    fun download(url: String, info: MediaInfo, action: ProbeAction, height: Int, bitrate: Int, progress: (Float) -> Unit): ProbeResult
}

/** Blocking methods run on Dispatchers.IO. Never expose yt-dlp output or URLs in diagnostics. */
class ProbeEngine(private val context: Context) : MediaInspector, MediaDownloader {
    private val engine = YoutubeDL.getInstance()
    private val workRoot = File(context.noBackupFilesDir, "probe-files")
    private var activeProcessId: String? = null

    fun cancelCurrent(): Boolean {
        val pid = activeProcessId ?: return false
        return try {
            val killed = engine.destroyProcessById(pid)
            activeProcessId = null
            killed
        } catch (_: Exception) { false }
    }
    

    @Synchronized fun initialize(): JSONObject {
        cachedInitResult?.let {
            state = EngineState.READY
            return it
        }
        return synchronized(initLock) {
            cachedInitResult?.let {
                state = EngineState.READY
                return it
            }
            state = EngineState.INITIALIZING
            try {
                FFmpeg.getInstance().init(context)
                engine.init(context)
                val targetFile = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
                if (!targetFile.exists()) {
                    targetFile.parentFile?.mkdirs()
                    context.resources.openRawResource(R.raw.ytdlp).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                for (name in listOf("libpython.so", "libffmpeg.so", "libqjs.so")) {
                    val f = File(context.applicationInfo.nativeLibraryDir, name)
                    if (!f.exists()) throw ProbeFailure(Problem.ENGINE, "Missing native binary: $name in ${context.applicationInfo.nativeLibraryDir}")
                }
                workRoot.mkdirs()
                try {
                    Os.setenv("PYTHONDONTWRITEBYTECODE", "1", true)
                    Os.setenv("PYTHONNOUSERSITE", "1", true)
                    val pythonHome = File(context.noBackupFilesDir, "youtubedl-android/packages/python/usr").absolutePath
                    val pythonLib = File(context.noBackupFilesDir, "youtubedl-android/packages/python/usr/lib/python3.12").absolutePath
                    val sitePackages = File(context.noBackupFilesDir, "youtubedl-android/packages/python/usr/lib/python3.12/site-packages").absolutePath
                    Os.setenv("PYTHONPATH", "$pythonLib:$sitePackages", true)
                    Os.setenv("PYTHONHOME", pythonHome, true)
                } catch (_: Throwable) {}

                val ver = "2026.08.19"
                val result = JSONObject().put("androidApi", Build.VERSION.SDK_INT)
                    .put("supportedAbis", Build.SUPPORTED_ABIS.joinToString(","))
                    .put("pageSize", Os.sysconf(OsConstants._SC_PAGESIZE))
                    .put("wrapper", "0.18.1").put("ytDlp", ver)
                    .put("quickJsPresent", true)
                cachedInitResult = result
                state = EngineState.READY
                result
            } catch (t: Throwable) {
                state = EngineState.FAILED
                throw t
            }
        }
    }

    private fun request(url: String, allowPlaylist: Boolean = false) = YoutubeDLRequest(LinkPolicy.validate(url))
        .addOption("--ignore-config")
        .addOption("--no-config-locations")
        .addOption("--no-plugin-dirs")
        .apply {
            if (!allowPlaylist) addOption("--no-playlist")
            else addOption("--yes-playlist")
        }
        .addOption("--no-cache-dir")
        .addOption("--no-warnings")
        .addOption("--socket-timeout", 20)
        .addOption("--retries", 2)
        .addOption("--fragment-retries", 2)
        .addOption("--no-check-formats")
        .addOption("--no-remote-components")

    override fun inspect(url: String): MediaInfo = inspect(url, false)

    fun inspect(url: String, forcePlaylist: Boolean = false): MediaInfo {
        val isLikelyPlaylist = forcePlaylist || url.contains("list=") || url.contains("/playlist") || url.contains("/sets/")
        val req = request(url, allowPlaylist = isLikelyPlaylist)
            .addOption("--dump-single-json")
            .addOption("--flat-playlist")
            .addOption("--skip-download")

        val response = engine.execute(req, null, null)
        val json = JSONObject(response.out)
        if (json.optBoolean("is_live") || json.optString("live_status") == "is_upcoming")
            throw ProbeFailure(Problem.LIVE_OR_PLAYLIST)

        val isPlaylist = json.optString("_type") in setOf("playlist", "multi_video")
        if (isPlaylist) {
            val entriesArray = json.optJSONArray("entries")
            val entryList = mutableListOf<PlaylistEntry>()
            if (entriesArray != null) {
                for (i in 0 until entriesArray.length()) {
                    val entryObj = entriesArray.optJSONObject(i) ?: continue
                    val entryId = entryObj.optString("id", "${i + 1}")
                    val entryTitle = entryObj.optString("title", "Item ${i + 1}")
                    val entryDuration = entryObj.optDouble("duration").takeIf { it.isFinite() }
                    val entryUrl = entryObj.optString("url", entryObj.optString("webpage_url", ""))
                    entryList.add(PlaylistEntry(entryId, entryTitle, entryDuration, entryUrl.ifBlank { null }))
                }
            }
            val title = json.optString("title", "Playlist").ifBlank { "Playlist" }
            val playlistId = json.optString("id").ifBlank { null }
            return MediaInfo(
                title = title,
                duration = json.optDouble("duration").takeIf { it.isFinite() },
                formats = emptyList(),
                isPlaylist = true,
                playlistCount = if (entryList.isNotEmpty()) entryList.size else json.optInt("playlist_count", 0),
                entries = entryList,
                id = playlistId
            )
        }

        val array = json.optJSONArray("formats")
        val rows = if (array == null) listOf(json) else (0 until array.length()).map { array.getJSONObject(it) }
        val formats = rows.filter { !it.optBoolean("has_drm") }.map { f ->
            fun codec(key: String): Boolean = f.optString(key).let { it.isNotBlank() && it != "none" && it != "null" }
            org.videopocket.probe.core.MediaFormat(
                f.optString("format_id", "best"), f.optString("ext"), f.optInt("height", 0),
                if (!f.has("vcodec")) f.optString("ext") !in setOf("mp3", "m4a", "aac", "ogg", "opus") else codec("vcodec"), if (!f.has("acodec")) true else codec("acodec"),
                f.optLong("filesize", f.optLong("filesize_approx", 0)).takeIf { it > 0 },
                f.optString("url").takeIf { it.isNotBlank() }
            )
        }
        if (formats.isEmpty()) throw ProbeFailure(Problem.UNSUPPORTED)
        val thumbnail = json.optString("thumbnail").takeIf { it.isNotBlank() }
            ?: json.optJSONArray("thumbnails")?.let { arr ->
                if (arr.length() > 0) arr.optJSONObject(arr.length() - 1)?.optString("url") else null
            }
        val directUrl = json.optString("url").takeIf { it.isNotBlank() }
            ?: formats.firstOrNull { it.url != null }?.url

        val subList = mutableListOf<SubtitleTrack>()
        fun parseSubObject(subObj: JSONObject?, isAuto: Boolean) {
            if (subObj == null) return
            val keys = subObj.keys()
            while (keys.hasNext()) {
                val lang = keys.next()
                val langArray = subObj.optJSONArray(lang) ?: continue
                for (k in 0 until langArray.length()) {
                    val subFormat = langArray.optJSONObject(k) ?: continue
                    val subUrl = subFormat.optString("url")
                    val ext = subFormat.optString("ext", "srt")
                    if (subUrl.isNotBlank()) {
                        subList.add(SubtitleTrack(lang, lang.uppercase(), ext, subUrl, isAuto))
                        break
                    }
                }
            }
        }
        parseSubObject(json.optJSONObject("subtitles"), false)
        if (subList.isEmpty()) {
            parseSubObject(json.optJSONObject("automatic_captions"), true)
        }
        val author = json.optString("uploader").ifBlank { json.optString("channel").ifBlank { json.optString("creator").ifBlank { null } } }
        val extractor = json.optString("extractor_key").ifBlank { json.optString("extractor").ifBlank { null } }
        val mediaId = json.optString("id").ifBlank { null }

        return MediaInfo(
            title = json.optString("title", "Video"),
            duration = json.optDouble("duration").takeIf { it.isFinite() },
            formats = formats,
            thumbnailUrl = thumbnail,
            streamUrl = directUrl,
            uploader = author,
            extractor = extractor,
            subtitles = subList,
            id = mediaId
        )
    }

    override fun download(url: String, info: MediaInfo, action: ProbeAction, height: Int, bitrate: Int, progress: (Float) -> Unit): ProbeResult {
        require(action != ProbeAction.INSPECT)
        require(bitrate in setOf(128, 192, 320))
        val isPlaylist = info.isPlaylist || action == ProbeAction.PLAYLIST_VIDEO || action == ProbeAction.PLAYLIST_MP3
        if (isPlaylist) {
            return downloadPlaylist(url, info, action, height, bitrate, progress)
        }
        val format = FormatPolicy.select(info, action, height)
        val estimate = info.formats.filter { it.id in format.split('+') }.mapNotNull { it.bytes }.sum()
        if (StatFs(context.noBackupFilesDir.path).availableBytes < maxOf(256L * 1024 * 1024, estimate * 3))
            throw ProbeFailure(Problem.SPACE)
        val id = UUID.randomUUID().toString()
        val folder = File(workRoot, id).apply { mkdirs() }
        activeProcessId = id
        val started = System.nanoTime()
        try {
            val req = request(url, allowPlaylist = false).addOption("--format", format)
                .addOption("--no-simulate").addOption("--newline")
                .addOption("--output", File(folder, "media.%(ext)s").absolutePath)
                .addOption("--max-filesize", "512M")
                .addOption("--merge-output-format", "mkv")
            if (action == ProbeAction.MP3) req.addOption("--extract-audio")
                .addOption("--audio-format", "mp3").addOption("--audio-quality", "${bitrate}K")

            engine.execute(req, id) { percent, _, _ -> progress(percent.coerceIn(0f, 100f)) }
            val output = folder.listFiles()?.singleOrNull { it.isFile && it.extension in setOf("mp4", "webm", "mkv", "mp3", "m4a", "mov") }
                ?: folder.listFiles()?.firstOrNull { it.isFile && it.extension in setOf("mp4", "webm", "mkv", "mp3", "m4a", "mov") }
                ?: throw ProbeFailure(Problem.CONVERSION)
            val tracks = validateOutput(output, action)
            val saved = publish(output, action)
            return ProbeResult(action, (System.nanoTime() - started) / 1_000_000, output.length(), saved, true, tracks)
        } finally {
            if (activeProcessId == id) activeProcessId = null
            folder.deleteRecursively()
        }
    }

    fun downloadClip(
        url: String,
        info: MediaInfo,
        clip: ClipRequest,
        progress: (Float, String) -> Unit
    ): ProbeResult {
        require(clip.startSeconds >= 0.0) { "Start time must be >= 0" }
        require(clip.endSeconds > clip.startSeconds) { "End time must be > start time" }
        val totalDur = info.duration ?: 3600.0
        require(clip.startSeconds < totalDur) { "Start time exceeds video duration" }

        val durationRatio = (clip.durationSeconds / totalDur).coerceIn(0.01, 1.0)
        val selectedFormat = info.formats.filter { it.height == clip.height }.maxByOrNull { it.bytes ?: 0 }
        val estTotal = selectedFormat?.bytes ?: (100L * 1024 * 1024)
        val estimate = (estTotal * durationRatio).toLong()
        if (StatFs(context.noBackupFilesDir.path).availableBytes < maxOf(128L * 1024 * 1024, estimate * 3))
            throw ProbeFailure(Problem.SPACE)

        val id = UUID.randomUUID().toString()
        val folder = File(workRoot, id).apply { mkdirs() }
        activeProcessId = id
        val started = System.nanoTime()

        try {
            val startFormatted = ClipRequest.formatSecondsFull(clip.startSeconds)
            val endFormatted = ClipRequest.formatSecondsFull(clip.endSeconds)
            val sectionArg = "*${startFormatted}-${endFormatted}"

            val formatStr = when {
                clip.isAudioOnly -> "bestaudio/best"
                clip.isBestQuality || clip.height == 0 -> "bestvideo+bestaudio/best"
                else -> {
                    val targetH = if (info.heights.contains(clip.height)) clip.height else info.defaultHeight
                    "bestvideo[height<=$targetH]+bestaudio/best[height<=$targetH]/best"
                }
            }

            progress(5f, "Preparing segment download...")

            val req = request(url, allowPlaylist = false)
                .addOption("--download-sections", sectionArg)
                .addOption("--force-keyframes-at-cuts")
                .addOption("--format", formatStr)
                .addOption("--no-simulate")
                .addOption("--newline")
                .addOption("--output", File(folder, "clip.%(ext)s").absolutePath)
                .addOption("--max-filesize", "512M")

            if (clip.isAudioOnly) {
                req.addOption("-x")
                    .addOption("--audio-format", "mp3")
                    .addOption("--audio-quality", "192K")
            } else {
                val outExt = if (clip.format.equals("webm", ignoreCase = true)) "webm" else "mp4"
                req.addOption("--merge-output-format", outExt)
            }

            engine.execute(req, id) { percent, _, _ ->
                val p = percent.coerceIn(0f, 100f)
                val stage = if (p < 80f) "Downloading segment (${p.toInt()}%)" else "Processing & cutting clip (${p.toInt()}%)"
                progress(p, stage)
            }

            progress(92f, "Finalizing & saving clip...")

            val output = folder.listFiles()?.singleOrNull { it.isFile && it.extension in setOf("mp4", "webm", "mkv", "mp3", "m4a", "mov") }
                ?: folder.listFiles()?.firstOrNull { it.isFile && it.extension in setOf("mp4", "webm", "mkv", "mp3", "m4a", "mov") }
                ?: throw ProbeFailure(Problem.CONVERSION, "Clip segment processing failed")

            val safeTitle = info.title.take(30).replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_').ifBlank { "Video" }
            fun formatCompact(sec: Double): String {
                val totalSec = sec.toLong().coerceAtLeast(0L)
                val m = totalSec / 60
                val s = totalSec % 60
                return String.format(java.util.Locale.US, "%02dm%02ds", m, s)
            }
            val qualityTag = if (clip.isAudioOnly) "audio" else "${clip.height}p"
            val displayBase = "${safeTitle}_${formatCompact(clip.startSeconds)}-${formatCompact(clip.endSeconds)}_${qualityTag}"
            val savedUri = publishWithName(output, if (clip.isAudioOnly) ProbeAction.MP3 else ProbeAction.CLIP, displayBase)

            progress(100f, "Clip Ready ✓")

            return ProbeResult(
                action = ProbeAction.CLIP,
                elapsedMs = (System.nanoTime() - started) / 1_000_000,
                bytes = output.length(),
                outputUri = savedUri,
                passed = true,
                detail = "Clip [${clip.startFormatted()} - ${clip.endFormatted()}] (${output.length() / 1024} KB)"
            )
        } finally {
            if (activeProcessId == id) activeProcessId = null
            folder.deleteRecursively()
        }
    }

    private fun downloadPlaylist(url: String, info: MediaInfo, action: ProbeAction, height: Int, bitrate: Int, progress: (Float) -> Unit): ProbeResult {
        if (StatFs(context.noBackupFilesDir.path).availableBytes < 512L * 1024 * 1024)
            throw ProbeFailure(Problem.SPACE)
        val id = UUID.randomUUID().toString()
        val folder = File(workRoot, id).apply { mkdirs() }
        activeProcessId = id
        val started = System.nanoTime()
        try {
            val req = request(url, allowPlaylist = true)
                .addOption("--no-simulate")
                .addOption("--newline")
                .addOption("--output", File(folder, "%(playlist_index)s-%(title).40s.%(ext)s").absolutePath)
                .addOption("--max-filesize", "512M")

            if (action == ProbeAction.MP3 || action == ProbeAction.PLAYLIST_MP3) {
                req.addOption("-x")
                    .addOption("--audio-format", "mp3")
                    .addOption("--audio-quality", "${bitrate}K")
            } else {
                req.addOption("--format", "bestvideo[height<=$height]+bestaudio/best[height<=$height]/best")
                    .addOption("--merge-output-format", "mkv")
            }

            engine.execute(req, id) { percent, _, _ -> progress(percent.coerceIn(0f, 100f)) }
            val outputs = folder.listFiles()?.filter { it.isFile && it.extension in setOf("mp4", "webm", "mkv", "mp3", "m4a", "mov") }?.sortedBy { it.name }
                ?: emptyList()
            if (outputs.isEmpty()) throw ProbeFailure(Problem.CONVERSION, "No playlist items downloaded")
            val totalBytes = outputs.sumOf { it.length() }
            val publishedUris = outputs.map { publish(it, action) }
            val detail = "${outputs.size} playlist items saved (${totalBytes / 1024} KB)"
            return ProbeResult(action, (System.nanoTime() - started) / 1_000_000, totalBytes, publishedUris.firstOrNull(), true, detail)
        } finally {
            if (activeProcessId == id) activeProcessId = null
            folder.deleteRecursively()
        }
    }

    private fun validateOutput(file: File, action: ProbeAction): String {
        if (file.length() == 0L) throw ProbeFailure(Problem.CONVERSION)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            val types = tracks.map { it.getString(MediaFormat.KEY_MIME).orEmpty() }
            val audio = types.any { it.startsWith("audio/") }
            val video = types.any { it.startsWith("video/") }
            if (action == ProbeAction.MP3 && (!audio || video || "audio/mpeg" !in types)) throw ProbeFailure(Problem.CONVERSION)
            if (action == ProbeAction.MERGE && (!audio || !video)) throw ProbeFailure(Problem.CONVERSION)
            if (action == ProbeAction.VIDEO && !video) throw ProbeFailure(Problem.CONVERSION)
            return types.joinToString(",") + "; container/track check only; playback sync requires listening"
        } finally { extractor.release() }
    }

    private fun publish(file: File, action: ProbeAction): String {
        val mime = when (file.extension) {
            "mp3" -> "audio/mpeg"; "mp4" -> "video/mp4"; "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"; "m4a" -> "audio/mp4"; else -> "video/quicktime"
        }
        val safeName = file.nameWithoutExtension.take(40).replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val displayName = if (safeName.isNotBlank()) "${safeName}-${System.currentTimeMillis()}.${file.extension}"
            else "VideoPocket-${action.name}-${System.currentTimeMillis()}.${file.extension}"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/VideoPocket")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw ProbeFailure(Problem.SPACE)
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw ProbeFailure(Problem.SPACE)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return uri.toString()
        } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
    }

    fun publishWithName(file: File, action: ProbeAction, baseName: String): String {
        val mime = when (file.extension) {
            "mp3" -> "audio/mpeg"; "mp4" -> "video/mp4"; "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"; "m4a" -> "audio/mp4"; else -> "video/quicktime"
        }
        val cleanBase = baseName.take(60).replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
        val displayName = "${cleanBase}-${System.currentTimeMillis() % 10000}.${file.extension}"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/VideoPocket")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw ProbeFailure(Problem.SPACE)
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw ProbeFailure(Problem.SPACE)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return uri.toString()
        } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
    }

    companion object {
        @Volatile private var cachedInitResult: JSONObject? = null
        private val initLock = Any()

        @Volatile var state: EngineState = EngineState.NOT_INITIALIZED
            private set

        val isInitialized: Boolean get() = cachedInitResult != null
        val isReady: Boolean get() = state == EngineState.READY && cachedInitResult != null
        val cachedRuntime: JSONObject? get() = cachedInitResult

        fun classify(error: Throwable): Problem {
            if (error is ProbeFailure) return error.problem
            val message = error.message.orEmpty().lowercase()
            return when {
                "cancel" in message || "interrupt" in message || error is InterruptedException -> Problem.CANCELED
                "no space" in message -> Problem.SPACE
                "bot" in message || "captcha" in message || "challenge" in message || "cf-ray" in message || "confirm you're not a bot" in message -> Problem.RESTRICTED
                "sign in to confirm" in message -> Problem.RESTRICTED
                "403" in message && ("forbidden" in message || "denied" in message) -> Problem.RESTRICTED
                ("sign in" in message || "login" in message || "log in" in message || "this account is private" in message) -> Problem.LOGIN_REQUIRED
                "unsupported url" in message || "requested format" in message -> Problem.UNSUPPORTED
                "removed" in message || "not found" in message || "404" in message -> Problem.REMOVED
                "403" in message || "429" in message || "restricted" in message || "rate limit" in message || "too many requests" in message -> Problem.RESTRICTED
                "timed out" in message || "resolve" in message || "network" in message -> Problem.NETWORK
                "ffmpeg" in message -> Problem.CONVERSION
                else -> Problem.ENGINE
            }
        }
    }
}

