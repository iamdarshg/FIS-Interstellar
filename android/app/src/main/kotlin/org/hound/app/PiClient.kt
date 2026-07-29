package org.hound.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.hound.domain.CommandAck
import org.hound.domain.MotionIntent
import org.hound.domain.VisionState
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class PiClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 9090,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : AutoCloseable {

    private val isRunning = AtomicBoolean(false)
    private var connectionJob: Job? = null
    private var socket: Socket? = null
    private var printWriter: PrintWriter? = null

    private val _motionIntents = MutableSharedFlow<MotionIntent>(extraBufferCapacity = 64)
    val motionIntents: SharedFlow<MotionIntent> = _motionIntents.asSharedFlow()

    private val _commandAcks = MutableSharedFlow<CommandAck>(extraBufferCapacity = 64)
    val commandAcks: SharedFlow<CommandAck> = _commandAcks.asSharedFlow()

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            connectionJob = scope.launch {
                runLoop()
            }
        }
    }

    fun sendVisionState(state: VisionState): Boolean {
        val writer = printWriter
        if (writer == null || !isRunning.get()) return false

        return try {
            val jsonStr = Json.encodeToString(VisionState.serializer(), state)
            writer.println(jsonStr)
            writer.flush()
            !writer.checkError()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun runLoop() {
        while (isRunning.get()) {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), 2000)
                sock.soTimeout = 100
                socket = sock

                val writer = PrintWriter(OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true)
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), "UTF-8"))
                printWriter = writer

                while (isRunning.get() && sock.isConnected && !sock.isClosed) {
                    val line = try {
                        reader.readLine()
                    } catch (e: Exception) {
                        null
                    }

                    if (line == null) {
                        if (!isRunning.get()) break
                        delay(10)
                        continue
                    }

                    parseIncomingLine(line)
                }
            } catch (e: Exception) {
                // Socket drop / error
            } finally {
                cleanupSocket()
            }

            if (isRunning.get()) {
                delay(100)
            }
        }
    }

    private fun parseIncomingLine(line: String) {
        try {
            if (line.contains("\"type\":\"motion_intent\"") || line.contains("\"type\": \"motion_intent\"")) {
                val intent = Json.decodeFromString(MotionIntent.serializer(), line)
                _motionIntents.tryEmit(intent)
            } else if (line.contains("\"type\":\"command_ack\"") || line.contains("\"type\": \"command_ack\"")) {
                val ack = Json.decodeFromString(CommandAck.serializer(), line)
                _commandAcks.tryEmit(ack)
            }
        } catch (e: Exception) {
            // Ignore parse error
        }
    }

    private fun cleanupSocket() {
        printWriter = null
        try {
            socket?.close()
        } catch (e: Exception) { }
        socket = null
    }

    override fun close() {
        isRunning.set(false)
        connectionJob?.cancel()
        cleanupSocket()
    }
}
