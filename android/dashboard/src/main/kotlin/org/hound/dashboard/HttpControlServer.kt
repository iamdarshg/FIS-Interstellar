package org.hound.dashboard

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hound.domain.BoundingBox
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class HttpControlServer(
    private val dashboardState: DashboardState,
    private val port: Int = 8080
) {

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private val threadPool = Executors.newCachedThreadPool()

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            serverThread = Thread {
                try {
                    val ss = ServerSocket().apply {
                        reuseAddress = true
                        bind(java.net.InetSocketAddress(port))
                    }
                    serverSocket = ss
                    while (isRunning.get()) {
                        try {
                            val client = ss.accept()
                            client.tcpNoDelay = true
                            client.soTimeout = 1500
                            threadPool.execute { handleClient(client) }
                        } catch (e: Exception) {
                            if (!isRunning.get()) break
                        }
                    }
                } catch (e: Exception) {
                }
            }.apply {
                isDaemon = true
                priority = Thread.MAX_PRIORITY
                start()
            }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), "UTF-8"))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            // Read remaining HTTP request headers without blocking
            while (reader.ready()) {
                val line = reader.readLine()
                if (line.isNullOrEmpty()) break
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
                    val bytesToSend = if (jpeg != null && jpeg.isNotEmpty()) {
                        jpeg
                    } else {
                        getFallbackJpegStream()
                    }
                    sendResponse(out, "200 OK", "image/jpeg", bytesToSend)
                }
                method == "POST" && path == "/api/target/learn" -> {
                    val label = parseQueryParam(queryString, "label") ?: "target_${System.currentTimeMillis() % 1000}"
                    val targetType = (parseQueryParam(queryString, "type") ?: "GOAL").uppercase()
                    val decodedLabel = URLDecoder.decode(label, "UTF-8").trim()
                    if (decodedLabel.isNotEmpty()) {
                        if (!dashboardState.learnedTargets.contains(decodedLabel)) {
                            dashboardState.learnedTargets.add(decodedLabel)
                        }
                        dashboardState.targetTypes[decodedLabel] = if (targetType == "HAZARD") "HAZARD" else "GOAL"
                    }
                    dashboardState.onLearnTriggeredWithLabelAndType?.invoke(decodedLabel, targetType)
                    dashboardState.onLearnTriggeredWithLabel?.invoke(decodedLabel)
                    dashboardState.onLearnTriggered?.invoke()

                    val typesJson = Json.encodeToString(dashboardState.targetTypes.toMap())
                    val resp = """{"status":"ok","action":"learn","label":"$decodedLabel","type":"$targetType","learnedTargets":${Json.encodeToString(dashboardState.learnedTargets.toList())},"targetTypes":$typesJson}""".toByteArray(Charsets.UTF_8)
                    sendResponse(out, "200 OK", "application/json; charset=utf-8", resp)
                }
                method == "POST" && path == "/api/target/toggle_type" -> {
                    val label = parseQueryParam(queryString, "label") ?: ""
                    val decodedLabel = URLDecoder.decode(label, "UTF-8").trim()
                    var newType = "GOAL"
                    if (decodedLabel.isNotEmpty() && dashboardState.targetTypes.containsKey(decodedLabel)) {
                        val cur = dashboardState.targetTypes[decodedLabel] ?: "GOAL"
                        newType = if (cur == "GOAL") "HAZARD" else "GOAL"
                        dashboardState.targetTypes[decodedLabel] = newType
                        dashboardState.onToggleTargetType?.invoke(decodedLabel)
                    }

                    val typesJson = Json.encodeToString(dashboardState.targetTypes.toMap())
                    val resp = """{"status":"ok","action":"toggle_type","label":"$decodedLabel","newType":"$newType","targetTypes":$typesJson}""".toByteArray(Charsets.UTF_8)
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
                method == "POST" && path == "/api/control/mode" -> {
                    val missionParam = parseQueryParam(queryString, "mission") ?: "FINDING"
                    val mode = if (missionParam.uppercase() == "AVOIDANCE") org.hound.domain.MissionMode.OBJECT_AVOIDANCE else org.hound.domain.MissionMode.OBJECT_FINDING
                    dashboardState.missionMode.set(mode)
                    dashboardState.onMissionModeChanged?.invoke(mode)

                    val resp = """{"status":"ok","missionMode":"${mode.name}"}""".toByteArray(Charsets.UTF_8)
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
    }

    private fun getSystemTelemetryJson(): String {
        val runtime = Runtime.getRuntime()
        val totalMb = runtime.totalMemory() / (1024 * 1024)
        val freeMb = runtime.freeMemory() / (1024 * 1024)
        val usedMb = totalMb - freeMb
        val maxMb = runtime.maxMemory() / (1024 * 1024)
        val cores = runtime.availableProcessors()
        val fps = dashboardState.processedFps.get()

        return """{"cpuCores":$cores,"ramUsedMb":$usedMb,"ramTotalMb":$totalMb,"ramMaxMb":$maxMb,"processedFps":$fps}"""
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
        val targets = state.learnedTargets
        val objects = mutableListOf<Object2D>()

        if (vision != null && vision.mode != VisionMode.IDLE && vision.targetBox != null) {
            val box = vision.targetBox!!
            val relX = ((box.xMin + box.xMax) / 2.0f - 0.5f) * 2.0f // Scale [-1, 1]
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

    private fun getFallbackJpegStream(): ByteArray {
        return try {
            val width = 320
            val height = 240
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.rgb(3, 7, 18))
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(168, 85, 247)
                textSize = 18f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("📹 HOUND Live Stream Connected", (width / 2).toFloat(), (height / 2).toFloat(), paint)
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
            bitmap.recycle()
            baos.toByteArray()
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            threadPool.shutdown()
            serverSocket?.close()
        } catch (e: Exception) { }
        serverSocket = null
    }
}
