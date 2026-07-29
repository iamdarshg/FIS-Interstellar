package org.hound.dashboard

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.hound.domain.VisionMode
import org.hound.domain.VisionState

class HttpControlServer(
    val dashboardState: DashboardState,
    private val port: Int = 8080
) {

    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        if (server != null) return
        server = embeddedServer(CIO, port = port) {
            configureRouting(dashboardState)
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    companion object {
        fun Application.configureRouting(state: DashboardState) {
            routing {
                get("/") {
                    val html = HtmlDashboardRenderer.render(state)
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/state") {
                    val currentState = state.visionState.get() ?: VisionState(
                        timestampMs = System.currentTimeMillis(),
                        mode = VisionMode.IDLE,
                        confidence = 0.0f,
                        targetBox = null,
                        reason = "INITIALIZING"
                    )
                    val jsonStr = Json.encodeToString(VisionState.serializer(), currentState)
                    call.respondText(jsonStr, ContentType.Application.Json)
                }

                get("/preview.jpg") {
                    val jpeg = state.latestPreviewJpeg.get()
                    if (jpeg != null && jpeg.isNotEmpty()) {
                        call.respondBytes(jpeg, ContentType.Image.JPEG)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "No preview available")
                    }
                }

                post("/api/target/learn") {
                    state.onLearnTriggered?.invoke()
                    call.respondText("""{"status":"ok","action":"learn"}""", ContentType.Application.Json)
                }

                post("/api/control/start") {
                    state.onStartTriggered?.invoke()
                    call.respondText("""{"status":"ok","action":"start"}""", ContentType.Application.Json)
                }

                post("/api/control/stop") {
                    state.onStopTriggered?.invoke()
                    call.respondText("""{"status":"ok","action":"stop"}""", ContentType.Application.Json)
                }

                post("/api/control/reset") {
                    state.onResetTriggered?.invoke()
                    call.respondText("""{"status":"ok","action":"reset"}""", ContentType.Application.Json)
                }
            }
        }
    }
}
