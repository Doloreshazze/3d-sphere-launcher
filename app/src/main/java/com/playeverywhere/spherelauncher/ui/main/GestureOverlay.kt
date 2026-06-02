package com.playeverywhere.spherelauncher.ui.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.antigravity.gesture.HandLandmarkData

@Composable
fun SemiTransparentHandOverlay(
    landmarks: List<HandLandmarkData>?,
    modifier: Modifier = Modifier
) {
    if (landmarks.isNullOrEmpty()) return

    // Pulse animation for fingertips
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Hand skeletal connections mapping
    val connections = remember {
        listOf(
            // Thumb
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
            // Index
            Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
            // Middle
            Pair(0, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12),
            // Ring
            Pair(0, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16),
            // Pinky
            Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20),
            // Palm base knuckles
            Pair(5, 9), Pair(9, 13), Pair(13, 17)
        )
    }

    // Holographic semi-transparent neon cyan color
    val skeletonColor = Color(0xFF00FFCC)

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 1. Draw bones (twin-stroke lines for glowing look)
        connections.forEach { (startIdx, endIdx) ->
            val startLm = landmarks.find { it.index == startIdx }
            val endLm = landmarks.find { it.index == endIdx }

            if (startLm != null && endLm != null) {
                // Coordinates from MediaPipe are in [0, 1] relative to rotated/mirrored camera preview.
                // Draw over the full screen Canvas space.
                val startPoint = Offset(startLm.x * canvasWidth, startLm.y * canvasHeight)
                val endPoint = Offset(endLm.x * canvasWidth, endLm.y * canvasHeight)

                // Wide background glow line (highly transparent)
                drawLine(
                    color = skeletonColor.copy(alpha = 0.15f),
                    start = startPoint,
                    end = endPoint,
                    strokeWidth = 18f,
                    cap = StrokeCap.Round
                )

                // Mid-thickness glow line (semi-transparent)
                drawLine(
                    color = skeletonColor.copy(alpha = 0.35f),
                    start = startPoint,
                    end = endPoint,
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )

                // Sharp inner core (semi-transparent white)
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = startPoint,
                    end = endPoint,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }

        // 2. Draw joint nodes
        landmarks.forEach { lm ->
            val point = Offset(lm.x * canvasWidth, lm.y * canvasHeight)
            val isTip = lm.index in listOf(4, 8, 12, 16, 20)

            if (isTip) {
                // Glowing aura for fingertips
                drawCircle(
                    color = skeletonColor.copy(alpha = 0.20f),
                    radius = pulseScale * 1.5f,
                    center = point
                )
                // Solid node (semi-transparent cyan)
                drawCircle(
                    color = skeletonColor.copy(alpha = 0.45f),
                    radius = 12f,
                    center = point
                )
                // Center core (semi-transparent white)
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = 5f,
                    center = point
                )
            } else {
                // Normal joint node
                drawCircle(
                    color = skeletonColor.copy(alpha = 0.4f),
                    radius = 7f,
                    center = point
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = 3f,
                    center = point
                )
            }
        }
    }
}
