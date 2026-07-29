package org.hound.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class YuvToRgbTest {

    @Test
    fun testBlackImageConversion() {
        val width = 4
        val height = 4
        val y = ByteArray(width * height) { 0 }
        val u = ByteArray((width / 2) * (height / 2)) { 128.toByte() }
        val v = ByteArray((width / 2) * (height / 2)) { 128.toByte() }

        val rgb = YuvToRgb.convertYuv420ToRgb(y, u, v, width, height, width, width / 2, 1, 0)
        assertEquals(width * height * 3, rgb.size)

        for (i in rgb.indices) {
            assertEquals(0, rgb[i].toInt() and 0xFF)
        }
    }

    @Test
    fun testWhiteImageConversion() {
        val width = 4
        val height = 4
        val y = ByteArray(width * height) { 255.toByte() }
        val u = ByteArray((width / 2) * (height / 2)) { 128.toByte() }
        val v = ByteArray((width / 2) * (height / 2)) { 128.toByte() }

        val rgb = YuvToRgb.convertYuv420ToRgb(y, u, v, width, height, width, width / 2, 1, 0)

        for (i in rgb.indices) {
            assertEquals(255, rgb[i].toInt() and 0xFF)
        }
    }

    @Test
    fun testRedImageConversion() {
        val width = 4
        val height = 4
        val y = ByteArray(width * height) { 76.toByte() }
        val u = ByteArray((width / 2) * (height / 2)) { 85.toByte() }
        val v = ByteArray((width / 2) * (height / 2)) { 255.toByte() }

        val rgb = YuvToRgb.convertYuv420ToRgb(y, u, v, width, height, width, width / 2, 1, 0)

        val r = rgb[0].toInt() and 0xFF
        val g = rgb[1].toInt() and 0xFF
        val b = rgb[2].toInt() and 0xFF

        assertTrue("R channel should be close to 254, got $r", abs(r - 254) <= 2)
        assertTrue("G channel should be close to 0, got $g", abs(g - 0) <= 2)
        assertTrue("B channel should be close to 0, got $b", abs(b - 0) <= 2)
    }

    @Test
    fun testOddRowStrideConversion() {
        val width = 4
        val height = 4
        val yRowStride = 6
        val y = ByteArray(yRowStride * height) { 100.toByte() }
        val u = ByteArray(4 * 2) { 128.toByte() }
        val v = ByteArray(4 * 2) { 128.toByte() }

        val rgb = YuvToRgb.convertYuv420ToRgb(y, u, v, width, height, yRowStride, 4, 1, 0)
        assertEquals(width * height * 3, rgb.size)
    }

    @Test
    fun testRotationsMapCornersCorrectly() {
        val width = 2
        val height = 2
        val y = byteArrayOf(255.toByte(), 0, 0, 0)
        val u = byteArrayOf(128.toByte())
        val v = byteArrayOf(128.toByte())

        val rgb0 = YuvToRgb.convertYuv420ToRgb(y, u, v, width, height, 2, 1, 1, 0)
        assertEquals(255, rgb0[0].toInt() and 0xFF)

        val rgb90 = YuvToRgb.convertYuv420ToRgb(y, u, v, width, height, 2, 1, 1, 90)
        assertEquals(255, rgb90[3].toInt() and 0xFF)

        val rgb180 = YuvToRgb.convertYuv420ToRgb(y, u, v, width, height, 2, 1, 1, 180)
        assertEquals(255, rgb180[9].toInt() and 0xFF)

        val rgb270 = YuvToRgb.convertYuv420ToRgb(y, u, v, width, height, 2, 1, 1, 270)
        assertEquals(255, rgb270[6].toInt() and 0xFF)
    }
}
