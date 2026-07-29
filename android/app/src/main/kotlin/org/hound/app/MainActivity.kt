package org.hound.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private companion object {
        const val REQUEST_CAMERA_PERMISSION = 100
    }

    private lateinit var statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusTextView = TextView(this).apply {
            textSize = 18f
            setPadding(32, 32, 32, 32)
            text = "HOUND Starting..."
        }
        setContentView(statusTextView)

        checkAndStartService()
        observeServiceHealth()
    }

    private fun checkAndStartService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startHoundService()
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

    private fun observeServiceHealth() {
        lifecycleScope.launch {
            HoundService.healthState.collectLatest { health ->
                statusTextView.text = """
                    HOUND Headless Service
                    Status: ${health.status}
                    Error: ${health.errorCode}
                    Message: ${health.message}
                    Dashboard URL: http://0.0.0.0:8080
                """.trimIndent()
            }
        }
    }
}
