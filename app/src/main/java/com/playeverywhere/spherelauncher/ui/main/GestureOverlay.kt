package com.playeverywhere.spherelauncher.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.antigravity.gesture.HandLandmarkData

// Hand skeleton connections (MediaPipe 21-landmark model)
private val HAND_CONNECTIONS = listOf(
    // Thumb
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    // Index
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    // Middle
    0 to 9, 9 to 10, 10 to 11, 11 to 12,
    // Ring
    0 to 13, 13 to 14, 14 to 15, 15 to 16,
    // Pinky
    0 to 17, 17 to 18, 18 to 19, 19 to 20,
    // Palm knuckles
    5 to 9, 9 to 13, 13 to 17
)

private val FINGERTIP_INDICES = setOf(4, 8, 12, 16, 20)
private val KNUCKLE_INDICES = setOf(5, 9, 13, 17)

// Ghost-hand colour palette — warm translucent skin tones with soft blue-white highlights
private val SKIN_BASE    = Color(0xFFFFD0A0) // warm peach
private val SKIN_DEEP    = Color(0xFFD4956A) // deeper amber for depth
private val JOINT_GLOW   = Color(0xFFE8F4FF) // cold-white joint sheen
private val TIP_GLOW     = Color(0xFFF0E8FF) // slight violet for fingertips

@Composable
fun SemiTransparentHandOverlay(
    landmarks: List<HandLandmarkData>?,
    modifier: Modifier = Modifier
) {
    if (landmarks.isNullOrEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        fun lmOffset(idx: Int): Offset {
            val lm = landmarks.find { it.index == idx } ?: return Offset(w * 0.5f, h * 0.5f)
            return Offset(lm.x * w, lm.y * h)
        }

        // ── 1. PALM — Volumetric fill ──────────────────────────────
        val palmPath = Path().apply {
            val wrist = lmOffset(0)
            moveTo(wrist.x, wrist.y)
            
            val p1 = lmOffset(1)
            val p5 = lmOffset(5)
            val p9 = lmOffset(9)
            val p13 = lmOffset(13)
            val p17 = lmOffset(17)

            lineTo(p1.x, p1.y) // base of thumb
            lineTo(p5.x, p5.y) // index knuckle
            lineTo(p9.x, p9.y) // middle knuckle
            lineTo(p13.x, p13.y) // ring knuckle
            lineTo(p17.x, p17.y) // pinky knuckle
            close() // back to wrist
        }

        // Deep shadow/base for the palm to give thickness
        drawPath(
            path = palmPath,
            color = SKIN_DEEP.copy(alpha = 0.15f),
            style = Stroke(width = 60f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )
        // Main translucent fleshy fill for the palm
        drawPath(
            path = palmPath,
            color = SKIN_BASE.copy(alpha = 0.28f),
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
        drawPath(
            path = palmPath,
            color = SKIN_BASE.copy(alpha = 0.35f),
            style = Stroke(width = 30f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )

        // ── 2. FINGERS — Volumetric thick curves ──────────────────────────────
        // Each finger is drawn as a continuous, thick, rounded path.
        val fingers = listOf(
            listOf(1, 2, 3, 4),      // thumb
            listOf(5, 6, 7, 8),      // index
            listOf(9, 10, 11, 12),   // middle
            listOf(13, 14, 15, 16),  // ring
            listOf(17, 18, 19, 20)   // pinky
        )

        fingers.forEach { fingerNodeIndices ->
            val path = Path().apply {
                val start = lmOffset(fingerNodeIndices[0])
                moveTo(start.x, start.y)
                for (i in 1 until fingerNodeIndices.size) {
                    val pt = lmOffset(fingerNodeIndices[i])
                    lineTo(pt.x, pt.y)
                }
            }

            // Layer 1: Outer soft flesh / ambient occlusion (thickest, darkest)
            drawPath(
                path = path,
                color = SKIN_DEEP.copy(alpha = 0.18f),
                style = Stroke(width = 54f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Layer 2: Main skin volume (mid thickness, warm peach)
            drawPath(
                path = path,
                color = SKIN_BASE.copy(alpha = 0.38f),
                style = Stroke(width = 38f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Layer 3: Specular highlight / core (simulates cylindrical 3D light reflection)
            drawPath(
                path = path,
                color = JOINT_GLOW.copy(alpha = 0.25f),
                style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        
        // ── 3. FINGERTIPS — Volumetric rounded caps ──────────────────────────────
        FINGERTIP_INDICES.forEach { idx ->
            val pt = lmOffset(idx)
            // Soft glow at the very tips
            drawCircle(TIP_GLOW.copy(alpha = 0.25f), radius = 22f, center = pt)
        }
    }
}
