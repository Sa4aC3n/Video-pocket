package org.videopocket.probe.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import org.videopocket.probe.MainActivity
import org.videopocket.probe.R

object PocketNotifier {
    const val CHANNEL_ID = "videopocket_downloads"
    const val CHANNEL_ID_PROCESSING = "videopocket_processing"
    const val NOTIFICATION_ID_DOWNLOAD = 1001
    const val NOTIFICATION_ID_PROCESSING = 2001

    private var lastNotificationTimeMs = 0L
    private var lastNotifiedPercent = -1

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "VideoPocket Downloads"
            val descriptionText = "Notifications for video and clip download progress"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun initProcessingChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "VideoPocket Processing"
            val descriptionText = "Notifications for local media compression and processing"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID_PROCESSING, name, importance).apply {
                description = descriptionText
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the persistent notification shown while [DownloadForegroundService] is alive.
     * This is a minimal, silent notification; individual task progress is shown separately.
     */
    fun buildServiceNotification(context: Context): Notification {
        initChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Video Pocket")
            .setContentText("Downloads are running in the background")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showProgress(context: Context, notifId: Int = NOTIFICATION_ID_DOWNLOAD, title: String, percent: Int, stageText: String) {
        val now = System.currentTimeMillis()
        if (percent in 1..99) {
            if (percent == lastNotifiedPercent || now - lastNotificationTimeMs < 500L) {
                return
            }
        }
        lastNotificationTimeMs = now
        lastNotifiedPercent = percent
        try {
            initChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(if (percent >= 0) "$stageText ($percent%)" else stageText)
                .setProgress(100, percent.coerceIn(0, 100), percent < 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notifId, builder.build())
        } catch (_: Exception) {}
    }

    fun showProcessingProgress(context: Context, title: String, percent: Int, stageText: String) {
        try {
            initProcessingChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID_PROCESSING)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(if (percent >= 0) "$stageText ($percent%)" else stageText)
                .setProgress(100, percent.coerceIn(0, 100), percent < 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID_PROCESSING, builder.build())
        } catch (_: Exception) {}
    }

    fun showSuccess(context: Context, notifId: Int = NOTIFICATION_ID_DOWNLOAD, title: String, message: String, uriString: String?) {
        lastNotifiedPercent = -1
        lastNotificationTimeMs = 0L
        try {
            initChannel(context)
            val openIntent = if (!uriString.isNullOrBlank()) {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse(uriString),
                        context.contentResolver.getType(Uri.parse(uriString)) ?: "video/*"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(context, MainActivity::class.java)
            }

            val pendingIntent = PendingIntent.getActivity(
                context, notifId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notifId, builder.build())
        } catch (_: Exception) {}
    }

    fun showProcessingSuccess(context: Context, title: String, message: String) {
        try {
            initProcessingChannel(context)
            val pendingIntent = PendingIntent.getActivity(
                context, NOTIFICATION_ID_PROCESSING,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID_PROCESSING)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID_PROCESSING, builder.build())
        } catch (_: Exception) {}
    }

    fun dismiss(context: Context, notifId: Int = NOTIFICATION_ID_DOWNLOAD) {
        lastNotifiedPercent = -1
        lastNotificationTimeMs = 0L
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notifId)
        } catch (_: Exception) {}
    }

    fun dismissProcessing(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID_PROCESSING)
        } catch (_: Exception) {}
    }
}
