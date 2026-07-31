package org.hound.dashboard

import org.hound.domain.MapState
import org.hound.domain.MissionMode
import org.hound.domain.VisionState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class DashboardState {

    val visionState = AtomicReference<VisionState?>(null)
    val mapState = AtomicReference<MapState?>(null)
    val latestPreviewJpeg = AtomicReference<ByteArray?>(null)
    val batteryPercentage = AtomicReference<Int>(100)
    val lastMetrics = AtomicReference<String>("{}")
    val learnedTargets = CopyOnWriteArrayList<String>()
    val targetTypes = ConcurrentHashMap<String, String>() // "GOAL" or "HAZARD"
    val missionMode = AtomicReference<MissionMode>(MissionMode.OBJECT_FINDING)
    val multiPrototypeMemory = ConcurrentHashMap<String, CopyOnWriteArrayList<FloatArray>>()
    val processedFps = AtomicReference<Float>(28.5f)
    val rawRadarReadout = AtomicReference<String>("TARGET_DETECTED: YES | DIST: 2.45m | SIGNAL: 88%")

    var onLearnTriggered: (() -> Unit)? = null
    var onLearnTriggeredWithLabel: ((label: String) -> Unit)? = null
    var onLearnTriggeredWithLabelAndType: ((label: String, type: String) -> Unit)? = null
    var onStartTriggered: (() -> Unit)? = null
    var onStopTriggered: (() -> Unit)? = null
    var onResetTriggered: (() -> Unit)? = null
    var onMissionModeChanged: ((mode: MissionMode) -> Unit)? = null
    var onToggleTargetType: ((label: String) -> Unit)? = null
}
