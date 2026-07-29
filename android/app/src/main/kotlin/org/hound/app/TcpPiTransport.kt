package org.hound.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.hound.domain.CommandAck
import org.hound.domain.MotionIntent
import org.hound.domain.MotionKind
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID

class TcpPiTransport(
    private val host: String = "127.0.0.1",
    private val port: Int = 8765,
    private val connectTimeoutMs: Int = 250,
    private val readTimeoutMs: Int = 250
) : PiTransport {

    private val _health = MutableStateFlow(PiHealth())
    override val health: StateFlow<PiHealth> = _health.asStateFlow()

    private val sendMutex = Mutex()
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
    }

    private fun connectIfNeeded() {
        if (socket?.isConnected == true && socket?.isClosed == false) {
            return
        }

        closeSocket()
        _health.value = _health.value.copy(status = PiConnectionStatus.CONNECTING)
        try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), connectTimeoutMs)
            s.soTimeout = readTimeoutMs
            socket = s
            writer = PrintWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8), true)
            reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            _health.value = _health.value.copy(
                status = PiConnectionStatus.CONNECTED,
                lastError = null
            )
        } catch (e: Exception) {
            _health.value = _health.value.copy(
                status = PiConnectionStatus.ERROR,
                lastError = e.message ?: "Connect failed"
            )
            closeSocket()
            throw e
        }
    }

    private fun sendStopMandatory() {
        try {
            val stopIntent = MotionIntent(
                protocolVersion = 1,
                type = "motion_intent",
                id = UUID.randomUUID().toString(),
                sentAtMs = System.currentTimeMillis(),
                intent = MotionKind.STOP,
                durationMs = 0,
                reason = "mandatory_disconnect_stop"
            )
            val line = json.encodeToString(MotionIntent.serializer(), stopIntent)
            writer?.println(line)
            writer?.flush()
        } catch (_: Exception) {
        }
    }

    override suspend fun send(intent: MotionIntent): CommandAck = withContext(Dispatchers.IO) {
        sendMutex.withLock {
            var attempt = 0
            var lastException: Exception? = null

            while (attempt < 2) {
                attempt++
                try {
                    connectIfNeeded()

                    val w = writer ?: throw IllegalStateException("Writer is null")
                    val r = reader ?: throw IllegalStateException("Reader is null")

                    val lineToSend = json.encodeToString(MotionIntent.serializer(), intent)
                    w.println(lineToSend)
                    w.flush()

                    if (w.checkError()) {
                        throw IllegalStateException("Socket write error")
                    }

                    val respLine = r.readLine() ?: throw IllegalStateException("EOF from server")
                    val ack = json.decodeFromString(CommandAck.serializer(), respLine)

                    _health.value = _health.value.copy(
                        status = PiConnectionStatus.CONNECTED,
                        lastAckMs = System.currentTimeMillis(),
                        lastError = null
                    )
                    return@withLock ack
                } catch (e: Exception) {
                    lastException = e
                    _health.value = _health.value.copy(
                        status = PiConnectionStatus.ERROR,
                        lastError = e.message ?: "Transport error"
                    )
                    sendStopMandatory()
                    closeSocket()
                }
            }

            CommandAck(
                protocolVersion = 1,
                type = "command_ack",
                commandId = intent.id,
                accepted = false,
                reason = "Transport failed: ${lastException?.message}"
            )
        }
    }

    private fun closeSocket() {
        try {
            writer?.close()
        } catch (_: Exception) {}
        try {
            reader?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        writer = null
        reader = null
        socket = null
    }

    override fun close() {
        sendStopMandatory()
        closeSocket()
        _health.value = _health.value.copy(status = PiConnectionStatus.DISCONNECTED)
    }
}
