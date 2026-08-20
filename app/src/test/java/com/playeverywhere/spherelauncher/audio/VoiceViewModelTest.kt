package com.playeverywhere.spherelauncher.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testVoiceViewModelInitialState() {
        val voiceMatcher = VoiceMatcher()
        val print = VoiceMatcher.VoicePrint(
            wakeWordText = "hey",
            appWordText = "app",
            packageName = "com.test.app",
            durationMs = 1000L,
            rmsEnvelope = listOf(1f, 2f, 1f)
        )
        voiceMatcher.enrollVoicePrint(print)

        val matched = voiceMatcher.match("hey app", 1000L, listOf(1f, 2f, 1f))
        assertNotNull(matched)
        assertEquals("com.test.app", matched?.packageName)
    }
}
