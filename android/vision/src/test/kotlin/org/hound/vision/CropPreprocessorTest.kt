package org.hound.vision

import org.hound.domain.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropPreprocessorTest {

    @Test
    fun testCropPreprocessorOutputBufferSizeAndProperties() {
        val width = 4
        val height = 4
        val rgbData = ByteArray(width * height * 3) { idx -> (idx * 5).toByte() }

        val frame = Frame(width = width, height = height, rgbData = rgbData, timestampMs = 1000L, onClose = {})
        val candidate = Candidate(BoundingBox(0.0f, 0.0f, 1.0f, 1.0f), 1.0f)

        val buffer = CropPreprocessor.prepare(frame, candidate)

        assertTrue(buffer.isDirect)
        assertEquals(49152, buffer.capacity())
        assertEquals(49152, buffer.remaining())
    }

    @Test
    fun testGoldenPixelCropOutput() {
        val rgbData = byteArrayOf(
            255.toByte(), 0, 0,
            0, 255.toByte(), 0,
            0, 0, 255.toByte(),
            255.toByte(), 255.toByte(), 255.toByte()
        )
        val frame = Frame(width = 2, height = 2, rgbData = rgbData, timestampMs = 1000L, onClose = {})
        val candidate = Candidate(BoundingBox(0.0f, 0.0f, 0.5f, 0.5f), 0.25f)

        val buffer = CropPreprocessor.prepare(frame, candidate)
        assertEquals(49152, buffer.remaining())

        val firstR = buffer.get().toInt() and 0xFF
        val firstG = buffer.get().toInt() and 0xFF
        val firstB = buffer.get().toInt() and 0xFF

        assertEquals(255, firstR)
        assertEquals(0, firstG)
        assertEquals(0, firstB)
    }
}
