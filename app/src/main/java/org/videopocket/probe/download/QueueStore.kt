package org.videopocket.probe.download

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import org.videopocket.probe.core.ClipRequest
import java.io.File

/** Pending operations are recovered as interrupted, never automatically redownloaded. */
class QueueStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "download-queue.json"))
    fun load(): List<DownloadTask> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val array = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        return (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            val savedState = runCatching { DownloadState.valueOf(json.getString("state")) }.getOrDefault(DownloadState.FAILED)
            val interrupted = savedState !in setOf(DownloadState.COMPLETED, DownloadState.FAILED, DownloadState.CANCELLED)
            val clip = json.optJSONObject("clip")?.let {
                ClipRequest(it.getDouble("start"), it.getDouble("end"), it.optInt("height", 0), it.optString("format", "mp4"), it.optBoolean("best"), it.optBoolean("audio"))
            }
            DownloadTask(id = json.getString("id"), url = json.getString("url"), title = json.getString("title"),
                platform = json.optString("platform"), quality = json.optString("quality"), format = json.optString("format", "mp4"),
                height = json.optInt("height", 0), bitrate = json.optInt("bitrate", 192), isAudioOnly = json.optBoolean("audio"),
                isClip = clip != null, clipRequest = clip,
                state = if (interrupted) DownloadState.FAILED else savedState,
                error = if (interrupted) "INTERRUPTED" else json.optString("error").ifBlank { null },
                outputUri = json.optString("outputUri").ifBlank { null }, bytes = json.optLong("bytes"),
                elapsedMs = if (json.has("elapsedMs") && !json.isNull("elapsedMs")) json.getLong("elapsedMs").takeIf { it >= 0 } else null,
                validationDetail = json.optString("validationDetail").takeIf { it.isNotBlank() && it != "null" },
                createdAt = json.optLong("createdAt"), progress = if (savedState == DownloadState.COMPLETED) 100f else -1f)
        }
    }
    fun save(tasks: List<DownloadTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(JSONObject().apply {
                put("id", task.id); put("url", task.url); put("title", task.title); put("platform", task.platform)
                put("quality", task.quality); put("format", task.format); put("height", task.height); put("bitrate", task.bitrate)
                put("audio", task.isAudioOnly); put("state", task.state.name); put("error", task.error ?: "")
                put("outputUri", task.outputUri ?: ""); put("bytes", task.bytes); put("createdAt", task.createdAt)
                put("elapsedMs", task.elapsedMs ?: JSONObject.NULL); put("validationDetail", task.validationDetail ?: JSONObject.NULL)
                task.clipRequest?.let { clip -> put("clip", JSONObject().apply {
                    put("start", clip.startSeconds); put("end", clip.endSeconds); put("height", clip.height)
                    put("format", clip.format); put("best", clip.isBestQuality); put("audio", clip.isAudioOnly)
                }) }
            })
        }
        val output = file.startWrite()
        try { output.write(array.toString().toByteArray()); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }
}
