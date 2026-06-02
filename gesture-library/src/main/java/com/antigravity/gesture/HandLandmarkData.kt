package com.antigravity.gesture

/**
 * Represents a single hand landmark in 3D normalized image space.
 * [x] and [y] are in range [0, 1] relative to the image boundaries.
 * [z] represents the landmark depth, with the depth at the wrist being the origin.
 * The smaller the value, the closer the landmark is to the camera.
 */
data class HandLandmarkData(
    val x: Float,
    val y: Float,
    val z: Float,
    val index: Int
)

/**
 * Helper data class that represents a connection between two landmarks to build a skeleton.
 */
data class HandConnection(
    val start: HandLandmarkData,
    val end: HandLandmarkData
)
