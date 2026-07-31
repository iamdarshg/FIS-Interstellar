package org.hound.dashboard

import kotlinx.serialization.json.Json
import org.hound.domain.MapState
import org.hound.domain.Object2D
import org.hound.domain.VisionMode
import org.hound.domain.VisionState
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
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
                client.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), "UTF-8"))
                val requestLine = reader.readLine() ?: return@Thread
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@Thread

                // Read remaining HTTP request headers
                var headerLine = reader.readLine()
                while (!headerLine.isNullOrEmpty()) {
                    headerLine = reader.readLine()
                }

                val method = parts[0]
                val fullPath = parts[1]
                val path = fullPath.substringBefore("?")
                val queryString = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""
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
                    method == "GET" && path == "/map" -> {
                        val currentMap = dashboardState.mapState.get() ?: createDefaultMapState(dashboardState)
                        val jsonStr = Json.encodeToString(MapState.serializer(), currentMap)
                        sendResponse(out, "200 OK", "application/json; charset=utf-8", jsonStr.toByteArray(Charsets.UTF_8))
                    }
                    method == "GET" && path == "/telemetry" -> {
                        val telemetry = getSystemTelemetryJson()
                        dashboardState.lastMetrics.set(telemetry)
                        sendResponse(out, "200 OK", "application/json; charset=utf-8", telemetry.toByteArray(Charsets.UTF_8))
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
                        val label = parseQueryParam(queryString, "label") ?: "target_${System.currentTimeMillis() % 1000}"
                        val decodedLabel = URLDecoder.decode(label, "UTF-8").trim()
                        if (decodedLabel.isNotEmpty() && !dashboardState.learnedTargets.contains(decodedLabel)) {
                            dashboardState.learnedTargets.add(decodedLabel)
                        }
                        dashboardState.onLearnTriggeredWithLabel?.invoke(decodedLabel)
                        dashboardState.onLearnTriggered?.invoke()

                        val resp = """{"status":"ok","action":"learn","label":"$decodedLabel","learnedTargets":${Json.encodeToString(dashboardState.learnedTargets.toList())}}""".toByteArray(Charsets.UTF_8)
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

    private fun getSystemTelemetryJson(): String {
        val runtime = Runtime.getRuntime()
        val totalMb = runtime.totalMemory() / (1024 * 1024)
        val freeMb = runtime.freeMemory() / (1024 * 1024)
        val usedMb = totalMb - freeMb
        val maxMb = runtime.maxMemory() / (1024 * 1024)
        val cores = runtime.availableProcessors()

        return """{"cpuCores":$cores,"ramUsedMb":$usedMb,"ramTotalMb":$totalMb,"ramMaxMb":$maxMb}"""
    }

    private fun parseQueryParam(query: String, key: String): String? {
        if (query.isEmpty()) return null
        return query.split("&")
            .map { it.split("=") }
            .firstOrNull { it.size == 2 && it[0] == key }
            ?.get(1)
    }

    private fun createDefaultMapState(state: DashboardState): MapState {
        val vision = state.visionState.get()
        val objects = mutableListOf<Object2D>()
        val targets = state.learnedTargets

        if (vision != null && vision.targetBox != null) {
            val box = vision.targetBox!!
            val centerX = (box.xMin + box.xMax) / 2.0f - 0.5f
            val relX = centerX * 2.0f
            val relY = 1.2f // Estimated 1.2m ahead
            val dist = Math.sqrt((relX * relX + relY * relY).toDouble()).toFloat()
            val angle = (Math.atan2(relX.toDouble(), relY.toDouble()) * 180.0 / Math.PI).toFloat()
            val primaryLabel = if (targets.isNotEmpty()) targets.last() else "tracked_target"

            objects.add(
                Object2D(
                    id = "target_1",
                    label = primaryLabel,
                    x = relX,
                    y = relY,
                    confidence = vision.confidence,
                    distance = dist,
                    angle = angle,
                    lastSeenMs = vision.timestampMs
                )
            )
        }

        return MapState(
            protocolVersion = 1,
            type = "map_state",
            timestampMs = System.currentTimeMillis(),
            roverX = 0.0f,
            roverY = 0.0f,
            roverHeading = 0.0f,
            objects = objects
        )
    }

    private fun sendResponse(out: OutputStream, status: String, contentType: String, body: ByteArray) {
        val header = "HTTP/1.1 $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
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
