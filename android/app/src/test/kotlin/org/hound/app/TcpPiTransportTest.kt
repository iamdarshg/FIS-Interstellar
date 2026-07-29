package org.hound.app

import kotlinx.coroutines.runBlocking
import org.hound.domain.CommandAck
import org.hound.domain.MotionIntent
import org.hound.domain.MotionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.util.UUID

class TcpPiTransportTest {

    @Volatile
    private var receivedWireLine: String? = null

    @Test
    fun testExactWireBytesAndSuccessfulAck() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val intentId = UUID.randomUUID().toString()
        val intent = MotionIntent(
            protocolVersion = 1,
            type = "motion_intent",
            id = intentId,
            sentAtMs = 1000L,
            intent = MotionKind.STOP,
            durationMs = 0,
            reason = "test_wire"
        )

        receivedWireLine = null

        val serverThread = Thread {
            try {
                val client = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val writer = PrintWriter(OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8), true)

                receivedWireLine = reader.readLine()
                val ackJson = """{"protocolVersion":1,"type":"command_ack","commandId":"$intentId","accepted":true,"reason":"ok"}"""
                writer.println(ackJson)
                writer.flush()
                Thread.sleep(100)
                client.close()
            } catch (_: Exception) {}
        }
        serverThread.start()
        Thread.sleep(50)

        val transport = TcpPiTransport(host = "127.0.0.1", port = port, connectTimeoutMs = 1000, readTimeoutMs = 1000)
        val ack = transport.send(intent)

        serverThread.join(1000)
        transport.close()
        serverSocket.close()

        val line = receivedWireLine
        assertTrue("Received wire line must not be null", line != null)
        assertTrue("Received wire line must contain intent id: $line", line?.contains(intentId) == true)
        assertTrue("Received wire line must contain motion_intent: $line", line?.contains("motion_intent") == true)
        assertTrue("Ack accepted must be true. Reason: ${ack.reason}", ack.accepted)
        assertEquals(intentId, ack.commandId)
    }

    @Test
    fun testFragmentedReply() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val intentId = UUID.randomUUID().toString()
        val intent = MotionIntent(
            protocolVersion = 1,
            type = "motion_intent",
            id = intentId,
            sentAtMs = 1000L,
            intent = MotionKind.ROTATE_LEFT,
            durationMs = 100,
            reason = "fragmented"
        )

        val serverThread = Thread {
            try {
                val client = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val out = client.getOutputStream()

                reader.readLine()
                val ackPart1 = """{"protocolVersion":1,"type":"command_ack","commandId":""""
                val ackPart2 = """$intentId","accepted":true,"reason":"ok"}""" + "\n"

                out.write(ackPart1.toByteArray(Charsets.UTF_8))
                out.flush()
                Thread.sleep(50)
                out.write(ackPart2.toByteArray(Charsets.UTF_8))
                out.flush()
                Thread.sleep(100)

                client.close()
            } catch (_: Exception) {}
        }
        serverThread.start()
        Thread.sleep(50)

        val transport = TcpPiTransport(host = "127.0.0.1", port = port, connectTimeoutMs = 1000, readTimeoutMs = 1000)
        val ack = transport.send(intent)

        serverThread.join(1000)
        transport.close()
        serverSocket.close()

        assertTrue("Fragmented reply must be reassembled. Reason: ${ack.reason}", ack.accepted)
    }

    @Test
    fun testTimeoutAndRetryWithSameId() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val intentId = UUID.randomUUID().toString()
        val intent = MotionIntent(
            protocolVersion = 1,
            type = "motion_intent",
            id = intentId,
            sentAtMs = 1000L,
            intent = MotionKind.DRIVE_FORWARD,
            durationMs = 200,
            reason = "retry_test"
        )

        val receivedIds = java.util.Collections.synchronizedList(mutableListOf<String>())

        val serverThread = Thread {
            try {
                // First connection times out
                val client1 = serverSocket.accept()
                val reader1 = BufferedReader(InputStreamReader(client1.getInputStream(), Charsets.UTF_8))
                val line1 = reader1.readLine()
                if (line1 != null) {
                    receivedIds.add(line1)
                }
                Thread.sleep(400) // Cause read timeout
                client1.close()

                // Second connection succeeds
                val client2 = serverSocket.accept()
                val reader2 = BufferedReader(InputStreamReader(client2.getInputStream(), Charsets.UTF_8))
                val writer2 = PrintWriter(OutputStreamWriter(client2.getOutputStream(), Charsets.UTF_8), true)
                val line2 = reader2.readLine()
                if (line2 != null) {
                    receivedIds.add(line2)
                }
                val ackJson = """{"protocolVersion":1,"type":"command_ack","commandId":"$intentId","accepted":true,"reason":"retry_ok"}"""
                writer2.println(ackJson)
                writer2.flush()
                Thread.sleep(100)
                client2.close()
            } catch (_: Exception) {}
        }
        serverThread.start()
        Thread.sleep(50)

        val transport = TcpPiTransport(host = "127.0.0.1", port = port, connectTimeoutMs = 250, readTimeoutMs = 250)
        val ack = transport.send(intent)

        serverThread.join(2000)
        transport.close()
        serverSocket.close()

        assertEquals(2, receivedIds.size)
        assertTrue("Retry used same ID", receivedIds[0].contains(intentId) && receivedIds[1].contains(intentId))
        assertTrue("Retry succeeded. Reason: ${ack.reason}", ack.accepted)
    }

    @Test
    fun testMalformedJsonAndCloseMidLine() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val intentId = UUID.randomUUID().toString()
        val intent = MotionIntent(
            protocolVersion = 1,
            type = "motion_intent",
            id = intentId,
            sentAtMs = 1000L,
            intent = MotionKind.ROTATE_RIGHT,
            durationMs = 150,
            reason = "malformed"
        )

        val serverThread = Thread {
            try {
                val client = serverSocket.accept()
                val out = client.getOutputStream()
                out.write("NOT_VALID_JSON\n".toByteArray(Charsets.UTF_8))
                out.flush()
                Thread.sleep(100)
                client.close()
            } catch (_: Exception) {}
        }
        serverThread.start()
        Thread.sleep(50)

        val transport = TcpPiTransport(host = "127.0.0.1", port = port, connectTimeoutMs = 250, readTimeoutMs = 250)
        val ack = transport.send(intent)

        serverThread.join(1000)
        transport.close()
        serverSocket.close()

        assertFalse("Malformed JSON response yields non-accepted Ack", ack.accepted)
    }
}
