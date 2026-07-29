package org.hound.domain

class Tracker {

    companion object {
        const val MATCH_THRESHOLD = 0.78f
        const val OCCLUSION_GRACE_MS = 350L
        const val OCCLUSION_TIMEOUT_MS = 3000L
        const val MAX_STALE_MS = 250L
    }

    var currentMode: VisionMode = VisionMode.IDLE
        private set

    var lastReliableObservation: Observation? = null
        private set

    private var lastTrackedTimeMs: Long = 0L
    private var lastUpdateNowMs: Long = -1L

    fun reset(): VisionState {
        currentMode = VisionMode.IDLE
        lastReliableObservation = null
        lastTrackedTimeMs = 0L
        lastUpdateNowMs = -1L
        return VisionState(
            timestampMs = 0L,
            mode = VisionMode.IDLE,
            confidence = 0.0f,
            targetBox = null,
            reason = "tracker_reset"
        )
    }

    fun startLearn(nowMs: Long): VisionState {
        require(nowMs >= 0) { "Timestamp must be non-negative" }
        if (lastUpdateNowMs != -1L && nowMs < lastUpdateNowMs) {
            throw IllegalArgumentException("Timestamp moved backward from $lastUpdateNowMs to $nowMs")
        }
        lastUpdateNowMs = nowMs
        currentMode = VisionMode.LEARNING
        return VisionState(
            timestampMs = nowMs,
            mode = VisionMode.LEARNING,
            confidence = 0.0f,
            targetBox = null,
            reason = "learn_started"
        )
    }

    fun startSearch(nowMs: Long): VisionState {
        require(nowMs >= 0) { "Timestamp must be non-negative" }
        if (lastUpdateNowMs != -1L && nowMs < lastUpdateNowMs) {
            throw IllegalArgumentException("Timestamp moved backward from $lastUpdateNowMs to $nowMs")
        }
        lastUpdateNowMs = nowMs
        currentMode = VisionMode.SEARCHING
        return VisionState(
            timestampMs = nowMs,
            mode = VisionMode.SEARCHING,
            confidence = 0.0f,
            targetBox = null,
            reason = "search_started"
        )
    }

    fun update(observation: Observation?, nowMs: Long): VisionState {
        require(nowMs >= 0) { "Timestamp must be non-negative" }
        if (lastUpdateNowMs != -1L && nowMs < lastUpdateNowMs) {
            throw IllegalArgumentException("Timestamp moved backward from $lastUpdateNowMs to $nowMs")
        }
        lastUpdateNowMs = nowMs

        val validObs = if (observation != null) {
            if (nowMs - observation.timestampMs > MAX_STALE_MS) {
                null
            } else if (observation.similarity < MATCH_THRESHOLD) {
                null
            } else {
                observation
            }
        } else {
            null
        }

        return when (currentMode) {
            VisionMode.IDLE -> {
                VisionState(
                    timestampMs = nowMs,
                    mode = VisionMode.IDLE,
                    confidence = 0.0f,
                    targetBox = null,
                    reason = "idle_no_action"
                )
            }
            VisionMode.LEARNING -> {
                VisionState(
                    timestampMs = nowMs,
                    mode = VisionMode.LEARNING,
                    confidence = 0.0f,
                    targetBox = null,
                    reason = "learning_in_progress"
                )
            }
            VisionMode.SEARCHING -> {
                if (validObs != null) {
                    currentMode = VisionMode.TRACKED
                    lastReliableObservation = validObs
                    lastTrackedTimeMs = nowMs
                    VisionState(
                        timestampMs = nowMs,
                        mode = VisionMode.TRACKED,
                        confidence = validObs.similarity,
                        targetBox = validObs.box,
                        reason = "target_detected"
                    )
                } else {
                    VisionState(
                        timestampMs = nowMs,
                        mode = VisionMode.SEARCHING,
                        confidence = 0.0f,
                        targetBox = null,
                        reason = "searching_no_match"
                    )
                }
            }
            VisionMode.TRACKED, VisionMode.OCCLUDED, VisionMode.LOST -> {
                if (validObs != null) {
                    currentMode = VisionMode.TRACKED
                    lastReliableObservation = validObs
                    lastTrackedTimeMs = nowMs
                    VisionState(
                        timestampMs = nowMs,
                        mode = VisionMode.TRACKED,
                        confidence = validObs.similarity,
                        targetBox = validObs.box,
                        reason = "target_tracked"
                    )
                } else {
                    val lastObs = lastReliableObservation
                    val elapsedMs = if (lastObs != null) nowMs - lastTrackedTimeMs else Long.MAX_VALUE

                    if (lastObs != null && elapsedMs < OCCLUSION_GRACE_MS) {
                        currentMode = VisionMode.TRACKED
                        val predBox = predictBox(lastObs, elapsedMs)
                        VisionState(
                            timestampMs = nowMs,
                            mode = VisionMode.TRACKED,
                            confidence = lastObs.similarity,
                            targetBox = predBox,
                            reason = "tracked_occlusion_grace"
                        )
                    } else if (lastObs != null && elapsedMs < OCCLUSION_TIMEOUT_MS) {
                        currentMode = VisionMode.OCCLUDED
                        val predBox = predictBox(lastObs, elapsedMs)
                        VisionState(
                            timestampMs = nowMs,
                            mode = VisionMode.OCCLUDED,
                            confidence = lastObs.similarity,
                            targetBox = predBox,
                            reason = "target_occluded"
                        )
                    } else {
                        currentMode = VisionMode.LOST
                        VisionState(
                            timestampMs = nowMs,
                            mode = VisionMode.LOST,
                            confidence = 0.0f,
                            targetBox = null,
                            reason = "target_lost_timeout"
                        )
                    }
                }
            }
        }
    }

    private fun predictBox(lastObs: Observation, elapsedMs: Long): BoundingBox {
        val dtSec = elapsedMs / 1000.0f
        val dx = lastObs.vx * dtSec
        val dy = lastObs.vy * dtSec

        val w = lastObs.box.xMax - lastObs.box.xMin
        val h = lastObs.box.yMax - lastObs.box.yMin

        val xMin = (lastObs.box.xMin + dx).coerceIn(0.0f, (1.0f - w).coerceAtLeast(0.0f))
        val yMin = (lastObs.box.yMin + dy).coerceIn(0.0f, (1.0f - h).coerceAtLeast(0.0f))
        val xMax = (xMin + w).coerceIn(xMin, 1.0f)
        val yMax = (yMin + h).coerceIn(yMin, 1.0f)

        return BoundingBox(xMin = xMin, yMin = yMin, xMax = xMax, yMax = yMax)
    }
}
