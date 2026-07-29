package org.hound.dashboard

import org.hound.domain.VisionState
import java.util.concurrent.atomic.AtomicReference

class DashboardState {

    val visionState = AtomicReference<VisionState?>(null)
    val latestPreviewJpeg = AtomicReference<ByteArray?>(null)
    val batteryPercentage = AtomicReference<Int>(100)
    val lastMetrics = AtomicReference<String>("{}")

    var onLearnTriggered: (() -> Unit)? = null
    var onStartTriggered: (() -> Unit)? = null
    var onStopTriggered: (() -> Unit)? = null
    var onResetTriggered: (() -> Unit)? = null
}
