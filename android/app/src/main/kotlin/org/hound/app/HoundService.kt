package org.hound.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.hound.dashboard.DashboardState
import org.hound.dashboard.HttpControlServer
import org.hound.domain.MotionIntent
import org.hound.domain.MotionKind
import org.hound.domain.VisionMode
import org.hound.domain.VisionState

enum class ServiceStatus {
    STOPPED,
    RUNNING,
    ERROR
}

enum class ServiceErrorCode {
    NONE,
    CAMERA_PERMISSION,
    CAMERA_INIT,
    MODEL_CONTRACT_MISMATCH
}

data class ServiceHealth(
    val status: ServiceStatus = ServiceStatus.STOPPED,
    val errorCode: ServiceErrorCode = ServiceErrorCode.NONE,
    val message: String = ""
)

class HoundService : Service() {

    companion object {
        const val ACTION_START = "org.hound.app.START"
        const val ACTION_STOP = "org.hound.app.STOP"
        const val ACTION_RESET = "org.hound.app.RESET"

        const val CHANNEL_ID = "hound_service"
        const val NOTIFICATION_ID = 1001

        private val _healthState = MutableStateFlow(ServiceHealth())
        val healthState: StateFlow<ServiceHealth> = _healthState.asStateFlow()

        var cameraInitializer: ((Context) -> Unit)? = null
        var activeDashboardState: DashboardState? = null
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var controlServer: HttpControlServer? = null
    private var piTransport: PiTransport? = null
    private var piDiscoveryJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    val dashboardState = DashboardState()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
            ACTION_RESET -> handleReset()
        }
        return START_STICKY
    }

    private fun handleStart() {
        activeDashboardState = dashboardState

        if (_healthState.value.status == ServiceStatus.RUNNING) {
            return
        }

        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            _healthState.value = ServiceHealth(
                status = ServiceStatus.ERROR,
                errorCode = ServiceErrorCode.CAMERA_PERMISSION,
                message = "Camera permission not granted"
            )
            return
        }

        createNotificationChannel()
        val notification = createNotification("HOUND Vision Running")
        startForeground(NOTIFICATION_ID, notification)

        acquireWakeLock()

        try {
            cameraInitializer?.invoke(this)

            if (controlServer == null) {
                controlServer = HttpControlServer(dashboardState, port = 8080).apply {
                    start()
                }
            }

            dashboardState.onStartTriggered = {
                dashboardState.visionState.set(
                    VisionState(
                        timestampMs = System.currentTimeMillis(),
                        mode = VisionMode.SEARCHING,
                        confidence = 0.0f,
                        targetBox = null,
                        reason = "search_started_by_user"
                    )
                )
                serviceScope.launch { sendMotion(MotionKind.DRIVE_FORWARD) }
            }
            dashboardState.onLearnTriggered = {
                dashboardState.visionState.set(
                    VisionState(
                        timestampMs = System.currentTimeMillis(),
                        mode = VisionMode.LEARNING,
                        confidence = 0.0f,
                        targetBox = null,
                        reason = "learning_started_by_user"
                    )
                )
            }
            dashboardState.onStopTriggered = {
                dashboardState.visionState.set(
                    VisionState(
                        timestampMs = System.currentTimeMillis(),
                        mode = VisionMode.IDLE,
                        confidence = 0.0f,
                        targetBox = null,
                        reason = "stopped_by_user"
                    )
                )
                serviceScope.launch { sendMotion(MotionKind.STOP) }
            }
            dashboardState.onResetTriggered = {
                dashboardState.learnedTargets.clear()
                dashboardState.visionState.set(
                    VisionState(
                        timestampMs = System.currentTimeMillis(),
                        mode = VisionMode.IDLE,
                        confidence = 0.0f,
                        targetBox = null,
                        reason = "reset_by_user"
                    )
                )
                serviceScope.launch { sendMotion(MotionKind.STOP) }
            }

            if (piTransport == null) {
                piDiscoveryJob = serviceScope.launch {
                    val resolved = PiEndpointResolver().resolve(this@HoundService)
                    if (resolved != null) {
                        piTransport = TcpPiTransport(
                            host = resolved.host,
                            port = resolved.port
                        )
                        _healthState.value = _healthState.value.copy(
                            message = "Pi discovered at ${resolved.host}:${resolved.port}"
                        )
                    } else {
                        _healthState.value = _healthState.value.copy(
                            message = "Pi control endpoint not found on the local network"
                        )
                    }
                }
            }

            dashboardState.visionState.set(
                VisionState(
                    timestampMs = System.currentTimeMillis(),
                    mode = VisionMode.IDLE,
                    confidence = 0.0f,
                    targetBox = null,
                    reason = "SERVICE_RUNNING"
                )
            )

            _healthState.value = ServiceHealth(
                status = ServiceStatus.RUNNING,
                errorCode = ServiceErrorCode.NONE,
                message = "Service running cleanly"
            )
        } catch (e: Exception) {
            releaseWakeLock()
            _healthState.value = ServiceHealth(
                status = ServiceStatus.ERROR,
                errorCode = ServiceErrorCode.CAMERA_INIT,
                message = "Camera init failed: ${e.message}"
            )
        }
    }

    private fun handleStop() {
        activeDashboardState = null
        piDiscoveryJob?.cancel()
        piDiscoveryJob = null
        piTransport?.close()
        piTransport = null
        controlServer?.stop()
        controlServer = null
        dashboardState.onLearnTriggered = null
        dashboardState.onStartTriggered = null
        dashboardState.onStopTriggered = null
        dashboardState.onResetTriggered = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        _healthState.value = ServiceHealth(
            status = ServiceStatus.STOPPED,
            errorCode = ServiceErrorCode.NONE,
            message = "Service stopped"
        )
        stopSelf()
    }

    private fun handleReset() {
        handleStop()
        handleStart()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HOUND::Vision")
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } finally {
            wakeLock = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HOUND Vision Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HOUND Headless Service")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        try {
            activeDashboardState = null
            serviceScope.cancel()
            releaseWakeLock()
        } finally {
            super.onDestroy()
        }
    }

    private suspend fun sendMotion(intent: MotionKind) {
        val transport = piTransport ?: return
        try {
            transport.send(
                MotionIntent(
                    id = java.util.UUID.randomUUID().toString(),
                    sentAtMs = System.currentTimeMillis(),
                    intent = intent,
                    durationMs = 100,
                    reason = "dashboard_trigger"
                )
            )
        } catch (_: Exception) {
        }
    }
}
