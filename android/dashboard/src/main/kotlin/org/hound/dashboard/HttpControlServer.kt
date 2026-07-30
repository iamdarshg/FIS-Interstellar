package org.hound.dashboard

import kotlinx.serialization.json.Json
import org.hound.domain.VisionMode
import org.hound.domain.VisionState
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class HttpControlServer(
    val dashboardState: DashboardState,
    private val port: Int = 8080
) {
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            serverThread = Thread {
                try {
                    val ss = ServerSocket(port)
                    serverSocket = ss
                    while (isRunning.get()) {
                        try {
                            val client = ss.accept()
                            handleClient(client)
                        } catch (e: Exception) {
                            if (!isRunning.get()) break
                        }
                    }
                } catch (e: Exception) {
                }
            }.apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun handleClient(client: Socket) {
        Thread {
            try {
                client.soTimeout = 3000
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), "UTF-8"))
                val requestLine = reader.readLine() ?: return@Thread
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@Thread

                val method = parts[0]
                val path = parts[1]
                val out = client.getOutputStream()

                when {
                    method == "GET" && path == "/" -> {
                        val html = HtmlDashboardRenderer.render(dashboardState)
                        sendResponse(out, "200 OK", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
                    }
                    method == "GET" && path == "/state" -> {
                        val currentState = dashboardState.visionState.get() ?: VisionState(
                            timestampMs = System.currentTimeMillis(),
                            mode = VisionMode.IDLE,
                            confidence = 0.0f,
                            targetBox = null,
                            reason = "INITIALIZING"
                        )
                        val jsonStr = Json.encodeToString(VisionState.serializer(), currentState)
                        sendResponse(out, "200 OK", "application/json; charset=utf-8", jsonStr.toByteArray(Charsets.UTF_8))
                    }
                    method == "GET" && path == "/preview.jpg" -> {
                        val jpeg = dashboardState.latestPreviewJpeg.get()
                        if (jpeg != null && jpeg.isNotEmpty()) {
                            sendResponse(out, "200 OK", "image/jpeg", jpeg)
                        } else {
                            val err = "No preview available".toByteArray(Charsets.UTF_8)
                            sendResponse(out, "404 Not Found", "text/plain; charset=utf-8", err)
                        }
                    }
                    method == "POST" && path == "/api/target/learn" -> {
                        dashboardState.onLearnTriggered?.invoke()
                        val resp = """{"status":"ok","action":"learn"}""".toByteArray(Charsets.UTF_8)
                        sendResponse(out, "200 OK", "application/json; charset=utf-8", resp)
                    }
                    method == "POST" && path == "/api/control/start" -> {
                        dashboardState.onStartTriggered?.invoke()
                        val resp = """{"status":"ok","action":"start"}""".toByteArray(Charsets.UTF_8)
                        sendResponse(out, "200 OK", "application/json; charset=utf-8", resp)
                    }
                    method == "POST" && path == "/api/control/stop" -> {
                        dashboardState.onStopTriggered?.invoke()
                        val resp = """{"status":"ok","action":"stop"}""".toByteArray(Charsets.UTF_8)
                        sendResponse(out, "200 OK", "application/json; charset=utf-8", resp)
                    }
                    method == "POST" && path == "/api/control/reset" -> {
                        dashboardState.onResetTriggered?.invoke()
                        val resp = """{"status":"ok","action":"reset"}""".toByteArray(Charsets.UTF_8)
                        sendResponse(out, "200 OK", "application/json; charset=utf-8", resp)
                    }
                    else -> {
                        val err = "404 Not Found".toByteArray(Charsets.UTF_8)
                        sendResponse(out, "404 Not Found", "text/plain; charset=utf-8", err)
                    }
                }
            } catch (e: Exception) {
            } finally {
                try { client.close() } catch (e: Exception) {}
            }
        }.start()
    }

    private fun sendResponse(out: OutputStream, status: String, contentType: String, body: ByteArray) {
        val header = "HTTP/1.1 $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) { }
        serverSocket = null
    }
}
