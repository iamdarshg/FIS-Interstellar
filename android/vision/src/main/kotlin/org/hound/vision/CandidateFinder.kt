package org.hound.vision

interface CandidateFinder {
    suspend fun candidates(frame: Frame, isLearning: Boolean = false): List<Candidate>
}
