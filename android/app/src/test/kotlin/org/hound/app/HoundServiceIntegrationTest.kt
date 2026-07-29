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
class HoundServiceIntegrationTest {

    @Before
    fun setUp() {
        HoundService.cameraInitializer = null
    }

    @Test
    fun testFullServiceLifecycleStartStopReset() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)

        val controller = Robolectric.buildService(HoundService::class.java)
        val service = controller.create().get()

        val startIntent = Intent(service, HoundService::class.java).apply {
            action = HoundService.ACTION_START
        }
        service.onStartCommand(startIntent, 0, 1)

        val health = HoundService.healthState.value
        assertEquals(ServiceStatus.RUNNING, health.status)

        val resetIntent = Intent(service, HoundService::class.java).apply {
            action = HoundService.ACTION_RESET
        }
        service.onStartCommand(resetIntent, 0, 2)
        assertEquals(ServiceStatus.RUNNING, HoundService.healthState.value.status)

        val stopIntent = Intent(service, HoundService::class.java).apply {
            action = HoundService.ACTION_STOP
        }
        service.onStartCommand(stopIntent, 0, 3)
        assertEquals(ServiceStatus.STOPPED, HoundService.healthState.value.status)
    }
}
