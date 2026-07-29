package org.hound.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.hound.domain.VisionMode
import org.hound.domain.VisionState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

class PiClientTest {

    @Test
    fun testPiClientSendAndReconnect() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val receivedCount = AtomicInteger(0)

        val serverThread = Thread {
            try {
                val client1 = serverSocket.accept()
                val reader1 = BufferedReader(InputStreamReader(client1.getInputStream()))
                for (i in 0 until 50) {
                    val line = reader1.readLine() ?: break
                    if (line.isNotEmpty()) receivedCount.incrementAndGet()
                }
                client1.close()

                val client2 = serverSocket.accept()
                val reader2 = BufferedReader(InputStreamReader(client2.getInputStream()))
                for (i in 0 until 50) {
                    val line = reader2.readLine() ?: break
                    if (line.isNotEmpty()) receivedCount.incrementAndGet()
                }
                client2.close()
            } catch (e: Exception) {
            }
        }
        serverThread.start()

        val client = PiClient(host = "127.0.0.1", port = port, scope = CoroutineScope(Dispatchers.IO))
        client.start()

        var sent1 = 0
        val startTime = System.currentTimeMillis()
        while (sent1 < 50 && System.currentTimeMillis() - startTime < 3000) {
            val state = VisionState(timestampMs = 1000L + sent1, mode = VisionMode.SEARCHING, confidence = 0.5f, reason = "TEST")
            if (client.sendVisionState(state)) {
                sent1++
            }
            Thread.sleep(10)
        }

        Thread.sleep(300)

        var sent2 = 0
        val startTime2 = System.currentTimeMillis()
        while (sent2 < 50 && System.currentTimeMillis() - startTime2 < 3000) {
            val state = VisionState(timestampMs = 2000L + sent2, mode = VisionMode.TRACKED, confidence = 0.9f, reason = "TEST")
            if (client.sendVisionState(state)) {
                sent2++
            }
            Thread.sleep(10)
        }

        client.close()
        serverSocket.close()
        serverThread.join(1000)

        assertTrue("Should receive at least 50 messages, got ${receivedCount.get()}", receivedCount.get() >= 50)
    }
}
