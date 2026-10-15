package org.videopocket.probe

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import java.io.File
import java.io.OutputStream

object TestEvidence {
    fun output(context: Context, name: String): OutputStream {
        if (InstrumentationRegistry.getArguments().getString("useTestStorageService") == "true") {
            return PlatformTestStorageRegistry.getInstance().openOutputFile(name)
        }
        // The second, adb-driven tablet test has no Gradle-managed storage service.
        val folder = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
        return File(folder, name).outputStream()
    }
}
