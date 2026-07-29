package org.hound.vision

import org.hound.domain.Observation
import org.hound.domain.Tracker
import org.hound.domain.VisionMode
import org.hound.domain.VisionState
import org.hound.domain.cosineSimilarity

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

    suspend fun process(
        frame: Frame,
        mode: VisionMode,
        targetPrototype: FloatArray? = null
    ): PipelineResult {
        val startNs = System.nanoTime()
        val isLearning = (mode == VisionMode.LEARNING)

        try {
            val candidates = candidateFinder.candidates(frame, isLearning)

            var bestObs: Observation? = null
            var maxSimilarity = 0.0f
            var maxArea = -1.0f

            if (targetPrototype != null && candidates.isNotEmpty()) {
                for (cand in candidates) {
                    try {
                        val inputBuffer = CropPreprocessor.prepare(frame, cand)
                        val embedding = encoder.encode(inputBuffer)
                        val sim = cosineSimilarity(embedding, targetPrototype)

                        val isBetter = when {
                            sim > maxSimilarity -> true
                            sim == maxSimilarity && cand.areaFraction > maxArea -> true
                            else -> false
                        }

                        if (isBetter) {
                            maxSimilarity = sim
                            maxArea = cand.areaFraction
                            if (sim >= Tracker.MATCH_THRESHOLD) {
                                bestObs = Observation(
                                    box = cand.box,
                                    similarity = sim,
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
