package org.hound.app

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HoundServiceStateTest {

    @Before
    fun setUp() {
        HoundService.cameraInitializer = null
    }

    @Test
    fun testDeniedCameraPermissionYieldsError() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).denyPermissions(Manifest.permission.CAMERA)

        val controller = Robolectric.buildService(HoundService::class.java)
        val service = controller.get()

        val intent = Intent(service, HoundService::class.java).apply {
            action = HoundService.ACTION_START
        }
        service.onStartCommand(intent, 0, 1)

        val health = HoundService.healthState.value
        assertEquals(ServiceStatus.ERROR, health.status)
        assertEquals(ServiceErrorCode.CAMERA_PERMISSION, health.errorCode)
    }

    @Test
    fun testStartIsIdempotentWhenRunning() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)

        val controller = Robolectric.buildService(HoundService::class.java)
        val service = controller.create().get()

        val startIntent = Intent(service, HoundService::class.java).apply {
            action = HoundService.ACTION_START
        }

        service.onStartCommand(startIntent, 0, 1)
        val initialHealth = HoundService.healthState.value
        assertEquals(ServiceStatus.RUNNING, initialHealth.status)

        service.onStartCommand(startIntent, 0, 2)
        val repeatHealth = HoundService.healthState.value
        assertEquals(ServiceStatus.RUNNING, repeatHealth.status)
    }

    @Test
    fun testCameraInitFailureYieldsCameraInitError() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)

        HoundService.cameraInitializer = {
            throw RuntimeException("Simulated camera failure")
        }

        val controller = Robolectric.buildService(HoundService::class.java)
        val service = controller.create().get()

        val startIntent = Intent(service, HoundService::class.java).apply {
            action = HoundService.ACTION_START
        }
        service.onStartCommand(startIntent, 0, 1)

        val health = HoundService.healthState.value
        assertEquals(ServiceStatus.ERROR, health.status)
        assertEquals(ServiceErrorCode.CAMERA_INIT, health.errorCode)
    }

    @Test
    fun testStopReleasesServiceAndSetsStatusStopped() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)

        val controller = Robolectric.buildService(HoundService::class.java)
        val service = controller.create().get()

        val startIntent = Intent(service, HoundService::class.java).apply {
            action = HoundService.ACTION_START
        }
        service.onStartCommand(startIntent, 0, 1)
        assertEquals(ServiceStatus.RUNNING, HoundService.healthState.value.status)

        val stopIntent = Intent(service, HoundService::class.java).apply {
            action = HoundService.ACTION_STOP
        }
        service.onStartCommand(stopIntent, 0, 2)
        assertEquals(ServiceStatus.STOPPED, HoundService.healthState.value.status)
    }
}
