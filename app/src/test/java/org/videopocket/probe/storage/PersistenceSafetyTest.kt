package org.videopocket.probe.storage

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.videopocket.probe.core.*
import org.videopocket.probe.download.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PersistenceSafetyTest {
    private lateinit var app: Application
    @Before fun prepare() {
        app = RuntimeEnvironment.getApplication()
        File(app.filesDir, "pocket_library.json").delete()
        File(app.filesDir, "pocket_library.json.bak").delete()
        File(app.noBackupFilesDir, "download-queue.json").delete()
    }
    private fun item(id: String, uri: String) = PocketLibraryItem(id, "اختبار", uri, false,
        duration = "—", resolution = "—", format = "MP4", fileSizeBytes = 12345,
        isFavorite = true, collections = listOf("تعليم"))

    @Test fun batchIndexRetainsExistingDataAndDeduplicatesSavedUri() {
        PocketLibraryManager.addItem(app, item("a", "content://media/1"))
        PocketLibraryManager.addItems(app, listOf(item("b", "content://media/2"), item("c", "content://media/1")))
        val loaded = PocketLibraryManager.getItems(app)
        assertEquals(2, loaded.size)
        assertEquals(setOf("b", "c"), loaded.map { it.id }.toSet())
        assertTrue(loaded.all { it.isFavorite && it.collections == listOf("تعليم") && it.fileSizeBytes == 12345L })
    }
    @Test fun corruptIndexIsNotSilentlyOverwritten() {
        val file = File(app.filesDir, "pocket_library.json")
        file.writeText("{broken original")
        assertThrows(java.io.IOException::class.java) { PocketLibraryManager.addItem(app, item("a", "content://media/1")) }
        assertEquals("{broken original", file.readText())
    }
    @Test fun cleanupExcludesActiveWorkAndMeasuresNestedBytes() {
        val active = Workspaces.create(app, "active")
        File(active, "fragment.part").writeBytes(ByteArray(50))
        val old = File(app.noBackupFilesDir, "probe-files/old/nested").apply { mkdirs() }
        File(old, "fragment.part").writeBytes(ByteArray(70))
        assertEquals(70L, Workspaces.clean(app))
        assertTrue(File(active, "fragment.part").exists())
        Workspaces.release(active)
        assertFalse(active.exists())
    }
    @Test fun interruptedQueueRequiresRestartAndKeepsClipSettings() {
        val store = QueueStore(app)
        val clip = ClipRequest(1.25, 3.75, 720, "webm", false, true)
        store.save(listOf(DownloadTask(id = "pending", url = "https://example.org/a", title = "title", platform = "test", quality = "720p", format = "webm",
            isClip = true, clipRequest = clip, state = DownloadState.DOWNLOADING),
            DownloadTask(id = "done", url = "https://example.org/b", title = "done", platform = "test", quality = "", format = "mp4",
                state = DownloadState.COMPLETED, outputUri = "content://media/7", bytes = 100,
                elapsedMs = 1337, validationDetail = "video/avc; container verified")))
        val loaded = store.load()
        assertEquals(DownloadState.FAILED, loaded[0].state)
        assertEquals("INTERRUPTED", loaded[0].error)
        assertEquals(clip, loaded[0].clipRequest)
        assertEquals(DownloadState.COMPLETED, loaded[1].state)
        assertEquals("content://media/7", loaded[1].outputUri)
        assertEquals(1337L, loaded[1].elapsedMs)
        assertEquals("video/avc; container verified", loaded[1].validationDetail)
        assertNull(loaded[0].elapsedMs)
    }
}
