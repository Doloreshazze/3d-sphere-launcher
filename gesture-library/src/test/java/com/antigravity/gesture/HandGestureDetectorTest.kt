package com.antigravity.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HandGestureDetectorTest {

    private lateinit var detector: HandGestureDetector

    @Before
    fun setUp() {
        // Create detector instance; Android stubs allow setup without crashing
        detector = HandGestureDetector(MockContext())
    }

    // Helper to generate a default hand landmark set with all extended fingers
    private fun createBaseHand(
        thumbTipX: Float = 0.2f, thumbTipY: Float = 0.4f,
        indexTipX: Float = 0.3f, indexTipY: Float = 0.1f
    ): List<HandLandmarkData> {
        val landmarks = mutableListOf<HandLandmarkData>()
        
        // Wrist (index 0)
        landmarks.add(HandLandmarkData(0.5f, 0.9f, 0f, 0))
        
        // Thumb (1-4)
        landmarks.add(HandLandmarkData(0.4f, 0.7f, -0.05f, 1))
        landmarks.add(HandLandmarkData(0.3f, 0.6f, -0.08f, 2))
        landmarks.add(HandLandmarkData(0.2f, 0.5f, -0.1f, 3))
        landmarks.add(HandLandmarkData(thumbTipX, thumbTipY, -0.12f, 4)) // Tip
        
        // Index (5-8)
        landmarks.add(HandLandmarkData(0.4f, 0.5f, -0.05f, 5)) // MCP
        landmarks.add(HandLandmarkData(0.38f, 0.35f, -0.08f, 6))
        landmarks.add(HandLandmarkData(0.35f, 0.2f, -0.1f, 7))
        landmarks.add(HandLandmarkData(indexTipX, indexTipY, -0.12f, 8)) // Tip
        
        // Middle (9-12) - Extended
        landmarks.add(HandLandmarkData(0.5f, 0.5f, -0.05f, 9)) // MCP
        landmarks.add(HandLandmarkData(0.5f, 0.35f, -0.08f, 10))
        landmarks.add(HandLandmarkData(0.5f, 0.2f, -0.1f, 11))
        landmarks.add(HandLandmarkData(0.5f, 0.08f, -0.12f, 12)) // Tip
        
        // Ring (13-16) - Extended
        landmarks.add(HandLandmarkData(0.6f, 0.52f, -0.05f, 13)) // MCP
        landmarks.add(HandLandmarkData(0.62f, 0.38f, -0.08f, 14))
        landmarks.add(HandLandmarkData(0.65f, 0.24f, -0.1f, 15))
        landmarks.add(HandLandmarkData(0.66f, 0.1f, -0.12f, 16)) // Tip
        
        // Pinky (17-20) - Extended
        landmarks.add(HandLandmarkData(0.7f, 0.55f, -0.05f, 17)) // MCP
        landmarks.add(HandLandmarkData(0.72f, 0.42f, -0.08f, 18))
        landmarks.add(HandLandmarkData(0.75f, 0.3f, -0.1f, 19))
        landmarks.add(HandLandmarkData(0.77f, 0.18f, -0.12f, 20)) // Tip
        
        return landmarks
    }

    @Test
    fun testProductionDistanceFormula() {
        val p1 = HandLandmarkData(0f, 0f, 0f, 0)
        val p2 = HandLandmarkData(3f, 4f, 12f, 1)
        
        // Distance should be sqrt(3^2 + 4^2 + 12^2) = 13.0
        val dist = detector.distance(p1, p2)
        assertEquals(13.0f, dist, 0.001f)
    }

    @Test
    fun testProductionIsFingerFolded() {
        val wrist = HandLandmarkData(0.5f, 0.9f, 0f, 0)
        
        // Extended finger
        val mcpExtended = HandLandmarkData(0.5f, 0.5f, 0f, 5)
        val tipExtended = HandLandmarkData(0.5f, 0.1f, 0f, 8)
        assertFalse(detector.isFingerFolded(wrist, tipExtended, mcpExtended))

        // Folded finger
        val mcpFolded = HandLandmarkData(0.5f, 0.5f, 0f, 5)
        val tipFolded = HandLandmarkData(0.5f, 0.52f, 0f, 8)
        assertTrue(detector.isFingerFolded(wrist, tipFolded, mcpFolded))
    }

    @Test
    fun testRecognizePinchAsActivate() {
        // Hand with Thumb Tip (4) and Index Tip (8) very close (pinch/clenched)
        val landmarks = createBaseHand(
            thumbTipX = 0.395f, thumbTipY = 0.495f,
            indexTipX = 0.400f, indexTipY = 0.500f
        )
        
        val gesture = detector.recognizeGesture(landmarks)
        assertEquals(Gesture.ACTIVATE, gesture)
    }

    @Test
    fun testRecognizeFist() {
        val wrist = HandLandmarkData(0.5f, 0.9f, 0f, 0)
        
        // All fingers curled close to wrist / MCP
        val landmarks = mutableListOf<HandLandmarkData>().apply {
            add(wrist) // 0
            // Thumb
            add(HandLandmarkData(0.4f, 0.7f, 0f, 1))
            add(HandLandmarkData(0.3f, 0.6f, 0f, 2))
            add(HandLandmarkData(0.2f, 0.5f, 0f, 3))
            add(HandLandmarkData(0.3f, 0.65f, 0f, 4))
            // Index (folded)
            add(HandLandmarkData(0.4f, 0.5f, 0f, 5))
            add(HandLandmarkData(0.4f, 0.52f, 0f, 6))
            add(HandLandmarkData(0.4f, 0.53f, 0f, 7))
            add(HandLandmarkData(0.4f, 0.55f, 0f, 8))
            // Middle (folded)
            add(HandLandmarkData(0.5f, 0.5f, 0f, 9))
            add(HandLandmarkData(0.5f, 0.52f, 0f, 10))
            add(HandLandmarkData(0.5f, 0.53f, 0f, 11))
            add(HandLandmarkData(0.5f, 0.55f, 0f, 12))
            // Ring (folded)
            add(HandLandmarkData(0.6f, 0.52f, 0f, 13))
            add(HandLandmarkData(0.6f, 0.53f, 0f, 14))
            add(HandLandmarkData(0.6f, 0.54f, 0f, 15))
            add(HandLandmarkData(0.6f, 0.56f, 0f, 16))
            // Pinky (folded)
            add(HandLandmarkData(0.7f, 0.55f, 0f, 17))
            add(HandLandmarkData(0.7f, 0.56f, 0f, 18))
            add(HandLandmarkData(0.7f, 0.57f, 0f, 19))
            add(HandLandmarkData(0.7f, 0.58f, 0f, 20))
        }

        val gesture = detector.recognizeGesture(landmarks)
        assertEquals(Gesture.FIST, gesture)
    }

    private class MockContext : android.content.ContextWrapper(null)
}
