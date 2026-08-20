package com.playeverywhere.spherelauncher.audio

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VoiceMatcherTest {

    private lateinit var voiceMatcher: VoiceMatcher

    @Before
    fun setUp() {
        voiceMatcher = VoiceMatcher()
    }

    @Test
    fun testEnrollAndGetPrints() {
        val print = VoiceMatcher.VoicePrint(
            wakeWordText = "Jarvis",
            appWordText = "Chrome",
            packageName = "com.android.chrome",
            durationMs = 1500L,
            rmsEnvelope = listOf(2f, 4f, 8f, 5f, 1f)
        )
        voiceMatcher.enrollVoicePrint(print)

        val prints = voiceMatcher.getEnrolledPrints()
        assertEquals(1, prints.size)
        assertEquals("Jarvis", prints[0].wakeWordText)
        assertEquals("com.android.chrome", prints[0].packageName)
    }

    @Test
    fun testMatchSuccess() {
        val print = VoiceMatcher.VoicePrint(
            wakeWordText = "старт",
            appWordText = "камера",
            packageName = "com.android.camera",
            durationMs = 1000L,
            rmsEnvelope = listOf(1f, 3f, 5f, 3f, 1f)
        )
        voiceMatcher.enrollVoicePrint(print)

        // Spoken phrase contains wake word and app word, duration close (1100ms), envelope similar
        val result = voiceMatcher.match(
            spokenText = "пожалуйста старт камера прямо сейчас",
            durationMs = 1100L,
            rmsEnvelope = listOf(1f, 3.2f, 4.8f, 3.1f, 1f)
        )

        assertNotNull(result)
        assertEquals("com.android.camera", result?.packageName)
    }

    @Test
    fun testMatchFailsWhenTextMismatch() {
        val print = VoiceMatcher.VoicePrint(
            wakeWordText = "open",
            appWordText = "settings",
            packageName = "com.android.settings",
            durationMs = 1200L,
            rmsEnvelope = listOf(3f, 6f, 3f)
        )
        voiceMatcher.enrollVoicePrint(print)

        // Spoken phrase misses the app word
        val result = voiceMatcher.match(
            spokenText = "open music player",
            durationMs = 1200L,
            rmsEnvelope = listOf(3f, 6f, 3f)
        )

        assertNull(result)
    }

    @Test
    fun testMatchFailsWhenDurationExceedsTolerance() {
        val print = VoiceMatcher.VoicePrint(
            wakeWordText = "launch",
            appWordText = "maps",
            packageName = "com.google.android.apps.maps",
            durationMs = 1000L,
            rmsEnvelope = listOf(2f, 5f, 2f)
        )
        voiceMatcher.enrollVoicePrint(print)

        // Duration is 2000ms, diff is 1000ms > 500ms (50% of 1000ms)
        val result = voiceMatcher.match(
            spokenText = "launch maps",
            durationMs = 2000L,
            rmsEnvelope = listOf(2f, 5f, 2f)
        )

        assertNull(result)
    }

    @Test
    fun testCalculateEnvelopeSimilarity() {
        val e1 = listOf(0f, 10f, 20f, 10f, 0f)
        val e2 = listOf(0f, 9f, 19f, 9.5f, 0f)

        val similarity = voiceMatcher.calculateEnvelopeSimilarity(e1, e2)
        assertTrue(similarity > 0.8f)
    }

    @Test
    fun testClearPrints() {
        voiceMatcher.enrollVoicePrint(
            VoiceMatcher.VoicePrint("hey", "app", "com.test", 1000L, emptyList())
        )
        assertEquals(1, voiceMatcher.getEnrolledPrints().size)

        voiceMatcher.clear()
        assertEquals(0, voiceMatcher.getEnrolledPrints().size)
    }
}
