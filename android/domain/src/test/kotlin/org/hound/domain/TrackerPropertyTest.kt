package org.hound.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

data class StepInput(val deltaMs: Long, val hasObs: Boolean, val similarity: Float)

class TrackerPropertyTest {

    @Test
    fun `tracker property invariants over 1000 random sequences`() = runBlocking {
        val stepArb = Arb.bind(
            Arb.long(10L..500L),
            Arb.boolean(),
            Arb.float(0.5f..1.0f)
        ) { delta, hasObs, sim -> StepInput(delta, hasObs, sim) }

        val sequenceArb = Arb.list(stepArb, 5..30)

        checkAll(1000, sequenceArb) { steps ->
            val tracker = Tracker()
            tracker.startSearch(1000L)
            var currentClock = 1000L

            for (step in steps) {
                currentClock += step.deltaMs
                val obs = if (step.hasObs) {
                    Observation(
                        box = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f),
                        similarity = step.similarity,
                        timestampMs = currentClock
                    )
                } else {
                    null
                }

                val state = tracker.update(obs, currentClock)

                if (state.mode == VisionMode.IDLE) {
                    assertNull(state.targetBox, "IDLE state must never carry a target box")
                }

                if (state.mode == VisionMode.OCCLUDED) {
                    assertNotNull(tracker.lastReliableObservation, "OCCLUDED state must have a last reliable observation")
                }

                state.targetBox?.let { box ->
                    assertTrue(box.xMin.isFinite() && box.yMin.isFinite() && box.xMax.isFinite() && box.yMax.isFinite(), "BoundingBox coordinates must be finite")
                    assertTrue(box.xMin in 0.0f..1.0f && box.yMin in 0.0f..1.0f, "Box min coordinates must be in [0, 1]")
                    assertTrue(box.xMax in 0.0f..1.0f && box.yMax in 0.0f..1.0f, "Box max coordinates must be in [0, 1]")
                    assertTrue(box.xMin <= box.xMax && box.yMin <= box.yMax, "Box min <= max")
                }
            }
        }
    }
}
