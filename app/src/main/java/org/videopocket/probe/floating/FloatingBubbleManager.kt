package org.videopocket.probe.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Manages the Floating Action Bubble overlay lifecycle and permissions.
 */
object FloatingBubbleManager {

    var isRunning by mutableStateOf(false)
        internal set

    /**
     * Checks if the app has permission to draw overlays on screen.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Opens system settings to allow overlay permission.
     */
    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Starts the FloatingBubbleService.
     */
    fun start(context: Context) {
        if (!canDrawOverlays(context)) {
            requestOverlayPermission(context)
            return
        }
        val intent = Intent(context, FloatingBubbleService::class.java)
        context.startService(intent)
    }

    /**
     * Stops the FloatingBubbleService.
     */
    fun stop(context: Context) {
        val intent = Intent(context, FloatingBubbleService::class.java)
        context.stopService(intent)
        isRunning = false
    }

    /**
     * Toggles the Floating Bubble on or off.
     */
    fun toggle(context: Context) {
        if (isRunning) {
            stop(context)
        } else {
            start(context)
        }
    }
}
