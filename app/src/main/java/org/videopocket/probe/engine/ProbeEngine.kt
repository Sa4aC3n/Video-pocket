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
import org.videopocket.probe.storage.Workspaces

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
    @Volatile private var activeProcessId: String? = null
    @Volatile private var cancelRequested = false
    private val operationLock = Any()
    private var publishing = false

    fun cancelCurrent(): Boolean = synchronized(operationLock) {
        val pid = activeProcessId ?: return@synchronized false
        if (publishing) return@synchronized false
        cancelRequested = true
        runCatching { engine.destroyProcessById(pid) }
        true
    }

    private fun begin(id: String) = synchronized(operationLock) {
        check(activeProcessId == null) { "Engine already has an operation" }
        cancelRequested = false
        publishing = false
        activeProcessId = id
    }

    private fun checkCancelled() {
        if (cancelRequested || Thread.currentThread().isInterrupted) throw ProbeFailure(Problem.CANCELED)
    }

    private fun beginPublishing() = synchronized(operationLock) {
        checkCancelled()
        // Once publication starts it finishes atomically; cancel is no longer accepted.
        publishing = true
    }

    /** Bounds disk use and stalled native work, including playlist downloads between callbacks. */
    private fun <T> withResourceGuard(id: String, timeoutMinutes: Long = 60, block: () -> T): T {
        val failure = java.util.concurrent.atomic.AtomicReference<Problem?>(null)
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(timeoutMinutes)
        val guard = WATCHDOG.scheduleWithFixedDelay({
            val reason = when {
                runCatching { StatFs(context.noBackupFilesDir.path).availableBytes < 64L * 1024 * 1024 }.getOrDefault(false) -> Problem.SPACE
                System.nanoTime() >= deadline -> Problem.NETWORK
                else -> null
            }
            if (reason != null) synchronized(operationLock) {
                if (activeProcessId == id && !publishing) {
                    failure.compareAndSet(null, reason)
                    runCatching { engine.destroyProcessById(id) }
                }
            }
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS)
        try {
            return block().also { failure.get()?.let { throw ProbeFailure(it) } }
        } catch (error: Exception) {
            failure.get()?.let { throw ProbeFailure(it) }
            throw error
        } finally { guard.cancel(false) }
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
                if (!NativeRuntimePolicy.supportsPageSize(Os.sysconf(OsConstants._SC_PAGESIZE)))
                    throw ProbeFailure(Problem.ENGINE, NativeRuntimePolicy.PAGE_SIZE_MESSAGE)
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

                val ver = engine.version(context) ?: "Unknown"
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

        initialize()
        val id = UUID.randomUUID().toString()
        begin(id)
        val response = try {
            checkCancelled()
            withResourceGuard(id, 10) { engine.execute(req, id, null) }.also { checkCancelled() }
        } finally { activeProcessId = null }
        val json = JSONObject(response.out)
        if (json.optBoolean("has_drm")) throw ProbeFailure(Problem.UNSUPPORTED)

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
            fun codec(key: String): String? = f.optString(key).takeIf { it.isNotBlank() && it != "null" }
            val videoCodec = codec("vcodec")
            val audioCodec = codec("acodec")
            val reportedHeight = f.optInt("height", 0).coerceAtLeast(0)
            val rawLang = f.optString("language").takeIf { it.isNotBlank() && it != "null" && it != "und" }
            val rawLangName = f.optString("language_name").takeIf { it.isNotBlank() && it != "null" }
            val note = f.optString("format_note").takeIf { it.isNotBlank() && it != "null" }
            val formatId = f.optString("format_id", "best").ifBlank { "best" }

            var langCode: String? = rawLang?.takeIf { it != "orig" }
            var langName: String? = rawLangName

            if (langCode == null && !note.isNullOrBlank()) {
                val candidate = if (note.contains(",")) note.substringAfter(",").trim() else note.trim()
                val clean = candidate
                    .replace(Regex("""\[.*?\]|\(.*?\)|dubbed|original|default""", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (clean.isNotBlank()) {
                    val lower = clean.lowercase()
                    val mapped = KNOWN_LANG_MAP[lower]
                    if (mapped != null) {
                        langCode = mapped
                        langName = clean
                    } else if (clean.length in 2..3 && clean.all { it.isLetter() }) {
                        langCode = clean.lowercase()
                    } else if (!clean.equals("medium", ignoreCase = true) &&
                        !clean.equals("low", ignoreCase = true) &&
                        !clean.equals("tiny", ignoreCase = true) &&
                        !clean.equals("high", ignoreCase = true) &&
                        !clean.equals("ultra", ignoreCase = true) &&
                        !clean.equals("audio", ignoreCase = true)) {
                        langName = clean
                    }
                }
            }

            if (langCode == null) {
                val match = Regex("""-(?:dubbed-)?([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)$""").find(formatId)
                if (match != null) {
                    val candidate = match.groupValues[1].lowercase()
                    if (candidate !in setOf("mp4", "m4a", "webm", "opus", "flv")) {
                        langCode = candidate
                    }
                }
            }

            val isOriginal = note?.contains("original", ignoreCase = true) == true ||
                rawLang == "orig" ||
                f.optInt("language_preference", 0) > 0 ||
                (!formatId.contains("-") && note?.contains("dub", ignoreCase = true) != true)

            org.videopocket.probe.core.MediaFormat(
                formatId, f.optString("ext"), reportedHeight,
                reportedHeight > 0 || (videoCodec != null && videoCodec != "none"),
                audioCodec != null && audioCodec != "none",
                f.optLong("filesize", f.optLong("filesize_approx", 0)).takeIf { it > 0 },
                f.optString("url").takeIf { it.isNotBlank() },
                videoKnown = reportedHeight > 0 || videoCodec != null,
                audioKnown = audioCodec != null,
                language = langCode,
                isOriginalAudio = isOriginal,
                formatNote = langName ?: note
            )
        }
        if (formats.isEmpty()) throw ProbeFailure(Problem.UNSUPPORTED)

        val audioFormats = formats.filter { it.audio && !it.video }.sortedByDescending { it.bytes ?: 0 }
        val audioTracks = mutableListOf<org.videopocket.probe.core.MediaAudioTrack>()
        val seenLangs = mutableSetOf<String>()
        val origTrack = audioFormats.firstOrNull { it.isOriginalAudio } ?: audioFormats.firstOrNull()
        if (origTrack != null) {
            val code = origTrack.language ?: "orig"
            val display = if (code != "orig" && code != "und") {
                val ar = getArabicDisplayLanguage(code)
                "$ar (الأصلي)"
            } else {
                "الصوت الأصلي (Original)"
            }
            audioTracks.add(org.videopocket.probe.core.MediaAudioTrack(origTrack.id, code, display, true))
            seenLangs.add(code)
            seenLangs.add(origTrack.id)
        }
        audioFormats.forEach { fmt ->
            val code = fmt.language ?: fmt.id
            if (code !in seenLangs && fmt.id != origTrack?.id) {
                seenLangs.add(code)
                val display = if (!fmt.language.isNullOrBlank() && fmt.language != "und" && fmt.language != "orig") {
                    getArabicDisplayLanguage(fmt.language)
                } else if (!fmt.formatNote.isNullOrBlank()) {
                    fmt.formatNote
                } else {
                    fmt.id
                }
                audioTracks.add(org.videopocket.probe.core.MediaAudioTrack(fmt.id, fmt.language ?: fmt.id, display, fmt.isOriginalAudio))
            }
        }

        val thumbnail = json.optString("thumbnail").takeIf { it.isNotBlank() }
            ?: json.optJSONArray("thumbnails")?.let { arr ->
                if (arr.length() > 0) arr.optJSONObject(arr.length() - 1)?.optString("url") else null
            }
        val directUrl = json.optString("url").takeIf { it.isNotBlank() }
            ?: formats.firstOrNull { it.url != null }?.url

        val subList = mutableListOf<SubtitleTrack>()
        val seenSubLangs = mutableSetOf<String>()
        fun parseSubObject(subObj: JSONObject?, isAuto: Boolean) {
            if (subObj == null) return
            val keys = subObj.keys()
            while (keys.hasNext()) {
                val lang = keys.next()
                if (lang in seenSubLangs) continue
                val langArray = subObj.optJSONArray(lang) ?: continue
                for (k in 0 until langArray.length()) {
                    val subFormat = langArray.optJSONObject(k) ?: continue
                    val subUrl = subFormat.optString("url")
                    val ext = subFormat.optString("ext", "srt")
                    val rawName = subFormat.optString("name").takeIf { it.isNotBlank() && it != "null" }
                    if (subUrl.isNotBlank()) {
                        val localizedName = runCatching {
                            val loc = java.util.Locale(lang)
                            val ar = loc.getDisplayLanguage(java.util.Locale("ar"))
                            if (ar.isNotBlank() && !ar.equals(lang, ignoreCase = true)) ar else loc.displayLanguage
                        }.getOrNull()?.takeIf { it.isNotBlank() } ?: rawName ?: lang.uppercase()
                        val fullName = if (isAuto) "$localizedName (تلقائي)" else localizedName
                        subList.add(SubtitleTrack(lang, fullName, ext, subUrl, isAuto))
                        seenSubLangs.add(lang)
                        break
                    }
                }
            }
        }
        parseSubObject(json.optJSONObject("subtitles"), false)
        parseSubObject(json.optJSONObject("automatic_captions"), true)
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
            id = mediaId,
            audioTracks = audioTracks
        )
    }

    override fun download(url: String, info: MediaInfo, action: ProbeAction, height: Int, bitrate: Int, progress: (Float) -> Unit): ProbeResult =
        downloadWithFormat(url, info, action, height, bitrate, "auto", progress, false)

    fun downloadWithFormat(
        url: String,
        info: MediaInfo,
        action: ProbeAction,
        height: Int,
        bitrate: Int,
        outputFormat: String,
        progress: (Float) -> Unit,
        removeSponsors: Boolean = false,
        selectedAudioTrackId: String? = null,
        selectedSubtitleLang: String? = null
    ): ProbeResult {
        require(outputFormat in setOf("auto", "mp4", "mkv", "webm"))
        initialize()
        require(action != ProbeAction.INSPECT)
        require(bitrate in setOf(128, 192, 320))
        val isPlaylist = info.isPlaylist || action == ProbeAction.PLAYLIST_VIDEO || action == ProbeAction.PLAYLIST_MP3
        if (isPlaylist) {
            return downloadPlaylist(url, info, action, height, bitrate, outputFormat, progress, removeSponsors, selectedAudioTrackId, selectedSubtitleLang)
        }
        val format = FormatPolicy.select(info, action, height, selectedAudioTrackId)
        val estimate = info.formats.filter { it.id in format.split('+') }.mapNotNull { it.bytes }.sum()
        if (StatFs(context.noBackupFilesDir.path).availableBytes < maxOf(256L * 1024 * 1024, estimate * 3))
            throw ProbeFailure(Problem.SPACE)
        val id = UUID.randomUUID().toString()
        val folder = Workspaces.create(context, id)
        begin(id)
        val started = System.nanoTime()
        try {
            val req = request(url, allowPlaylist = false).addOption("--format", format)
                .addOption("--no-simulate").addOption("--newline")
                .addOption("--output", File(folder, "media.%(ext)s").absolutePath)
                .addOption("--max-filesize", "512M")
                .addOption("--merge-output-format", if (outputFormat == "auto") "mkv" else outputFormat)
            if (outputFormat != "auto" && action != ProbeAction.MP3) req.addOption("--remux-video", outputFormat)
            if (action == ProbeAction.MP3) req.addOption("--extract-audio")
                .addOption("--audio-format", "mp3")
                .addOption("--audio-quality", "${bitrate}K")
                .addOption("--embed-metadata")
                .addOption("--embed-thumbnail")
                .addOption("--convert-thumbnails", "jpg")

            if (removeSponsors) {
                req.addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
            }

            if (!selectedSubtitleLang.isNullOrBlank() && selectedSubtitleLang != "none") {
                req.addOption("--write-subs")
                    .addOption("--write-auto-subs")
                    .addOption("--sub-langs", selectedSubtitleLang)
                if (action != ProbeAction.MP3) {
                    req.addOption("--embed-subs")
                }
            }

            checkCancelled()
            withResourceGuard(id) {
                engine.execute(req, id) { percent, _, _ -> progress(if (percent.isFinite() && percent >= 0) percent.coerceAtMost(100f) else -1f) }
            }
            checkCancelled()
            progress(-1f)
            val output = folder.listFiles()?.singleOrNull { it.isFile && it.extension in setOf("mp4", "webm", "mkv", "mp3", "m4a", "mov") }
                ?: folder.listFiles()?.firstOrNull { it.isFile && it.extension in setOf("mp4", "webm", "mkv", "mp3", "m4a", "mov") }
                ?: throw ProbeFailure(Problem.CONVERSION)
            val tracks = validateOutput(output, action)
            if (output.length() <= 0L) throw ProbeFailure(Problem.CONVERSION, "Empty output file")
            beginPublishing()
            val saved = publishWithName(output, action, info.title)

            // Also publish any written subtitle files (.srt, .vtt) so the user has the subtitle file saved directly
            val subFiles = folder.listFiles()?.filter { it.isFile && it.extension in setOf("srt", "vtt", "ass") } ?: emptyList()
            subFiles.forEach { subFile ->
                if (subFile.length() > 0) {
                    runCatching { publishWithName(subFile, action, "${info.title}.${subFile.extension}") }
                }
            }

            return ProbeResult(action, (System.nanoTime() - started) / 1_000_000, output.length(), saved, true, tracks)
        } finally {
            if (activeProcessId == id) activeProcessId = null
            Workspaces.release(folder)
        }
    }

    fun downloadClip(
        url: String,
        info: MediaInfo,
        clip: ClipRequest,
        progress: (Float, String) -> Unit
    ): ProbeResult {
        initialize()
        require(clip.startSeconds.isFinite() && clip.endSeconds.isFinite())
        require(clip.startSeconds >= 0.0) { "Start time must be >= 0" }
        require(clip.endSeconds > clip.startSeconds) { "End time must be > start time" }
        val totalDur = info.duration?.takeIf { it.isFinite() && it > 0 } ?: throw ProbeFailure(Problem.UNSUPPORTED, "Duration unavailable")
        require(clip.endSeconds <= totalDur) { "End time exceeds video duration" }

        val durationRatio = (clip.durationSeconds / totalDur).coerceIn(0.01, 1.0)
        val selectedFormat = info.formats.filter { it.height == clip.height }.maxByOrNull { it.bytes ?: 0 }
        val estTotal = selectedFormat?.bytes ?: (100L * 1024 * 1024)
        val estimate = (estTotal * durationRatio).toLong()
        if (StatFs(context.noBackupFilesDir.path).availableBytes < maxOf(128L * 1024 * 1024, estimate * 3))
            throw ProbeFailure(Problem.SPACE)

        val id = UUID.randomUUID().toString()
        val folder = Workspaces.create(context, id)
        begin(id)
        val started = System.nanoTime()

        try {
            val startFormatted = ClipRequest.formatSecondsFull(clip.startSeconds)
            val endFormatted = ClipRequest.formatSecondsFull(clip.endSeconds)
            val sectionArg = "*${startFormatted}-${endFormatted}"

            val formatStr = when {
                clip.isAudioOnly -> "bestaudio[format_note*=original]/bestaudio/best"
                clip.isBestQuality || clip.height == 0 -> "bestvideo+bestaudio[format_note*=original]/bestvideo+bestaudio/best"
                else -> {
                    val targetH = if (info.heights.contains(clip.height)) clip.height else info.defaultHeight
                    "bestvideo[height<=$targetH]+bestaudio[format_note*=original]/bestvideo[height<=$targetH]/best"
                }
            }

            progress(-1f, "Preparing segment download...")

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
                req.addOption("--merge-output-format", outExt).addOption("--remux-video", outExt)
            }

            checkCancelled()
            withResourceGuard(id) { engine.execute(req, id) { percent, _, _ ->
                val p = if (percent.isFinite() && percent >= 0) percent.coerceAtMost(100f) else -1f
                progress(p, "Downloading segment")
            } }
            checkCancelled()
            progress(-1f, "Validating & saving clip...")

            val output = folder.listFiles()?.singleOrNull { it.isFile && it.extension in setOf("mp4", "webm", "mkv", "mp3", "m4a", "mov") }
                ?: folder.listFiles()?.firstOrNull { it.isFile && it.extension in setOf("mp4", "webm", "mkv", "mp3", "m4a", "mov") }
                ?: throw ProbeFailure(Problem.CONVERSION, "Clip segment processing failed")

            if (output.length() <= 0L) throw ProbeFailure(Problem.CONVERSION, "Empty clip output")
            val safeTitle = info.title.take(30).replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_').ifBlank { "Video" }
            fun formatCompact(sec: Double): String {
                val totalSec = sec.toLong().coerceAtLeast(0L)
                val m = totalSec / 60
                val s = totalSec % 60
                return String.format(java.util.Locale.US, "%02dm%02ds", m, s)
            }
            val qualityTag = if (clip.isAudioOnly) "audio" else "clip"
            val displayBase = "${safeTitle}_${formatCompact(clip.startSeconds)}-${formatCompact(clip.endSeconds)}_${qualityTag}"
            validateOutput(output, if (clip.isAudioOnly) ProbeAction.MP3 else ProbeAction.VIDEO)
            beginPublishing()
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
            Workspaces.release(folder)
        }
    }

    private fun downloadPlaylist(
        url: String,
        info: MediaInfo,
        action: ProbeAction,
        height: Int,
        bitrate: Int,
        outputFormat: String,
        progress: (Float) -> Unit,
        removeSponsors: Boolean = false,
        selectedAudioTrackId: String? = null,
        selectedSubtitleLang: String? = null
    ): ProbeResult {
        if (StatFs(context.noBackupFilesDir.path).availableBytes < 512L * 1024 * 1024)
            throw ProbeFailure(Problem.SPACE)
        val id = UUID.randomUUID().toString()
        val folder = Workspaces.create(context, id)
        begin(id)
        val started = System.nanoTime()
        try {
            val req = request(url, allowPlaylist = true)
                .addOption("--no-simulate")
                .addOption("--newline")
                .addOption("--output", File(folder, "%(playlist_index)05d-%(title).80s.%(ext)s").absolutePath)
                .addOption("--max-filesize", "512M")

            if (removeSponsors) {
                req.addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
            }

            if (!selectedSubtitleLang.isNullOrBlank() && selectedSubtitleLang != "none") {
                req.addOption("--write-subs")
                    .addOption("--write-auto-subs")
                    .addOption("--sub-langs", selectedSubtitleLang)
                if (action !in setOf(ProbeAction.MP3, ProbeAction.PLAYLIST_MP3)) {
                    req.addOption("--embed-subs")
                }
            }

            if (action == ProbeAction.MP3 || action == ProbeAction.PLAYLIST_MP3) {
                val audioFmt = if (!selectedAudioTrackId.isNullOrBlank()) selectedAudioTrackId else "bestaudio[format_note*=original]/bestaudio/best"
                req.addOption("--format", audioFmt)
                    .addOption("-x")
                    .addOption("--audio-format", "mp3")
                    .addOption("--audio-quality", "${bitrate}K")
                    .addOption("--embed-metadata")
                    .addOption("--embed-thumbnail")
                    .addOption("--convert-thumbnails", "jpg")
            } else {
                val audioFmt = if (!selectedAudioTrackId.isNullOrBlank()) selectedAudioTrackId else "bestaudio[format_note*=original]/bestaudio/best"
                req.addOption("--format", "bestvideo[height<=$height]+$audioFmt/bestvideo[height<=$height]+bestaudio/best[height<=$height]/best")
                    .addOption("--merge-output-format", if (outputFormat == "auto") "mkv" else outputFormat)
            }

            if (outputFormat != "auto" && action !in setOf(ProbeAction.MP3, ProbeAction.PLAYLIST_MP3)) req.addOption("--remux-video", outputFormat)
            checkCancelled()
            withResourceGuard(id) { engine.execute(req, id) { _, _, _ -> progress(-1f) } }
            checkCancelled()
            val outputs = folder.listFiles()?.filter { it.isFile && it.extension in setOf("mp4", "webm", "mkv", "mp3", "m4a", "mov") }?.sortedBy { it.name }
                ?: emptyList()
            if (outputs.isEmpty()) throw ProbeFailure(Problem.CONVERSION, "No playlist items downloaded")
            if (outputs.any { it.length() <= 0L }) throw ProbeFailure(Problem.CONVERSION, "Playlist contains an empty output file")
            val totalBytes = outputs.sumOf { it.length() }
            if (outputs.any { it.length() > 512L * 1024 * 1024 }) {
                throw ProbeFailure(Problem.SPACE, "Playlist output exceeds the 512 MB per-file limit")
            }
            outputs.forEach { validateOutput(it, if (action in setOf(ProbeAction.MP3, ProbeAction.PLAYLIST_MP3)) ProbeAction.MP3 else ProbeAction.VIDEO) }
            beginPublishing()
            val publishedUris = mutableListOf<String>()
            try {
                outputs.forEach { publishedUris.add(publish(it, action)) }
            } catch (failure: Exception) {
                publishedUris.forEach { publishedUri ->
                    runCatching { context.contentResolver.delete(android.net.Uri.parse(publishedUri), null, null) }
                }
                throw failure
            }
            val detail = "${outputs.size} playlist items saved (${totalBytes / 1024} KB)"
            return ProbeResult(action, (System.nanoTime() - started) / 1_000_000, totalBytes, publishedUris.firstOrNull(), true, detail, publishedUris.toList())
        } finally {
            if (activeProcessId == id) activeProcessId = null
            Workspaces.release(folder)
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
            "mkv" -> "video/x-matroska"; "m4a" -> "audio/mp4"; "wav" -> "audio/wav"; else -> "video/quicktime"
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
            val finalized = resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            if (finalized <= 0) throw ProbeFailure(Problem.CONVERSION, "Unable to finalize saved media")
            return uri.toString()
        } catch (e: Exception) { runCatching { resolver.delete(uri, null, null) }; throw e }
    }

    fun publishWithName(file: File, action: ProbeAction, baseName: String): String {
        val mime = when (file.extension) {
            "mp3" -> "audio/mpeg"; "mp4" -> "video/mp4"; "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"; "m4a" -> "audio/mp4"; "wav" -> "audio/wav"
            "srt" -> "application/x-subrip"; "vtt" -> "text/vtt"; "ass" -> "text/x-ssa"
            else -> "video/quicktime"
        }
        val cleanBase = org.videopocket.probe.storage.FileNamingEngine.sanitize(baseName).take(100)
        val displayName = "${cleanBase}-${UUID.randomUUID().toString().take(8)}.${file.extension}"
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
            val finalized = resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            if (finalized <= 0) throw ProbeFailure(Problem.CONVERSION, "Unable to finalize saved media")
            return uri.toString()
        } catch (e: Exception) { runCatching { resolver.delete(uri, null, null) }; throw e }
    }

    companion object {
        val KNOWN_LANG_MAP = mapOf(
            "arabic" to "ar", "العربية" to "ar",
            "english" to "en", "الإنجليزية" to "en",
            "spanish" to "es", "الإسبانية" to "es",
            "french" to "fr", "الفرنسية" to "fr",
            "german" to "de", "الألمانية" to "de",
            "italian" to "it", "الإيطالية" to "it",
            "portuguese" to "pt", "البرتغالية" to "pt",
            "russian" to "ru", "الروسية" to "ru",
            "hindi" to "hi", "الهندية" to "hi",
            "turkish" to "tr", "التركية" to "tr",
            "japanese" to "ja", "اليابانية" to "ja",
            "korean" to "ko", "الكورية" to "ko",
            "chinese" to "zh", "الصينية" to "zh",
            "urdu" to "ur", "الأردية" to "ur",
            "indonesian" to "id", "الإندونيسية" to "id",
            "bengali" to "bn", "البنغالية" to "bn",
            "persian" to "fa", "الفارسية" to "fa",
            "vietnamese" to "vi", "الفيتنامية" to "vi",
            "dutch" to "nl", "الهولندية" to "nl",
            "polish" to "pl", "البولندية" to "pl",
            "ukrainian" to "uk", "الأوكرانية" to "uk"
        )

        fun getArabicDisplayLanguage(code: String): String {
            if (code.isBlank() || code == "und" || code == "orig") return "Original"
            return runCatching {
                val locale = java.util.Locale(code)
                val arName = locale.getDisplayLanguage(java.util.Locale("ar"))
                if (arName.isNotBlank() && !arName.equals(code, ignoreCase = true)) arName
                else locale.displayLanguage.takeIf { it.isNotBlank() } ?: code.uppercase()
            }.getOrDefault(code.uppercase())
        }

        @Volatile private var cachedInitResult: JSONObject? = null
        private val initLock = Any()
        private val WATCHDOG = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "download-watchdog").apply { isDaemon = true }
        }

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

