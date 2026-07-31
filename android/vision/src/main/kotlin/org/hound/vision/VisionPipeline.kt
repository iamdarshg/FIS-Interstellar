package org.hound.vision

import org.hound.domain.BoundingBox
import org.hound.domain.Observation
import org.hound.domain.Tracker
import org.hound.domain.VisionMode
import org.hound.domain.VisionState
import org.hound.domain.cosineSimilarity
import java.util.concurrent.atomic.AtomicLong

data class PipelineResult(
    val candidates: List<Candidate>,
    val bestObservation: Observation?,
    val visionState: VisionState,
    val previewJpeg: ByteArray?,
    val metrics: PipelineMetrics
)

class VisionPipeline(
    private val candidateFinder: CandidateFinder,
    private val encoder: EmbeddingEncoder,
    private val tracker: Tracker
) {
    private val frameCounter = AtomicLong(0L)

    suspend fun process(
        frame: Frame,
        mode: VisionMode,
        targetPrototype: FloatArray? = null,
        targetPrototypes: List<FloatArray>? = null
    ): PipelineResult {
        val startNs = System.nanoTime()
        val isLearning = (mode == VisionMode.LEARNING)
        val count = frameCounter.incrementAndGet()

        try {
            val lastObs = tracker.lastReliableObservation
            val isTracked = (mode == VisionMode.TRACKED || tracker.currentMode == VisionMode.TRACKED)

            val candidates: List<Candidate> = if (isTracked && lastObs != null && count % 4L != 0L) {
                // High-FPS Fast Track Path: Expand previous box by 15% and skip heavy detector overhead
                val box = lastObs.box
                val w = box.xMax - box.xMin
                val h = box.yMax - box.yMin
                val expandX = w * 0.15f
                val expandY = h * 0.15f
                val fastBox = BoundingBox(
                    xMin = (box.xMin - expandX).coerceIn(0.0f, 1.0f),
                    yMin = (box.yMin - expandY).coerceIn(0.0f, 1.0f),
                    xMax = (box.xMax + expandX).coerceIn(0.0f, 1.0f),
                    yMax = (box.yMax + expandY).coerceIn(0.0f, 1.0f)
                )
                listOf(
                    Candidate(
                        box = fastBox,
                        areaFraction = (fastBox.xMax - fastBox.xMin) * (fastBox.yMax - fastBox.yMin),
                        source = CandidateSource.DETECTOR
                    )
                )
            } else {
                candidateFinder.candidates(frame, isLearning)
            }

            var bestObs: Observation? = null
            var maxSimilarity = 0.0f
            var maxArea = -1.0f

            val prototypesToTest = mutableListOf<FloatArray>()
            if (targetPrototype != null) prototypesToTest.add(targetPrototype)
            if (targetPrototypes != null) prototypesToTest.addAll(targetPrototypes)

            if (prototypesToTest.isNotEmpty() && candidates.isNotEmpty()) {
                for (cand in candidates) {
                    try {
                        val inputBuffer = CropPreprocessor.prepare(frame, cand)
                        val embedding = encoder.encode(inputBuffer)

                        var candSim = 0.0f
                        for (proto in prototypesToTest) {
                            val sim = cosineSimilarity(embedding, proto)
                            if (sim > candSim) {
                                candSim = sim
                            }
                        }

                        val isBetter = when {
                            candSim > maxSimilarity -> true
                            candSim == maxSimilarity && cand.areaFraction > maxArea -> true
                            else -> false
                        }

                        if (isBetter) {
                            maxSimilarity = candSim
                            maxArea = cand.areaFraction
                            if (candSim >= Tracker.MATCH_THRESHOLD) {
                                bestObs = Observation(
                                    box = cand.box,
                                    similarity = candSim,
                                    timestampMs = frame.timestampMs
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // Individual candidate exception does not abort other candidates
                    }
                }
            }

            val visionState = tracker.update(bestObs, frame.timestampMs)

            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
            val metrics = PipelineMetrics(
                frameTimestampMs = frame.timestampMs,
                candidateCount = candidates.size,
                bestSimilarity = maxSimilarity,
                totalLatencyMs = elapsedMs,
                isError = false
            )

            return PipelineResult(
                candidates = candidates,
                bestObservation = bestObs,
                visionState = visionState,
                previewJpeg = null,
                metrics = metrics
            )
        } catch (e: Exception) {
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
            val errorState = tracker.update(null, frame.timestampMs)
            return PipelineResult(
                candidates = emptyList(),
                bestObservation = null,
                visionState = errorState,
                previewJpeg = null,
                metrics = PipelineMetrics(
                    frameTimestampMs = frame.timestampMs,
                    candidateCount = 0,
                    bestSimilarity = 0.0f,
                    totalLatencyMs = elapsedMs,
                    isError = true
                )
            )
        }
    }
}
