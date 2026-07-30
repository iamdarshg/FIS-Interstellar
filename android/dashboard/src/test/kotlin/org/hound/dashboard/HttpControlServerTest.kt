package org.hound.dashboard

import org.hound.domain.VisionMode
import org.hound.domain.VisionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class HttpControlServerTest {

    private fun getFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }

    @Test
    fun testDashboardGetEndpoints() {
        val port = getFreePort()
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

        val server = HttpControlServer(state, port = port)
        server.start()
        Thread.sleep(100)

        try {
            val htmlConn = URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection
            assertEquals(200, htmlConn.responseCode)
            val htmlText = htmlConn.inputStream.bufferedReader().readText()
            assertTrue(htmlText.contains("HOUND Stationary Service"))

            val stateConn = URL("http://127.0.0.1:$port/state").openConnection() as HttpURLConnection
            assertEquals(200, stateConn.responseCode)
            val stateBody = stateConn.inputStream.bufferedReader().readText()
            assertTrue(stateBody.contains("\"timestampMs\":123456789"))
            assertTrue(stateBody.contains("\"SEARCHING\""))

            val missingPreviewConn = URL("http://127.0.0.1:$port/preview.jpg").openConnection() as HttpURLConnection
            assertEquals(404, missingPreviewConn.responseCode)

            state.latestPreviewJpeg.set(byteArrayOf(1, 2, 3, 4))
            val presentPreviewConn = URL("http://127.0.0.1:$port/preview.jpg").openConnection() as HttpURLConnection
            assertEquals(200, presentPreviewConn.responseCode)
            val bytes = presentPreviewConn.inputStream.readBytes()
            assertEquals(4, bytes.size)
        } finally {
            server.stop()
        }
    }

    @Test
    fun testPostControlEndpoints() {
        val port = getFreePort()
        val state = DashboardState()
        val learnCalled = AtomicBoolean(false)
        val startCalled = AtomicBoolean(false)
        val stopCalled = AtomicBoolean(false)
        val resetCalled = AtomicBoolean(false)

        state.onLearnTriggered = { learnCalled.set(true) }
        state.onStartTriggered = { startCalled.set(true) }
        state.onStopTriggered = { stopCalled.set(true) }
        state.onResetTriggered = { resetCalled.set(true) }

        val server = HttpControlServer(state, port = port)
        server.start()
        Thread.sleep(100)

        try {
            fun postPath(path: String): Int {
                val conn = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                return conn.responseCode
            }

            assertEquals(200, postPath("/api/target/learn"))
            assertTrue(learnCalled.get())

            assertEquals(200, postPath("/api/control/start"))
            assertTrue(startCalled.get())

            assertEquals(200, postPath("/api/control/stop"))
            assertTrue(stopCalled.get())

            assertEquals(200, postPath("/api/control/reset"))
            assertTrue(resetCalled.get())
        } finally {
            server.stop()
        }
    }
}
