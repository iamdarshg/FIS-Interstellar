package org.hound.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LatestFrameGateTest {

    @Test
    fun testFrameOnCloseIsCalledExactlyOnce() {
        val closeCount = AtomicInteger(0)
        val frame = Frame(
            width = 320,
            height = 240,
            rgbData = ByteArray(320 * 240 * 3),
            timestampMs = 1000L,
            onClose = { closeCount.incrementAndGet() }
        )

        assertEquals(0, closeCount.get())

        frame.close()
        assertEquals(1, closeCount.get())
        assertTrue(frame.isClosed)

        frame.close()
        assertEquals(1, closeCount.get())
    }

    @Test
    fun testAutoCloseableUseBlockClosesFrameOnce() {
        val closeCount = AtomicInteger(0)
        Frame(
            width = 320,
            height = 240,
            rgbData = ByteArray(320 * 240 * 3),
            timestampMs = 1000L,
            onClose = { closeCount.incrementAndGet() }
        ).use { frame ->
            assertEquals(0, closeCount.get())
        }
        assertEquals(1, closeCount.get())
    }
}
