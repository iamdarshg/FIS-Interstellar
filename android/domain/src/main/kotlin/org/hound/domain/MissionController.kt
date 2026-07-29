package org.hound.domain

import java.util.UUID

data class DecisionEvent(
    val timestampMs: Long,
    val category: String,
    val message: String
)

class MissionController(
    private val clock: Clock = SystemClock,
    val isStationaryBuild: Boolean = true
) {
    private val lock = Any()

    var hasPrototype: Boolean = false
        private set

    var isArmed: Boolean = false
        private set

    var isHunting: Boolean = false
        private set

    var isLearning: Boolean = false
        private set

    private val eventRingBuffer = ArrayDeque<DecisionEvent>(200)

    val events: List<DecisionEvent>
        get() = synchronized(lock) { eventRingBuffer.toList() }

    fun logEvent(category: String, message: String) {
        synchronized(lock) {
            val event = DecisionEvent(clock.nowMs(), category, message)
            if (eventRingBuffer.size >= 200) {
                eventRingBuffer.removeFirst()
            }
            eventRingBuffer.addLast(event)
        }
    }

    fun setHasPrototype(learned: Boolean) {
        synchronized(lock) {
            hasPrototype = learned
            logEvent("MISSION", "Target prototype state set to learned=$learned")
        }
    }

    fun learn() {
        synchronized(lock) {
            if (isHunting) {
                logEvent("ERROR", "PAUSE_BEFORE_LEARNING")
                throw IllegalStateException("PAUSE_BEFORE_LEARNING")
            }
            isLearning = true
            logEvent("MISSION", "Learn mode started")
        }
    }

    fun startHunt() {
        synchronized(lock) {
            if (!hasPrototype) {
                logEvent("ERROR", "NO_TARGET_LEARNED")
                throw IllegalStateException("NO_TARGET_LEARNED")
            }
            isLearning = false
            isHunting = true
            logEvent("MISSION", "Hunt mode started")
        }
    }

    fun pause() {
        synchronized(lock) {
            isHunting = false
            isLearning = false
            logEvent("MISSION", "Mission paused")
        }
    }

    fun reset() {
        synchronized(lock) {
            isHunting = false
            isLearning = false
            hasPrototype = false
            isArmed = false
            logEvent("MISSION", "Mission reset")
        }
    }

    fun setMovementArmed(armed: Boolean) {
        synchronized(lock) {
            if (isStationaryBuild && armed) {
                logEvent("ERROR", "MOVEMENT_DISABLED")
                throw IllegalStateException("MOVEMENT_DISABLED")
            }
            isArmed = armed
            logEvent("MISSION", "Movement armed set to $armed")
        }
    }

    fun motionFor(state: VisionState): MotionIntent {
        synchronized(lock) {
            return MotionIntent(
                protocolVersion = 1,
                type = "motion_intent",
                id = UUID.randomUUID().toString(),
                sentAtMs = clock.nowMs(),
                intent = MotionKind.STOP,
                durationMs = 0,
                reason = "stationary_mode"
            )
        }
    }
}
