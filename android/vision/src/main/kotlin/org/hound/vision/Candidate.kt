package org.hound.vision

import org.hound.domain.BoundingBox

enum class CandidateSource {
    DETECTOR,
    LEARN_CENTER_FALLBACK
}

data class Candidate(
    val box: BoundingBox,
    val areaFraction: Float,
    val source: CandidateSource = CandidateSource.DETECTOR
)
