package org.videopocket.probe.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Optimizes memory, concurrency, and UI rendering based on the device's hardware profile.
 * Ensures smooth performance on Android Go and low-end devices with <= 2 GB RAM.
 */
object DevicePerformanceManager {

    /**
     * Detects if the current device is running Android Go or has low memory capacity (<= 2 GB RAM).
     */
    fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val isGo = am.isLowRamDevice
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return isGo || totalRamGb <= 2.1
    }

    /**
     * Returns the optimal cache size limit in bytes.
     */
    fun getMaxCacheSizeBytes(context: Context): Long {
        return if (isLowRamDevice(context)) {
            16L * 1024L * 1024L // 16 MB on low-RAM devices
        } else {
            64L * 1024L * 1024L // 64 MB on standard devices
        }
    }

    /**
     * Returns the recommended concurrent network/transcoding jobs to prevent OOM.
     */
    fun getRecommendedMaxParallelJobs(context: Context): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return if (isLowRamDevice(context) || cores <= 4) 1 else 3
    }
}
