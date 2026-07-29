package org.hound.vision

import java.nio.ByteBuffer
import java.nio.ByteOrder

object CropPreprocessor {

    const val TARGET_SIZE = 128
    const val BUFFER_SIZE = TARGET_SIZE * TARGET_SIZE * 3

    fun prepare(frame: Frame, candidate: Candidate): ByteBuffer {
        val box = candidate.box
        val cropXMin = (box.xMin * frame.width).toInt().coerceIn(0, frame.width - 1)
        val cropYMin = (box.yMin * frame.height).toInt().coerceIn(0, frame.height - 1)
        val cropXMax = (box.xMax * frame.width).toInt().coerceIn(cropXMin + 1, frame.width)
        val cropYMax = (box.yMax * frame.height).toInt().coerceIn(cropYMin + 1, frame.height)

        val cropW = cropXMax - cropXMin
        val cropH = cropYMax - cropYMin

        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder())
        buffer.rewind()

        for (outY in 0 until TARGET_SIZE) {
            val srcY = cropYMin + (outY * cropH.toFloat() / TARGET_SIZE)
            val y0 = srcY.toInt().coerceIn(cropYMin, cropYMax - 1)
            val y1 = (y0 + 1).coerceIn(cropYMin, cropYMax - 1)
            val yWeight = srcY - y0

            for (outX in 0 until TARGET_SIZE) {
                val srcX = cropXMin + (outX * cropW.toFloat() / TARGET_SIZE)
                val x0 = srcX.toInt().coerceIn(cropXMin, cropXMax - 1)
                val x1 = (x0 + 1).coerceIn(cropXMin, cropXMax - 1)
                val xWeight = srcX - x0

                val c00 = getPixelRgb(frame, x0, y0)
                val c10 = getPixelRgb(frame, x1, y0)
                val c01 = getPixelRgb(frame, x0, y1)
                val c11 = getPixelRgb(frame, x1, y1)

                for (ch in 0..2) {
                    val top = c00[ch] * (1 - xWeight) + c10[ch] * xWeight
                    val bottom = c01[ch] * (1 - xWeight) + c11[ch] * xWeight
                    val valInterp = (top * (1 - yWeight) + bottom * yWeight).toInt().coerceIn(0, 255)
                    buffer.put(valInterp.toByte())
                }
            }
        }

        buffer.rewind()
        return buffer
    }

    private fun getPixelRgb(frame: Frame, x: Int, y: Int): IntArray {
        val idx = (y * frame.width + x) * 3
        return intArrayOf(
            frame.rgbData[idx].toInt() and 0xFF,
            frame.rgbData[idx + 1].toInt() and 0xFF,
            frame.rgbData[idx + 2].toInt() and 0xFF
        )
    }
}
