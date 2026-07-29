package org.hound.vision

import java.util.concurrent.atomic.AtomicBoolean

class Frame(
    val width: Int,
    val height: Int,
    val rgbData: ByteArray,
    val timestampMs: Long,
    private val onClose: () -> Unit
) : AutoCloseable {

    private val closed = AtomicBoolean(false)

    val isClosed: Boolean
        get() = closed.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            onClose()
        }
    }
}
