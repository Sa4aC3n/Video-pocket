package org.videopocket.probe.processing

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File

/** Lazy extraction of bundled assets only. Neither init method makes a network request. */
object BundledFfmpeg {
    @Synchronized fun initialize(context: Context) {
        FFmpeg.getInstance().init(context)
        // The upstream extractor preserves the archive's native-library symlinks.
        // Copying link entries as ordinary files produces invalid shared libraries.
        YoutubeDL.getInstance().init(context)
    }

    fun libraryPath(context: Context): String = listOf(
        File(context.noBackupFilesDir, "youtubedl-android/packages/python/usr/lib").path,
        File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").path,
        context.applicationInfo.nativeLibraryDir
    ).joinToString(":")
}
