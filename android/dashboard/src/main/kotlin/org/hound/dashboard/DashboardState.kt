package org.hound.dashboard

import org.hound.domain.MapState
import org.hound.domain.VisionState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class DashboardState {

    val visionState = AtomicReference<VisionState?>(null)
    val mapState = AtomicReference<MapState?>(null)
    val latestPreviewJpeg = AtomicReference<ByteArray?>(null)
    val batteryPercentage = AtomicReference<Int>(100)
    val lastMetrics = AtomicReference<String>("{}")
    val learnedTargets = CopyOnWriteArrayList<String>()

    var onLearnTriggered: (() -> Unit)? = null
    var onLearnTriggeredWithLabel: ((label: String) -> Unit)? = null
    var onStartTriggered: (() -> Unit)? = null
    var onStopTriggered: (() -> Unit)? = null
    var onResetTriggered: (() -> Unit)? = null
}
