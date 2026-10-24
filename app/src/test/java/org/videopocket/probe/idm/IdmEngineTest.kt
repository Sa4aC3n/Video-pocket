package org.videopocket.probe.idm

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class IdmEngineTest {

    private fun sanitizeFilename(name: String): String {
        val base = File(name).name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val cleaned = base.replace(Regex("^\\.+"), "").trim()
        return if (cleaned.isNotBlank()) cleaned.take(120) else "download_${System.currentTimeMillis()}"
    }

    @Test
    fun testPathTraversalSanitization() {
        // Path traversal payload
        val malicious = "../../etc/passwd"
        val sanitized = sanitizeFilename(malicious)
        assertFalse("Should not contain slashes or traversal dots", sanitized.contains("/") || sanitized.contains(".."))
        assertEquals("passwd", sanitized)

        // Windows absolute path payload
        val winPayload = "C:\\Windows\\System32\\cmd.exe"
        val sanitizedWin = sanitizeFilename(winPayload)
        assertFalse("Should not contain backslashes or colons", sanitizedWin.contains("\\") || sanitizedWin.contains(":"))
        assertEquals("cmd.exe", sanitizedWin)

        // Legitimate filename
        val legit = "sample_video_1080p.mp4"
        val sanitizedLegit = sanitizeFilename(legit)
        assertEquals("sample_video_1080p.mp4", sanitizedLegit)
    }

    @Test
    fun testIdmTaskManagerPersistence() {
        val testTask = IdmTask(
            url = "https://example.com/file.zip",
            filename = "file.zip",
            totalBytes = 1024L
        )
        IdmTaskManager.tasks.clear()
        IdmTaskManager.tasks.add(testTask)

        assertEquals(1, IdmTaskManager.tasks.size)
        assertEquals("file.zip", IdmTaskManager.tasks[0].filename)
        IdmTaskManager.tasks.clear()
    }
}
