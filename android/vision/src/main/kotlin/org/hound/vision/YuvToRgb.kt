package org.hound.vision

object YuvToRgb {

    fun convertYuv420ToRgb(
        yBuffer: ByteArray,
        uBuffer: ByteArray,
        vBuffer: ByteArray,
        width: Int,
        height: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        rotationDegrees: Int = 0
    ): ByteArray {
        val rgbUnrotated = ByteArray(width * height * 3)

        for (y in 0 until height) {
            val yRowStart = y * yRowStride
            val uvRowStart = (y / 2) * uvRowStride

            for (x in 0 until width) {
                val yIndex = yRowStart + x
                val uvIndex = uvRowStart + (x / 2) * uvPixelStride

                val yVal = (yBuffer[yIndex].toInt() and 0xFF)
                val uVal = (uBuffer[uvIndex].toInt() and 0xFF) - 128
                val vVal = (vBuffer[uvIndex].toInt() and 0xFF) - 128

                val r = (yVal + ((359 * vVal) shr 8)).coerceIn(0, 255)
                val g = (yVal - ((88 * uVal + 183 * vVal) shr 8)).coerceIn(0, 255)
                val b = (yVal + ((454 * uVal) shr 8)).coerceIn(0, 255)

                val outIdx = (y * width + x) * 3
                rgbUnrotated[outIdx] = r.toByte()
                rgbUnrotated[outIdx + 1] = g.toByte()
                rgbUnrotated[outIdx + 2] = b.toByte()
            }
        }

        return when (rotationDegrees) {
            90 -> rotate90(rgbUnrotated, width, height)
            180 -> rotate180(rgbUnrotated, width, height)
            270 -> rotate270(rgbUnrotated, width, height)
            else -> rgbUnrotated
        }
    }

    private fun rotate90(src: ByteArray, w: Int, h: Int): ByteArray {
        val dst = ByteArray(w * h * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val srcIdx = (y * w + x) * 3
                val dstX = h - 1 - y
                val dstY = x
                val dstIdx = (dstY * h + dstX) * 3
                dst[dstIdx] = src[srcIdx]
                dst[dstIdx + 1] = src[srcIdx + 1]
                dst[dstIdx + 2] = src[srcIdx + 2]
            }
        }
        return dst
    }

    private fun rotate180(src: ByteArray, w: Int, h: Int): ByteArray {
        val dst = ByteArray(w * h * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val srcIdx = (y * w + x) * 3
                val dstX = w - 1 - x
                val dstY = h - 1 - y
                val dstIdx = (dstY * w + dstX) * 3
                dst[dstIdx] = src[srcIdx]
                dst[dstIdx + 1] = src[srcIdx + 1]
                dst[dstIdx + 2] = src[srcIdx + 2]
            }
        }
        return dst
    }

    private fun rotate270(src: ByteArray, w: Int, h: Int): ByteArray {
        val dst = ByteArray(w * h * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val srcIdx = (y * w + x) * 3
                val dstX = y
                val dstY = w - 1 - x
                val dstIdx = (dstY * h + dstX) * 3
                dst[dstIdx] = src[srcIdx]
                dst[dstIdx + 1] = src[srcIdx + 1]
                dst[dstIdx + 2] = src[srcIdx + 2]
            }
        }
        return dst
    }
}
