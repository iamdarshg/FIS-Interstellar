package org.hound.dashboard

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.hound.dashboard.HttpControlServer.Companion.configureRouting
import org.hound.domain.VisionMode
import org.hound.domain.VisionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class HttpControlServerTest {

    @Test
    fun testDashboardGetEndpoints() = testApplication {
        val state = DashboardState()
        state.visionState.set(
            VisionState(
                timestampMs = 123456789L,
                mode = VisionMode.SEARCHING,
                confidence = 0.95f,
                targetBox = null,
                reason = "SEARCHING_ACTIVE"
            )
        )
        application {
            configureRouting(state)
        }

        val htmlResponse = client.get("/")
        assertEquals(HttpStatusCode.OK, htmlResponse.status)
        assertTrue(htmlResponse.bodyAsText().contains("HOUND Stationary Service"))

        val stateResponse = client.get("/state")
        assertEquals(HttpStatusCode.OK, stateResponse.status)
        val stateBody = stateResponse.bodyAsText()
        assertTrue(stateBody.contains("\"timestampMs\":123456789"))
        assertTrue(stateBody.contains("\"SEARCHING\""))

        val previewMissingResponse = client.get("/preview.jpg")
        assertEquals(HttpStatusCode.NotFound, previewMissingResponse.status)

        state.latestPreviewJpeg.set(byteArrayOf(1, 2, 3, 4))
        val previewPresentResponse = client.get("/preview.jpg")
        assertEquals(HttpStatusCode.OK, previewPresentResponse.status)
    }

    @Test
    fun testPostControlEndpoints() = testApplication {
        val state = DashboardState()
        val learnCalled = AtomicBoolean(false)
        val startCalled = AtomicBoolean(false)
        val stopCalled = AtomicBoolean(false)
        val resetCalled = AtomicBoolean(false)

        state.onLearnTriggered = { learnCalled.set(true) }
        state.onStartTriggered = { startCalled.set(true) }
        state.onStopTriggered = { stopCalled.set(true) }
        state.onResetTriggered = { resetCalled.set(true) }

        application {
            configureRouting(state)
        }

        assertEquals(HttpStatusCode.OK, client.post("/api/target/learn").status)
        assertTrue(learnCalled.get())

        assertEquals(HttpStatusCode.OK, client.post("/api/control/start").status)
        assertTrue(startCalled.get())

        assertEquals(HttpStatusCode.OK, client.post("/api/control/stop").status)
        assertTrue(stopCalled.get())

        assertEquals(HttpStatusCode.OK, client.post("/api/control/reset").status)
        assertTrue(resetCalled.get())
    }
}
