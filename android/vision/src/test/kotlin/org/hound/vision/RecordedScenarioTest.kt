package org.hound.vision

import org.hound.domain.BoundingBox
import org.hound.domain.Observation
import org.hound.domain.Tracker
import org.hound.domain.VisionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecordedScenarioTest {

    @Test
    fun testDistractorNeverReachesTracked() {
        val tracker = Tracker()
        tracker.startSearch(0L)

        // Distractor sequence with similarity 0.45 (< MATCH_THRESHOLD 0.78)
        for (i in 0 until 10) {
            val nowMs = i * 100L
            val obs = Observation(
                box = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f),
                similarity = 0.45f,
                vx = 0.0f,
                vy = 0.0f,
                timestampMs = nowMs
            )
            val state = tracker.update(obs, nowMs)
            assertNotEquals("Distractor must never reach TRACKED", VisionMode.TRACKED, state.mode)
            assertEquals(VisionMode.SEARCHING, state.mode)
        }
    }

    @Test
    fun testOcclusionAndReacquisition() {
        val tracker = Tracker()
        tracker.startSearch(0L)

        val targetBox = BoundingBox(0.3f, 0.3f, 0.7f, 0.7f)

        // Step 1: Confirm target -> TRACKED
        val obs1 = Observation(targetBox, 0.88f, 0.0f, 0.0f, 100L)
        val state1 = tracker.update(obs1, 100L)
        assertEquals(VisionMode.TRACKED, state1.mode)

        // Step 2: Missing target for < 350 ms -> Remains TRACKED
        val state2 = tracker.update(null, 300L)
        assertEquals(VisionMode.TRACKED, state2.mode)

        // Step 3: Missing target at 450 ms (>= 350 ms grace) -> OCCLUDED
        val state3 = tracker.update(null, 450L)
        assertEquals(VisionMode.OCCLUDED, state3.mode)

        // Step 4: Reacquire target -> TRACKED
        val obs2 = Observation(targetBox, 0.85f, 0.0f, 0.0f, 600L)
        val state4 = tracker.update(obs2, 600L)
        assertEquals(VisionMode.TRACKED, state4.mode)
    }

    @Test
    fun testOcclusionTimeoutToLost() {
        val tracker = Tracker()
        tracker.startSearch(0L)

        val targetBox = BoundingBox(0.3f, 0.3f, 0.7f, 0.7f)
        val obs1 = Observation(targetBox, 0.88f, 0.0f, 0.0f, 100L)
        tracker.update(obs1, 100L)

        // Missing target for 3500 ms (> 3000 ms timeout) -> LOST
        val stateLost = tracker.update(null, 3600L)
        assertEquals(VisionMode.LOST, stateLost.mode)
    }

    @Test
    fun testLightingVariationReplacesTarget() {
        val tracker = Tracker()
        tracker.startSearch(0L)

        val targetBox = BoundingBox(0.3f, 0.3f, 0.7f, 0.7f)
        for (i in 1..5) {
            val nowMs = i * 100L
            val sim = 0.80f + (i % 2) * 0.05f
            val obs = Observation(targetBox, sim, 0.0f, 0.0f, nowMs)
            val state = tracker.update(obs, nowMs)
            assertEquals(VisionMode.TRACKED, state.mode)
        }
    }
}
