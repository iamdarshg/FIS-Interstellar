package org.hound.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FakeEmbeddingEncoder(
    private val returnEmbedding: FloatArray = FloatArray(576) { 1.0f / 24.0f }
) : EmbeddingEncoder {

    var encodeCallCount = 0

    override fun encode(input: ByteBuffer): FloatArray {
        encodeCallCount++
        return returnEmbedding
    }

    override fun close() { }
}

class EmbeddingEncoderTest {

    @Test
    fun testFakeEmbeddingEncoderReturns576NormalizedOutput() {
        val encoder = FakeEmbeddingEncoder()
        val inputBuffer = ByteBuffer.allocateDirect(49152).order(ByteOrder.nativeOrder())
        val output = encoder.encode(inputBuffer)

        assertNotNull(output)
        assertEquals(576, output.size)
        assertEquals(1, encoder.encodeCallCount)
    }
}
