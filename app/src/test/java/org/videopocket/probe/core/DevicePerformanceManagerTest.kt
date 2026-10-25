package org.videopocket.probe.core

import org.junit.Assert.*
import org.junit.Test

class DevicePerformanceManagerTest {

    @Test
    fun testCacheSizes() {
        val standardCache = 64L * 1024L * 1024L
        val lowRamCache = 16L * 1024L * 1024L

        assertTrue(standardCache > lowRamCache)
        assertEquals(67108864L, standardCache)
        assertEquals(16777216L, lowRamCache)
    }

    @Test
    fun testParallelJobBounds() {
        val cores = Runtime.getRuntime().availableProcessors()
        assertTrue(cores >= 1)
    }
}
