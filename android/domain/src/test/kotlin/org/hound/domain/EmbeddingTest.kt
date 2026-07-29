package org.hound.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.floatArray
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.sqrt

import io.kotest.property.arbitrary.constant

class EmbeddingTest {

    @Test
    fun `l2Normalize exact cases`() {
        val norm = l2Normalize(floatArrayOf(3.0f, 4.0f))
        assertEquals(0.6f, norm[0], 1e-5f)
        assertEquals(0.8f, norm[1], 1e-5f)
    }

    @Test
    fun `cosineSimilarity exact cases`() {
        val v1 = floatArrayOf(1.0f, 0.0f)
        val v2 = floatArrayOf(1.0f, 0.0f)
        val v3 = floatArrayOf(0.0f, 1.0f)
        val v4 = floatArrayOf(-1.0f, 0.0f)

        assertEquals(1.0f, cosineSimilarity(v1, v2), 1e-5f)
        assertEquals(0.0f, cosineSimilarity(v1, v3), 1e-5f)
        assertEquals(-1.0f, cosineSimilarity(v1, v4), 1e-5f)
    }

    @Test
    fun `invalid vectors throw exceptions`() {
        assertThrows<IllegalArgumentException> { l2Normalize(floatArrayOf()) }
        assertThrows<IllegalArgumentException> { l2Normalize(floatArrayOf(0.0f, 0.0f)) }
        assertThrows<IllegalArgumentException> { l2Normalize(floatArrayOf(Float.NaN, 1.0f)) }
        assertThrows<IllegalArgumentException> { l2Normalize(floatArrayOf(Float.POSITIVE_INFINITY, 1.0f)) }

        assertThrows<IllegalArgumentException> { cosineSimilarity(floatArrayOf(1.0f), floatArrayOf(1.0f, 2.0f)) }
        assertThrows<IllegalArgumentException> { cosineSimilarity(floatArrayOf(), floatArrayOf()) }
        assertThrows<IllegalArgumentException> { cosineSimilarity(floatArrayOf(0.0f), floatArrayOf(1.0f)) }
    }

    @Test
    fun `kotest property tests for l2Normalize and cosineSimilarity`() = runBlocking {
        checkAll(1000, Arb.floatArray(Arb.constant(10), Arb.float(-100.0f..100.0f))) { arr ->
            val hasNonZero = arr.any { it != 0.0f && it.isFinite() }
            val allFinite = arr.all { it.isFinite() }

            if (hasNonZero && allFinite) {
                val normalized = l2Normalize(arr)
                var normSq = 0.0
                for (v in normalized) normSq += v.toDouble() * v.toDouble()
                assertEquals(1.0, sqrt(normSq), 1e-4)

                val scaled = FloatArray(arr.size) { i -> arr[i] * 2.5f }
                val normScaled = l2Normalize(scaled)
                for (i in arr.indices) {
                    assertEquals(normalized[i], normScaled[i], 1e-4f)
                }
            }
        }

        checkAll(1000, Arb.floatArray(Arb.constant(10), Arb.float(-100.0f..100.0f)), Arb.floatArray(Arb.constant(10), Arb.float(-100.0f..100.0f))) { a, b ->
            val aValid = a.any { it != 0.0f && it.isFinite() } && a.all { it.isFinite() }
            val bValid = b.any { it != 0.0f && it.isFinite() } && b.all { it.isFinite() }

            if (aValid && bValid) {
                val simAB = cosineSimilarity(a, b)
                val simBA = cosineSimilarity(b, a)
                assertEquals(simAB, simBA, 1e-5f)
                assertTrue(simAB in -1.0f..1.0f, "Cosine similarity must be in [-1, 1], was $simAB")
            }
        }
    }
}
