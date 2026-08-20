package com.playeverywhere.spherelauncher.audio

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class VoicePrintStoreTest {

    @Test
    fun testVoicePrintSerializationAndDeserialization() {
        val originalPrints = listOf(
            VoiceMatcher.VoicePrint(
                wakeWordText = "компьютер",
                appWordText = "браузер",
                packageName = "com.android.chrome",
                durationMs = 1450L,
                rmsEnvelope = listOf(1.5f, 3.2f, 7.8f, 4.1f, 0.9f)
            ),
            VoiceMatcher.VoicePrint(
                wakeWordText = "старт",
                appWordText = "музыка",
                packageName = "com.google.android.music",
                durationMs = 980L,
                rmsEnvelope = listOf(0.5f, 2.2f, 5.0f, 1.1f)
            )
        )

        val json = Json.encodeToString(originalPrints)
        val decodedPrints = Json.decodeFromString<List<VoiceMatcher.VoicePrint>>(json)

        assertEquals(2, decodedPrints.size)
        assertEquals("компьютер", decodedPrints[0].wakeWordText)
        assertEquals("браузер", decodedPrints[0].appWordText)
        assertEquals("com.android.chrome", decodedPrints[0].packageName)
        assertEquals(1450L, decodedPrints[0].durationMs)
        assertEquals(5, decodedPrints[0].rmsEnvelope.size)
        assertEquals(7.8f, decodedPrints[0].rmsEnvelope[2], 0.001f)

        assertEquals("com.google.android.music", decodedPrints[1].packageName)
    }

    @Test
    fun testEmptyJsonDeserialization() {
        val decoded = try {
            Json.decodeFromString<List<VoiceMatcher.VoicePrint>>("[]")
        } catch (e: Exception) {
            null
        }
        assertEquals(emptyList<VoiceMatcher.VoicePrint>(), decoded)
    }
}
