package org.hound.vision

data class PipelineMetrics(
    val frameTimestampMs: Long,
    val candidateCount: Int,
    val bestSimilarity: Float,
    val totalLatencyMs: Long,
    val isError: Boolean = false
)
