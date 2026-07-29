package org.hound.domain

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class MotionKind {
    STOP,
    ROTATE_LEFT,
    ROTATE_RIGHT,
    DRIVE_FORWARD
}

@Serializable
enum class VisionMode {
    IDLE,
    LEARNING,
    SEARCHING,
    TRACKED,
    OCCLUDED,
    LOST
}

@Serializable
data class BoundingBox(
    val xMin: Float,
    val yMin: Float,
    val xMax: Float,
    val yMax: Float
) {
    init {
        require(xMin.isFinite() && yMin.isFinite() && xMax.isFinite() && yMax.isFinite()) {
            "BoundingBox coordinates must be finite"
        }
        require(xMin in 0.0f..1.0f) { "xMin $xMin must be in [0.0, 1.0]" }
        require(yMin in 0.0f..1.0f) { "yMin $yMin must be in [0.0, 1.0]" }
        require(xMax in 0.0f..1.0f) { "xMax $xMax must be in [0.0, 1.0]" }
        require(yMax in 0.0f..1.0f) { "yMax $yMax must be in [0.0, 1.0]" }
        require(xMin <= xMax) { "xMin $xMin must be <= xMax $xMax" }
        require(yMin <= yMax) { "yMin $yMin must be <= yMax $yMax" }
    }
}

@Serializable
data class MotionIntent(
    val protocolVersion: Int = 1,
    val type: String = "motion_intent",
    val id: String,
    val sentAtMs: Long,
    val intent: MotionKind,
    val durationMs: Int,
    val reason: String
) {
    init {
        require(protocolVersion == 1) { "protocolVersion must be 1" }
        require(type == "motion_intent") { "type must be motion_intent" }
        require(sentAtMs >= 0) { "sentAtMs must be >= 0" }
        require(durationMs in 0..500) { "durationMs $durationMs must be in [0, 500]" }
        try {
            UUID.fromString(id)
        } catch (e: Exception) {
            throw IllegalArgumentException("id must be a valid UUID", e)
        }
    }
}

@Serializable
data class VisionState(
    val protocolVersion: Int = 1,
    val type: String = "vision_state",
    val timestampMs: Long,
    val mode: VisionMode,
    val confidence: Float,
    val targetBox: BoundingBox? = null,
    val reason: String
) {
    init {
        require(protocolVersion == 1) { "protocolVersion must be 1" }
        require(type == "vision_state") { "type must be vision_state" }
        require(timestampMs >= 0) { "timestampMs must be >= 0" }
        require(confidence.isFinite() && confidence in 0.0f..1.0f) {
            "confidence $confidence must be in [0.0, 1.0]"
        }
    }
}

@Serializable
data class CommandAck(
    val protocolVersion: Int = 1,
    val type: String = "command_ack",
    val commandId: String,
    val accepted: Boolean,
    val reason: String
) {
    init {
        require(protocolVersion == 1) { "protocolVersion must be 1" }
        require(type == "command_ack") { "type must be command_ack" }
        try {
            UUID.fromString(commandId)
        } catch (e: Exception) {
            throw IllegalArgumentException("commandId must be a valid UUID", e)
        }
    }
}
