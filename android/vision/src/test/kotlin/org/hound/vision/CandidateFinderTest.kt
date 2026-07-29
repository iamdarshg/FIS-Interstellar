package org.hound.vision

import kotlinx.coroutines.runBlocking
import org.hound.domain.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCandidateFinder(private val rawBoxes: List<BoundingBox>) : CandidateFinder {
    override suspend fun candidates(frame: Frame, isLearning: Boolean): List<Candidate> {
        val candidatesList = mutableListOf<Candidate>()
        for (box in rawBoxes) {
            val areaFraction = (box.xMax - box.xMin) * (box.yMax - box.yMin)
            if (areaFraction in 0.01f..0.90f) {
                candidatesList.add(Candidate(box, areaFraction, CandidateSource.DETECTOR))
            }
        }
        val sorted = candidatesList.sortedByDescending { it.areaFraction }.take(5)
        if (sorted.isEmpty() && isLearning) {
            return listOf(Candidate(BoundingBox(0.2f, 0.2f, 0.8f, 0.8f), 0.36f, CandidateSource.LEARN_CENTER_FALLBACK))
        }
        return sorted
    }
}

class CandidateFinderTest {

    private val testFrame = Frame(width = 320, height = 240, rgbData = ByteArray(320 * 240 * 3), timestampMs = 1000L, onClose = {})

    @Test
    fun testSortingAndAreaFiltering() = runBlocking {
        val boxes = listOf(
            BoundingBox(0.0f, 0.0f, 0.05f, 0.05f),
            BoundingBox(0.0f, 0.0f, 0.96f, 0.96f),
            BoundingBox(0.1f, 0.1f, 0.3f, 0.3f),
            BoundingBox(0.1f, 0.1f, 0.5f, 0.5f)
        )
        val finder = FakeCandidateFinder(boxes)

        val result = finder.candidates(testFrame, isLearning = false)
        assertEquals(2, result.size)
        assertEquals(0.16f, result[0].areaFraction, 1e-4f)
        assertEquals(0.04f, result[1].areaFraction, 1e-4f)
    }

    @Test
    fun testLearningCenterFallbackWhenZeroCandidates() = runBlocking {
        val finder = FakeCandidateFinder(emptyList())

        val searchResult = finder.candidates(testFrame, isLearning = false)
        assertTrue(searchResult.isEmpty())

        val learnResult = finder.candidates(testFrame, isLearning = true)
        assertEquals(1, learnResult.size)
        assertEquals(CandidateSource.LEARN_CENTER_FALLBACK, learnResult[0].source)
        assertEquals(BoundingBox(0.2f, 0.2f, 0.8f, 0.8f), learnResult[0].box)
    }
}
