package org.videopocket.probe

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yausername.ffmpeg.FFmpeg
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.videopocket.probe.core.PocketLibraryManager
import org.videopocket.probe.core.ProbeAction
import org.videopocket.probe.engine.ProbeEngine
import org.videopocket.probe.processing.*
import org.videopocket.probe.storage.Workspaces
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun activityLaunchKeepsDownloaderUninitialized() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(ProbeEngine.isInitialized) }
        }
    }

    @Test fun actualBundledEncoderProducesEditableMediaAndKeepsOriginal() {
        val engine = LocalMediaProcessor(context)
        val capabilities = engine.capabilities()
        assertTrue("Bundled MP4 encoder must pass real device probe", "mp4" in capabilities.formats(false))
        val folder = Workspaces.create(context, "fixture-${UUID.randomUUID()}")
        val uris = mutableListOf<String>()
        try {
            FFmpeg.getInstance().init(context)
            val file = File(folder, "source.mp4")
            val binary = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
            val builder = ProcessBuilder(binary.path, "-hide_banner", "-nostdin", "-y", "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=15",
                "-f", "lavfi", "-i", "sine=frequency=1000:sample_rate=44100", "-t", "4", "-c:v", "libx264", "-crf", "12", "-threads", "2", "-c:a", "aac", file.path)
                .redirectErrorStream(true).redirectOutput(File(folder, "fixture.log"))
            builder.environment()["LD_LIBRARY_PATH"] = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").path + ":" + context.applicationInfo.nativeLibraryDir
            val child = builder.start()
            assertTrue(child.waitFor(30, TimeUnit.SECONDS))
            assertEquals(0, child.exitValue())
            val original = ProbeEngine(context).publishWithName(file, ProbeAction.VIDEO, "instrumented-original")
            uris.add(original)
            val before = hash(original)
            val progress = mutableListOf<Float>()
            val result = engine.run(ProcessingRequest(original, "instrumented-compression", preset = CompressionPreset.SMALL_FILE)) {
                it.percent?.let(progress::add)
            }
            uris.add(result.media.uri)
            assertTrue(result.media.bytes in 1 until result.inputBytes)
            assertEquals(before, hash(original))
            assertTrue(progress.isNotEmpty())
            assertEquals(100f, progress.last(), 0f)
            val trim = LocalMediaProcessor(context).run(ProcessingRequest(original, "instrumented-trim", ProcessingMode.TRIM,
                startSeconds = 1.0, endSeconds = 2.5)) {}
            uris.add(trim.media.uri)
            assertTrue(kotlin.math.abs((trim.media.durationMs ?: 0) - 1500) < 500)
            val audio = LocalMediaProcessor(context).run(ProcessingRequest(original, "instrumented-audio", ProcessingMode.EXTRACT_AUDIO, format = "m4a")) {}
            uris.add(audio.media.uri)
            assertTrue(audio.media.isAudio)
            assertEquals(before, hash(original))
            val cancelled = LocalMediaProcessor(context)
            cancelled.cancel()
            try { cancelled.run(ProcessingRequest(original, "cancelled")) {}; fail("Cancellation was ignored") }
            catch (failure: ProcessingFailure) { assertEquals("CANCELLED", failure.code) }
            assertEquals(before, hash(original))
        } finally {
            PocketLibraryManager.getItems(context).filter { it.uriString in uris }.forEach { PocketLibraryManager.deleteItem(context, it.id) }
            uris.forEach { runCatching { context.contentResolver.delete(Uri.parse(it), null, null) } }
            Workspaces.release(folder)
        }
    }
    private fun hash(uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(Uri.parse(uri))!!.use { stream ->
            val buffer = ByteArray(8192)
            while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
