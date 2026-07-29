package org.hound.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.sqrt

class TargetLearnerTest {

    @Test
    fun `buildPrototype aggregates and normalizes correctly`() {
        val samples = List(8) {
            floatArrayOf(1.0f, 2.0f, 3.0f)
        }
        val proto = TargetLearner.buildPrototype(samples)
        assertEquals(3, proto.size)

        var sumSq = 0.0
        for (v in proto) sumSq += v.toDouble() * v.toDouble()
        assertEquals(1.0, sqrt(sumSq), 1e-5)

        val expectedNorm = l2Normalize(floatArrayOf(1.0f, 2.0f, 3.0f))
        for (i in 0..2) {
            assertEquals(expectedNorm[i], proto[i], 1e-5f)
        }
    }

    @Test
    fun `buildPrototype rejects fewer than 8 or more than 32 samples`() {
        val sample = floatArrayOf(1.0f, 0.0f)
        assertThrows<IllegalArgumentException> {
            TargetLearner.buildPrototype(List(7) { sample })
        }
        assertThrows<IllegalArgumentException> {
            TargetLearner.buildPrototype(List(33) { sample })
        }
    }

    @Test
    fun `buildPrototype rejects mismatched sample dimensions`() {
        val samples = List(8) { idx ->
            if (idx == 3) floatArrayOf(1.0f) else floatArrayOf(1.0f, 2.0f)
        }
        assertThrows<IllegalArgumentException> {
            TargetLearner.buildPrototype(samples)
        }
    }

    @Test
    fun `buildPrototype rejects non-finite elements`() {
        val samples = List(8) { idx ->
            if (idx == 0) floatArrayOf(Float.NaN, 2.0f) else floatArrayOf(1.0f, 2.0f)
        }
        assertThrows<IllegalArgumentException> {
            TargetLearner.buildPrototype(samples)
        }
    }
}
