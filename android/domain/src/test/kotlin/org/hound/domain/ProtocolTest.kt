package org.hound.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class ProtocolTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    private val fixtureDir = File("../../protocol/fixtures")

    @Test
    fun `valid motion stop fixture parses and serializes identically`() {
        val file = File(fixtureDir, "motion-stop.json")
        assertTrue(file.exists(), "Fixture motion-stop.json should exist")
        val content = file.readText()
        val parsed = json.decodeFromString<MotionIntent>(content)

        assertEquals(1, parsed.protocolVersion)
        assertEquals("motion_intent", parsed.type)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", parsed.id)
        assertEquals(MotionKind.STOP, parsed.intent)
        assertEquals(0, parsed.durationMs)

        val reSerialized = json.encodeToString(MotionIntent.serializer(), parsed)
        val parsedAgain = json.decodeFromString<MotionIntent>(reSerialized)
        assertEquals(parsed, parsedAgain)
    }

    @Test
    fun `invalid version 2 fixture fails`() {
        val file = File(fixtureDir, "invalid-version-2.json")
        val content = file.readText()
        assertThrows<Exception> {
            json.decodeFromString<MotionIntent>(content)
        }
    }

    @Test
    fun `invalid unknown key fixture fails`() {
        val file = File(fixtureDir, "invalid-unknown-key.json")
        val content = file.readText()
        assertThrows<Exception> {
            json.decodeFromString<MotionIntent>(content)
        }
    }

    @Test
    fun `invalid negative duration fixture fails`() {
        val file = File(fixtureDir, "invalid-negative-duration.json")
        val content = file.readText()
        assertThrows<Exception> {
            json.decodeFromString<MotionIntent>(content)
        }
    }

    @Test
    fun `invalid missing id fixture fails`() {
        val file = File(fixtureDir, "invalid-missing-id.json")
        val content = file.readText()
        assertThrows<Exception> {
            json.decodeFromString<MotionIntent>(content)
        }
    }

    @Test
    fun `invalid nan coord fixture fails`() {
        val file = File(fixtureDir, "invalid-nan-coord.json")
        val content = file.readText()
        assertThrows<Exception> {
            json.decodeFromString<MotionIntent>(content)
        }
    }

    @Test
    fun `kotest property test bounding box outside range throws`() = runBlocking {
        checkAll(1000, Arb.float()) { f ->
            if (f < 0.0f || f > 1.0f || !f.isFinite()) {
                assertThrows<IllegalArgumentException> {
                    BoundingBox(f, 0.1f, 0.9f, 0.9f)
                }
                assertThrows<IllegalArgumentException> {
                    BoundingBox(0.1f, f, 0.9f, 0.9f)
                }
            }
        }
    }
}
