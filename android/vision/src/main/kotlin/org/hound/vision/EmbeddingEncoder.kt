package org.hound.vision

import java.nio.ByteBuffer

interface EmbeddingEncoder : AutoCloseable {
    fun encode(input: ByteBuffer): FloatArray
}
