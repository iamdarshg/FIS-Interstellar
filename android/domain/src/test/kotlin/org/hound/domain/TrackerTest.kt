package org.hound.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TrackerTest {

    private lateinit var tracker: Tracker
    private val box = BoundingBox(0.2f, 0.2f, 0.5f, 0.5f)

    @BeforeEach
    fun setUp() {
        tracker = Tracker()
    }

    @Test
    fun `reset sets state to IDLE with non-empty reason`() {
        val state = tracker.reset()
        assertEquals(VisionMode.IDLE, state.mode)
        assertFalse(state.reason.isBlank())
        assertNull(state.targetBox)
    }

    @Test
    fun `startSearch transitions to SEARCHING with non-empty reason`() {
        val state = tracker.startSearch(1000L)
        assertEquals(VisionMode.SEARCHING, state.mode)
        assertFalse(state.reason.isBlank())
        assertNull(state.targetBox)
    }

    @Test
    fun `score below threshold remains SEARCHING`() {
        tracker.startSearch(1000L)
        val obs = Observation(box = box, similarity = 0.77f, timestampMs = 1000L)
        val state = tracker.update(obs, 1000L)
        assertEquals(VisionMode.SEARCHING, state.mode)
        assertNull(state.targetBox)
        assertFalse(state.reason.isBlank())
    }

    @Test
    fun `score at or above threshold transitions to TRACKED`() {
        tracker.startSearch(1000L)
        val obs = Observation(box = box, similarity = 0.78f, timestampMs = 1000L)
        val state = tracker.update(obs, 1000L)
        assertEquals(VisionMode.TRACKED, state.mode)
        assertEquals(box, state.targetBox)
        assertEquals(0.78f, state.confidence)
        assertFalse(state.reason.isBlank())
    }

    @Test
    fun `transition table for occlusion timing`() {
        tracker.startSearch(1000L)
        val obs = Observation(box = box, similarity = 0.85f, timestampMs = 1000L)
        tracker.update(obs, 1000L) // TRACKED at t=1000

        // Missing at t=1349 (349 ms missing) -> TRACKED
        val state349 = tracker.update(null, 1349L)
        assertEquals(VisionMode.TRACKED, state349.mode)
        assertNotNull(state349.targetBox)
        assertFalse(state349.reason.isBlank())

        // Missing at t=1350 (350 ms missing) -> OCCLUDED
        val state350 = tracker.update(null, 1350L)
        assertEquals(VisionMode.OCCLUDED, state350.mode)
        assertNotNull(state350.targetBox)
        assertFalse(state350.reason.isBlank())

        // Missing at t=4000 (3000 ms missing) -> LOST
        val state3000 = tracker.update(null, 4000L)
        assertEquals(VisionMode.LOST, state3000.mode)
        assertNull(state3000.targetBox)
        assertFalse(state3000.reason.isBlank())

        // Reacquisition from LOST -> TRACKED
        val reObs = Observation(box = box, similarity = 0.80f, timestampMs = 4100L)
        val stateRe = tracker.update(reObs, 4100L)
        assertEquals(VisionMode.TRACKED, stateRe.mode)
        assertEquals(box, stateRe.targetBox)
    }

    @Test
    fun `constant velocity prediction and clamping`() {
        tracker.startSearch(1000L)
        val movingObs = Observation(box = BoundingBox(0.1f, 0.1f, 0.3f, 0.3f), similarity = 0.9f, vx = 0.5f, vy = 0.5f, timestampMs = 1000L)
        tracker.update(movingObs, 1000L)

        // Elapsed = 500 ms -> dx = 0.5 * 0.5 = 0.25, dy = 0.25
        val stateOccluded = tracker.update(null, 1500L)
        assertEquals(VisionMode.OCCLUDED, stateOccluded.mode)
        val predBox = stateOccluded.targetBox!!
        assertEquals(0.35f, predBox.xMin, 1e-4f)
        assertEquals(0.35f, predBox.yMin, 1e-4f)
        assertEquals(0.55f, predBox.xMax, 1e-4f)
        assertEquals(0.55f, predBox.yMax, 1e-4f)
    }

    @Test
    fun `timestamps moving backward are rejected`() {
        tracker.startSearch(1000L)
        assertThrows<IllegalArgumentException> {
            tracker.update(null, 999L)
        }
    }

    @Test
    fun `stale observations older than 250ms are rejected`() {
        tracker.startSearch(1000L)
        val staleObs = Observation(box = box, similarity = 0.9f, timestampMs = 700L)
        val state = tracker.update(staleObs, 1000L)
        assertEquals(VisionMode.SEARCHING, state.mode)
        assertNull(state.targetBox)
    }
}
