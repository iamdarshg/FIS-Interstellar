package org.hound.vision

import kotlinx.coroutines.runBlocking
import org.hound.domain.BoundingBox
import org.hound.domain.Tracker
import org.hound.domain.VisionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class VisionPipelineTest {

    private val testFrame = Frame(width = 320, height = 240, rgbData = ByteArray(320 * 240 * 3), timestampMs = 1000L, onClose = {})

    @Test
    fun testHighestCosineWins() = runBlocking {
        val box1Large = BoundingBox(0.1f, 0.1f, 0.6f, 0.6f)
        val box2Small = BoundingBox(0.1f, 0.1f, 0.3f, 0.3f)

        val proto = FloatArray(576) { 1.0f / 24.0f }
        val highSimEmbedding = proto.copyOf()
        val lowSimEmbedding = FloatArray(576) { if (it == 0) 1.0f else 0.0f }

        val finder = FakeCandidateFinder(listOf(box1Large, box2Small))

        var callIdx = 0
        val encoder = object : EmbeddingEncoder {
            override fun encode(input: ByteBuffer): FloatArray {
                callIdx++
                return if (callIdx == 1) lowSimEmbedding else highSimEmbedding
            }
            override fun close() {}
        }

        val tracker = Tracker()
        tracker.startSearch(1000L)
        val pipeline = VisionPipeline(finder, encoder, tracker)

        val result = pipeline.process(testFrame, VisionMode.SEARCHING, proto)

        assertEquals(VisionMode.TRACKED, result.visionState.mode)
        assertEquals(box2Small, result.bestObservation?.box)
    }

    @Test
    fun testScoreBelowThresholdProducesNoObservation() = runBlocking {
        val box = BoundingBox(0.1f, 0.1f, 0.3f, 0.3f)
        val proto = FloatArray(576) { 1.0f / 24.0f }
        val orthogonalEmbedding = FloatArray(576) { 0.0f }

        val finder = FakeCandidateFinder(listOf(box))
        val encoder = FakeEmbeddingEncoder(orthogonalEmbedding)
        val tracker = Tracker()
        tracker.startSearch(1000L)

        val pipeline = VisionPipeline(finder, encoder, tracker)
        val result = pipeline.process(testFrame, VisionMode.SEARCHING, proto)

        assertEquals(VisionMode.SEARCHING, result.visionState.mode)
        assertNull(result.bestObservation)
    }

    @Test
    fun testCandidateExceptionIsolation() = runBlocking {
        val box1Large = BoundingBox(0.1f, 0.1f, 0.6f, 0.6f)
        val box2Small = BoundingBox(0.1f, 0.1f, 0.3f, 0.3f)
        val proto = FloatArray(576) { 1.0f / 24.0f }

        val finder = FakeCandidateFinder(listOf(box1Large, box2Small))
        var callIdx = 0
        val failingEncoder = object : EmbeddingEncoder {
            override fun encode(input: ByteBuffer): FloatArray {
                callIdx++
                if (callIdx == 1) throw RuntimeException("Fault injected in candidate 1")
                return proto.copyOf()
            }
            override fun close() {}
        }

        val tracker = Tracker()
        tracker.startSearch(1000L)
        val pipeline = VisionPipeline(finder, failingEncoder, tracker)

        val result = pipeline.process(testFrame, VisionMode.SEARCHING, proto)

        assertEquals(VisionMode.TRACKED, result.visionState.mode)
        assertEquals(box2Small, result.bestObservation?.box)
    }
}
