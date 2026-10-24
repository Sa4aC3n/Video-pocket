package org.videopocket.probe.storage

import android.content.Context
import java.io.File

/** Only owns private scratch directories; completed media is never stored here. */
object Workspaces {
    private val active = mutableSetOf<String>()

    @Synchronized fun create(context: Context, id: String): File {
        val directory = File(context.noBackupFilesDir, "probe-files/$id")
        check(directory.mkdirs()) { "Unable to create processing workspace" }
        active.add(directory.canonicalPath)
        return directory
    }

    @Synchronized fun release(directory: File) {
        directory.deleteRecursively()
        active.remove(directory.canonicalPath)
    }

    @Synchronized fun clean(context: Context): Long {
        var freed = 0L
        File(context.noBackupFilesDir, "probe-files").listFiles()?.forEach { directory ->
            if (directory.canonicalPath !in active) {
                val before = size(directory)
                directory.deleteRecursively()
                freed += (before - size(directory)).coerceAtLeast(0L)
            }
        }
        return freed
    }

    fun size(file: File): Long = if (!file.exists()) 0L else if (file.isFile) file.length()
        else file.listFiles()?.sumOf { size(it) } ?: 0L
}
