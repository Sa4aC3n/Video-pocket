package org.videopocket.probe.floating

import org.junit.Assert.*
import org.junit.Test

class FloatingBubbleManagerTest {

    @Test
    fun testInitialState() {
        assertNotNull(FloatingBubbleManager)
        assertFalse(FloatingBubbleManager.isRunning)
    }

    @Test
    fun testStateMutation() {
        FloatingBubbleManager.isRunning = true
        assertTrue(FloatingBubbleManager.isRunning)
        FloatingBubbleManager.isRunning = false
        assertFalse(FloatingBubbleManager.isRunning)
    }
}
