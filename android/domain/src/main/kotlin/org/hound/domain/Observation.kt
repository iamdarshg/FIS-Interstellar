package org.hound.domain

data class Observation(
    val box: BoundingBox,
    val similarity: Float,
    val vx: Float = 0.0f,
    val vy: Float = 0.0f,
    val timestampMs: Long
) {
    init {
        require(similarity.isFinite() && similarity in -1.0f..1.0f) {
            "similarity $similarity must be in [-1, 1]"
        }
        require(vx.isFinite() && vy.isFinite()) { "Velocities must be finite" }
        require(timestampMs >= 0) { "timestampMs must be >= 0" }
    }
}
