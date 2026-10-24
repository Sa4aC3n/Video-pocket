package org.videopocket.probe.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Manages starting and stopping [DownloadForegroundService].
 *
 * Call [acquire] whenever a download or processing task becomes active.
 * Call [release] when the last active task finishes.
 * The service uses a simple reference count so multiple concurrent callers
 * can safely acquire/release without racing.
 */
object ServiceController {

    @Volatile private var refCount = 0

    /**
     * Increment the active-task reference count.
     * Starts the foreground service if it isn't already running.
     */
    @Synchronized
    fun acquire(context: Context) {
        refCount++
        if (!DownloadForegroundService.active) {
            val intent = Intent(context.applicationContext, DownloadForegroundService::class.java)
            ContextCompat.startForegroundService(context.applicationContext, intent)
            DownloadForegroundService.active = true
        }
    }

    /**
     * Decrement the active-task reference count.
     * Stops the foreground service once all tasks are done.
     */
    @Synchronized
    fun release(context: Context) {
        if (refCount > 0) refCount--
        if (refCount == 0 && DownloadForegroundService.active) {
            val intent = Intent(context.applicationContext, DownloadForegroundService::class.java)
                .apply { action = DownloadForegroundService.ACTION_STOP }
            context.applicationContext.startService(intent)
            DownloadForegroundService.active = false
        }
    }

    /** Force-stop regardless of reference count. Used on app shutdown. */
    @Synchronized
    fun forceStop(context: Context) {
        refCount = 0
        if (DownloadForegroundService.active) {
            val intent = Intent(context.applicationContext, DownloadForegroundService::class.java)
                .apply { action = DownloadForegroundService.ACTION_STOP }
            context.applicationContext.startService(intent)
            DownloadForegroundService.active = false
        }
    }
}
