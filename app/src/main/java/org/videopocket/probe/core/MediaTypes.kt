package org.videopocket.probe.core

import java.util.Locale

object MediaTypes {
    fun isAudio(extension: String): Boolean = extension.lowercase(Locale.ROOT) in
        setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")
    fun fallbackMime(extension: String): String = if (isAudio(extension)) "audio/*" else "video/*"
}
