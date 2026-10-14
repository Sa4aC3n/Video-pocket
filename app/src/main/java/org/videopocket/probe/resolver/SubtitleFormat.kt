package org.videopocket.probe.resolver

import org.videopocket.probe.core.ClipRequest

/** Converts actual timed cues; never relabels JSON, HTML, or plain text as subtitles. */
object SubtitleFormat {
    private val timing = Regex("^((?:[0-9]{2,}:)?[0-9]{2}:[0-9]{2}[.,][0-9]{3})\\s+-->\\s+((?:[0-9]{2,}:)?[0-9]{2}:[0-9]{2}[.,][0-9]{3})(?:\\s+.*)?$")
    private data class Cue(val start: Double, val end: Double, val text: String)

    fun convert(content: String, format: String): String {
        require(format in setOf("srt", "vtt", "txt")) { "Unsupported subtitle format" }
        val normalized = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').trim()
        val cues = normalized.split(Regex("\n[ \\t]*\n")).mapNotNull { block ->
            val lines = block.lines()
            if (lines.first().startsWith("NOTE") || lines.first() in setOf("STYLE", "REGION")) return@mapNotNull null
            val index = lines.indexOfFirst { timing.matches(it.trim()) }
            if (index < 0) return@mapNotNull null
            val match = timing.matchEntire(lines[index].trim())!!
            val start = ClipRequest.parseTimestamp(match.groupValues[1].replace(',', '.'))
            val end = ClipRequest.parseTimestamp(match.groupValues[2].replace(',', '.'))
            require(start != null && end != null && end > start) { "Invalid subtitle timing" }
            val text = lines.drop(index + 1).joinToString("\n").trim()
            require(text.isNotBlank()) { "Empty subtitle cue" }
            Cue(start, end, text)
        }
        require(cues.isNotEmpty()) { "No supported timed subtitle cues" }
        if (format == "txt") return cues.joinToString("\n") { it.text }
        fun timestamp(value: Double): String {
            val millis = kotlin.math.round(value * 1000).toLong()
            return String.format(java.util.Locale.US, "%02d:%02d:%02d%s%03d", millis / 3_600_000,
                millis / 60_000 % 60, millis / 1000 % 60, if (format == "srt") "," else ".", millis % 1000)
        }
        val body = cues.mapIndexed { index, cue ->
            (if (format == "srt") "${index + 1}\n" else "") +
                "${timestamp(cue.start)} --> ${timestamp(cue.end)}\n${cue.text}"
        }.joinToString("\n\n") + "\n"
        return if (format == "vtt") "WEBVTT\n\n$body" else body
    }
}
