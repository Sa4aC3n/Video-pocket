package org.videopocket.probe.processing

import android.content.Context
import android.util.AtomicFile
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import java.util.zip.ZipFile

/** Shared FFmpeg dependencies also live in the Python archive. Extract libraries, not Python code. */
object BundledFfmpeg {
    private var preparedPath: String? = null

    @Synchronized fun initialize(context: Context) {
        FFmpeg.getInstance().init(context)
        val folder = File(context.noBackupFilesDir, "videopocket-native").apply { mkdirs() }
        if (preparedPath == folder.path && File(folder, "libc++_shared.so").isFile) return
        val archive = File(context.applicationInfo.nativeLibraryDir, "libpython.zip.so")
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().filter { entry ->
                entry.name.startsWith("usr/lib/") && !entry.isDirectory &&
                    '/' !in entry.name.removePrefix("usr/lib/") && ".so" in entry.name
            }.toList()
            check(entries.any { it.name == "usr/lib/libc++_shared.so" }) { "Bundled shared runtime is missing" }
            for (entry in entries) {
                val atomic = AtomicFile(File(folder, entry.name.removePrefix("usr/lib/")))
                val stream = atomic.startWrite()
                try {
                    zip.getInputStream(entry).use { it.copyTo(stream) }
                    atomic.finishWrite(stream)
                } catch (failure: Exception) { atomic.failWrite(stream); throw failure }
            }
        }
        preparedPath = folder.path
    }

    fun libraryPath(context: Context): String = listOf(
        File(context.noBackupFilesDir, "videopocket-native").path,
        File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").path,
        context.applicationInfo.nativeLibraryDir
    ).joinToString(":")
}
