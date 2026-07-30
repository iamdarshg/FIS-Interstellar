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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.hound.dashboard.DashboardState
import org.hound.dashboard.HttpControlServer
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
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var controlServer: HttpControlServer? = null
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
        controlServer?.stop()
        controlServer = null
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
            releaseWakeLock()
        } finally {
            super.onDestroy()
        }
    }
}
