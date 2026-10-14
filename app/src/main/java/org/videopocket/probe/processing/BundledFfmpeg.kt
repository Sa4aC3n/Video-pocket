package org.videopocket.probe.processing

import android.content.Context
import android.util.AtomicFile
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import java.util.zip.ZipFile

/** FFmpeg's C++ runtime is shipped in the wrapper's Python archive, not its FFmpeg archive. */
object BundledFfmpeg {
    private var preparedPath: String? = null

    @Synchronized fun initialize(context: Context) {
        FFmpeg.getInstance().init(context)
        val folder = File(context.noBackupFilesDir, "videopocket-native").apply { mkdirs() }
        val target = File(folder, "libc++_shared.so")
        if (preparedPath == target.path && target.isFile) return
        val archive = File(context.applicationInfo.nativeLibraryDir, "libpython.zip.so")
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry("usr/lib/libc++_shared.so") ?: throw ProcessingFailure("ENGINE_UNAVAILABLE")
            val atomic = AtomicFile(target)
            val stream = atomic.startWrite()
            try {
                zip.getInputStream(entry).use { it.copyTo(stream) }
                atomic.finishWrite(stream)
            } catch (failure: Exception) { atomic.failWrite(stream); throw failure }
        }
        preparedPath = target.path
    }

    fun libraryPath(context: Context): String = listOf(
        File(context.noBackupFilesDir, "videopocket-native").path,
        File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").path,
        context.applicationInfo.nativeLibraryDir
    ).joinToString(":")
}
