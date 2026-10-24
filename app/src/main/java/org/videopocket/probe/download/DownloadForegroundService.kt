package org.videopocket.probe.download

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.videopocket.probe.engine.PocketNotifier

/**
 * Foreground service that keeps downloads and processing alive when the app is in the background.
 *
 * Lifecycle:
 *  - Started (via startForegroundService) when a download or processing task begins.
 *  - Posts a persistent foreground notification so Android does not kill the process.
 *  - Stopped automatically when [release] is called and all active tasks are done.
 *
 * The actual download work remains in [DownloadQueueManager] / [LocalMediaProcessor] running on
 * their own coroutine scopes.  This service exists solely to satisfy Android's requirement that
 * any background processing be accompanied by a visible notification and a declared foreground
 * service type.
 */
class DownloadForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        PocketNotifier.initChannel(this)
        PocketNotifier.initProcessingChannel(this)

        // Show the persistent foreground notification immediately.
        // Individual task notifications (with progress) are posted separately by PocketNotifier.
        val notification = PocketNotifier.buildServiceNotification(this)
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        active = false
    }

    companion object {
        const val ACTION_STOP = "org.videopocket.probe.ACTION_STOP_SERVICE"
        const val FOREGROUND_NOTIFICATION_ID = 1000

        /** True while the service is running (set by [ServiceController]). */
        @Volatile var active = false
            internal set
    }
}
