package org.hound.app

import kotlinx.coroutines.flow.StateFlow
import org.hound.domain.CommandAck
import org.hound.domain.MotionIntent

enum class PiConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class PiHealth(
    val status: PiConnectionStatus = PiConnectionStatus.DISCONNECTED,
    val lastAckMs: Long = 0L,
    val lastError: String? = null
)

interface PiTransport : AutoCloseable {
    val health: StateFlow<PiHealth>
    suspend fun send(intent: MotionIntent): CommandAck
}
