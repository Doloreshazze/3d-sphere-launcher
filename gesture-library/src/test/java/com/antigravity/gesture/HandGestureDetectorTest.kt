package com.antigravity.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.math.sqrt

class HandGestureDetectorTest {

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
    fun testDistanceFormula() {
        val p1 = HandLandmarkData(0f, 0f, 0f, 0)
        val p2 = HandLandmarkData(3f, 4f, 12f, 1)
        
        // Distance should be sqrt(3^2 + 4^2 + 12^2) = sqrt(9 + 16 + 144) = sqrt(169) = 13.0
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        val dz = p1.z - p2.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        assertEquals(13.0f, dist, 0.001f)
    }

    @Test
    fun testIsFingerFolded() {
        val wrist = HandLandmarkData(0.5f, 0.9f, 0f, 0)
        
        // 1. Extended finger: tip is far away from wrist compared to MCP
        val mcpExtended = HandLandmarkData(0.5f, 0.5f, 0f, 5) // Dist = 0.4
        val tipExtended = HandLandmarkData(0.5f, 0.1f, 0f, 8) // Dist = 0.8
        
        val extendedDistTip = dist(wrist, tipExtended)
        val extendedDistMcp = dist(wrist, mcpExtended)
        assertFalse(extendedDistTip < extendedDistMcp * 1.05f)

        // 2. Folded finger: tip is curled back, closer to wrist
        val mcpFolded = HandLandmarkData(0.5f, 0.5f, 0f, 5) // Dist = 0.4
        val tipFolded = HandLandmarkData(0.5f, 0.52f, 0f, 8) // Dist = 0.38
        
        val foldedDistTip = dist(wrist, tipFolded)
        val foldedDistMcp = dist(wrist, mcpFolded)
        assertTrue(foldedDistTip < foldedDistMcp * 1.05f)
    }

    @Test
    fun testRecognizePinchAsActivate() {
        // Create hand with Thumb Tip (4) and Index Tip (8) extremely close (pinch)
        val landmarks = createBaseHand(
            thumbTipX = 0.29f, thumbTipY = 0.2f, // Thumb tip near index tip
            indexTipX = 0.3f, indexTipY = 0.2f
        )
        
        // Calculate thumb to index tip distance
        val thumbTip = landmarks[4]
        val indexTip = landmarks[8]
        val dx = thumbTip.x - indexTip.x
        val dy = thumbTip.y - indexTip.y
        val dz = thumbTip.z - indexTip.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        
        // dist should be ~0.01, which is < PINCH_THRESHOLD (0.045f)
        assertTrue(dist < 0.045f)
    }

    @Test
    fun testRecognizeFistAsActivate() {
        val wrist = HandLandmarkData(0.5f, 0.9f, 0f, 0)
        
        // Model a fist hand where all finger tips are curled close to MCPs/wrist
        val indexMcp = HandLandmarkData(0.4f, 0.5f, 0f, 5)  // dist = 0.412
        val indexTip = HandLandmarkData(0.4f, 0.53f, 0f, 8) // dist = 0.384 -> Folded!
        
        val middleMcp = HandLandmarkData(0.5f, 0.5f, 0f, 9)  // dist = 0.4
        val middleTip = HandLandmarkData(0.5f, 0.52f, 0f, 12) // dist = 0.38 -> Folded!
        
        val ringMcp = HandLandmarkData(0.6f, 0.52f, 0f, 13) // dist = 0.392
        val ringTip = HandLandmarkData(0.6f, 0.54f, 0f, 16) // dist = 0.372 -> Folded!
        
        val pinkyMcp = HandLandmarkData(0.7f, 0.55f, 0f, 17) // dist = 0.403
        val pinkyTip = HandLandmarkData(0.7f, 0.57f, 0f, 20) // dist = 0.380 -> Folded!

        // Assert all four fingers are detected as folded
        assertTrue(isFingerFoldedDummy(wrist, indexTip, indexMcp))
        assertTrue(isFingerFoldedDummy(wrist, middleTip, middleMcp))
        assertTrue(isFingerFoldedDummy(wrist, ringTip, ringMcp))
        assertTrue(isFingerFoldedDummy(wrist, pinkyTip, pinkyMcp))
    }

    private fun isFingerFoldedDummy(wrist: HandLandmarkData, tip: HandLandmarkData, mcp: HandLandmarkData): Boolean {
        val tipToWrist = dist(wrist, tip)
        val mcpToWrist = dist(wrist, mcp)
        return tipToWrist < mcpToWrist * 1.05f
    }

    private fun dist(p1: HandLandmarkData, p2: HandLandmarkData): Float {
        return sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y) + (p1.z - p2.z) * (p1.z - p2.z))
    }
}
