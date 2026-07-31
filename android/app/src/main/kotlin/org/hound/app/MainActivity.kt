package org.hound.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.hound.domain.Tracker
import org.hound.domain.VisionMode
import org.hound.domain.VisionState
import org.hound.vision.CameraSource
import org.hound.vision.Frame
import org.hound.vision.LiteRtEmbeddingEncoder
import org.hound.vision.MlKitCandidateFinder
import org.hound.vision.VisionPipeline
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private companion object {
        const val REQUEST_CAMERA_PERMISSION = 100
    }

    private lateinit var statusTextView: TextView
    private var cameraSource: CameraSource? = null
    private var visionPipeline: VisionPipeline? = null
    private var tracker = Tracker()
    private var frameCounter = 0L
    private var lastFrameTimeMs = 0L
    private var currentFps = 25.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusTextView = TextView(this).apply {
            textSize = 18f
            setPadding(32, 32, 32, 32)
            text = "HOUND Starting Camera & Vision Pipeline..."
        }
        setContentView(statusTextView)

        initVisionPipeline()
        forceConnectHotspot()
        checkAndStartService()
        observeServiceHealth()
    }

    private fun initVisionPipeline() {
        try {
            val candidateFinder = MlKitCandidateFinder()
            val encoder = LiteRtEmbeddingEncoder.fromAssets(this)
            visionPipeline = VisionPipeline(candidateFinder, encoder, tracker)
        } catch (_: Exception) {
        }
    }

    private fun forceConnectHotspot() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            if (!wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = true
            }

            val targetSsid = "\"Darsh’s iPhone\""
            val targetPass = "\"pwd12345\""

            val wifiConfig = WifiConfiguration().apply {
                SSID = targetSsid
                preSharedKey = targetPass
            }

            val netId = wifiManager.addNetwork(wifiConfig)
            if (netId != -1) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
            }
        } catch (_: Exception) {
        }
    }

    private fun checkAndStartService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startHoundService()
            startCameraPreview()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startHoundService()
                startCameraPreview()
            } else {
                statusTextView.text = "Camera permission required for HOUND"
            }
        }
    }

    private fun startHoundService() {
        val intent = Intent(this, HoundService::class.java).apply {
            action = HoundService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startCameraPreview() {
        if (cameraSource == null) {
            cameraSource = CameraSource(this, this)
        }
        cameraSource?.start { frame ->
            frameCounter++
            val now = System.currentTimeMillis()
            if (lastFrameTimeMs > 0) {
                val delta = now - lastFrameTimeMs
                if (delta > 0) {
                    val instantFps = 1000.0f / delta
                    currentFps = 0.85f * currentFps + 0.15f * instantFps
                    HoundService.activeDashboardState?.processedFps?.set(currentFps)
                }
            }
            lastFrameTimeMs = now

            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    // Encode Live Camera JPEG Preview
                    val jpeg = frameToJpeg(frame)
                    if (jpeg.isNotEmpty()) {
                        HoundService.activeDashboardState?.latestPreviewJpeg?.set(jpeg)
                    }

                    // Process Vision Tracking Pipeline
                    val dashState = HoundService.activeDashboardState
                    val curVision = dashState?.visionState?.get()
                    val mode = curVision?.mode ?: VisionMode.SEARCHING

                    val pipeline = visionPipeline
                    if (pipeline != null) {
                        val result = pipeline.process(frame, mode)
                        val targetBox = result.bestObservation?.box
                            ?: if (result.candidates.isNotEmpty()) result.candidates[0].box else null

                        val updatedVision = VisionState(
                            timestampMs = frame.timestampMs,
                            mode = if (targetBox != null) VisionMode.TRACKED else mode,
                            confidence = result.bestObservation?.similarity ?: 0.85f,
                            targetBox = targetBox,
                            reason = curVision?.reason ?: "tracking_active"
                        )
                        dashState?.visionState?.set(updatedVision)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun frameToJpeg(frame: Frame): ByteArray {
        return try {
            val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(frame.width * frame.height)
            for (i in 0 until frame.width * frame.height) {
                val r = frame.rgbData[i * 3].toInt() and 0xFF
                val g = frame.rgbData[i * 3 + 1].toInt() and 0xFF
                val b = frame.rgbData[i * 3 + 2].toInt() and 0xFF
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            bitmap.setPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            bitmap.recycle()
            baos.toByteArray()
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    private fun observeServiceHealth() {
        lifecycleScope.launch {
            HoundService.healthState.collectLatest { health ->
                statusTextView.text = """
                    📷 HOUND Live Camera & Vision Active
                    Status: ${health.status}
                    Frames Captured: $frameCounter (${String.format("%.1f", currentFps)} FPS)
                    Message: ${health.message}
                    Untethered Dashboard URL: http://172.20.10.14:8080
                    Local ADB URL: http://127.0.0.1:8080
                """.trimIndent()
            }
        }
    }

    override fun onDestroy() {
        cameraSource?.stop()
        super.onDestroy()
    }
}
