package org.hound.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MissionControllerTest {

    private lateinit var controller: MissionController

    @BeforeEach
    fun setUp() {
        controller = MissionController(clock = { 1000L }, isStationaryBuild = true)
    }

    @Test
    fun `hunt before prototype throws NO_TARGET_LEARNED`() {
        val ex = assertThrows<IllegalStateException> {
            controller.startHunt()
        }
        assertEquals("NO_TARGET_LEARNED", ex.message)
    }

    @Test
    fun `learn while hunting throws PAUSE_BEFORE_LEARNING`() {
        controller.setHasPrototype(true)
        controller.startHunt()
        val ex = assertThrows<IllegalStateException> {
            controller.learn()
        }
        assertEquals("PAUSE_BEFORE_LEARNING", ex.message)
    }

    @Test
    fun `arm in stationary build throws MOVEMENT_DISABLED`() {
        val ex = assertThrows<IllegalStateException> {
            controller.setMovementArmed(true)
        }
        assertEquals("MOVEMENT_DISABLED", ex.message)
    }

    @Test
    fun `stationary mode motionFor returns STOP duration 0 and stationary_mode reason for all states`() {
        val states = listOf(
            VisionState(timestampMs = 1000L, mode = VisionMode.IDLE, confidence = 0f, reason = "idle"),
            VisionState(timestampMs = 1000L, mode = VisionMode.LEARNING, confidence = 0f, reason = "learn"),
            VisionState(timestampMs = 1000L, mode = VisionMode.SEARCHING, confidence = 0f, reason = "search"),
            VisionState(timestampMs = 1000L, mode = VisionMode.TRACKED, confidence = 0.9f, targetBox = BoundingBox(0.1f, 0.1f, 0.5f, 0.5f), reason = "tracked"),
            VisionState(timestampMs = 1000L, mode = VisionMode.OCCLUDED, confidence = 0.8f, targetBox = BoundingBox(0.1f, 0.1f, 0.5f, 0.5f), reason = "occluded"),
            VisionState(timestampMs = 1000L, mode = VisionMode.LOST, confidence = 0f, reason = "lost")
        )

        for (state in states) {
            val motion = controller.motionFor(state)
            assertEquals(MotionKind.STOP, motion.intent)
            assertEquals(0, motion.durationMs)
            assertEquals("stationary_mode", motion.reason)
            assertEquals(1, motion.protocolVersion)
        }
    }

    @Test
    fun `concurrency test with 20 coroutines issuing 10_000 calls`() = runBlocking {
        controller.setHasPrototype(true)
        val jobs = List(20) { workerId ->
            launch(Dispatchers.Default) {
                repeat(500) { iter ->
                    when (iter % 3) {
                        0 -> controller.pause()
                        1 -> controller.startHunt()
                        2 -> controller.logEvent("WORKER", "worker $workerId step $iter")
                    }
                }
            }
        }
        jobs.forEach { it.join() }

        assertTrue(controller.events.size <= 200, "Event ring buffer must be <= 200")
        val finalMotion = controller.motionFor(VisionState(timestampMs = 2000L, mode = VisionMode.TRACKED, confidence = 0.9f, reason = "test"))
        assertEquals(MotionKind.STOP, finalMotion.intent)
    }
}
