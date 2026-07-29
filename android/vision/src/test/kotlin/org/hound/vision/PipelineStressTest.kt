package org.hound.vision

import kotlinx.coroutines.runBlocking
import org.hound.domain.Tracker
import org.hound.domain.VisionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PipelineStressTest {

    @Test
    fun testPipelineStressBoundedProcessing() = runBlocking {
        val totalFramesSubmitted = 10_000
        val closedFrameCount = AtomicInteger(0)
        val processedFrameCount = AtomicInteger(0)
        var lastProcessedTimestamp = -1L

        val finder = FakeCandidateFinder(emptyList())
        val encoder = FakeEmbeddingEncoder()
        val tracker = Tracker()
        tracker.startSearch(1000L)
        val pipeline = VisionPipeline(finder, encoder, tracker)

        for (i in 0 until totalFramesSubmitted) {
            val timestamp = 1000L + i * 10L
            val frame = Frame(
                width = 320,
                height = 240,
                rgbData = ByteArray(320 * 240 * 3),
                timestampMs = timestamp,
                onClose = { closedFrameCount.incrementAndGet() }
            )

            frame.use { f ->
                val result = pipeline.process(f, VisionMode.SEARCHING)
                processedFrameCount.incrementAndGet()
                assertTrue(
                    "Processed frame timestamps must strictly increase",
                    result.metrics.frameTimestampMs > lastProcessedTimestamp
                )
                lastProcessedTimestamp = result.metrics.frameTimestampMs
            }
        }

        assertEquals(totalFramesSubmitted, processedFrameCount.get())
        assertEquals(totalFramesSubmitted, closedFrameCount.get())
    }
}
