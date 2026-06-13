package com.antigravity.gesture

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Handles initialization of MediaPipe Hand Landmarker in Live Stream mode and detects
 * gestures: UP, DOWN, LEFT, RIGHT, and ACTIVATE from realtime video frames.
 */
class HandGestureDetector(private val context: Context) : AutoCloseable {

    private var handLandmarker: HandLandmarker? = null
    private val lock = Any()

    private val _landmarksFlow = MutableStateFlow<List<HandLandmarkData>?>(null)
    /**
     * Emits the 21 hand landmarks detected in real-time, or null if no hand is detected.
     */
    val landmarksFlow: StateFlow<List<HandLandmarkData>?> = _landmarksFlow.asStateFlow()

    private val _gestureFlow = MutableStateFlow<Gesture>(Gesture.NONE)
    /**
     * Emits the active gesture detected in real-time.
     */
    val gestureFlow: StateFlow<Gesture> = _gestureFlow.asStateFlow()

    private val _handScaleFlow = MutableStateFlow(0.5f)
    /**
     * Emits the apparent scale (size) of the hand in the frame.
     */
    val handScaleFlow: StateFlow<Float> = _handScaleFlow.asStateFlow()

    // Sliding window history for wave gesture recognition
    private val history = mutableListOf<TimestampedPoint>()
    private val historyWindowMs = 450L
    private var lastGestureTimeMs = 0L
    private var lastHandDetectedTimeMs = 0L
    private var lastFistTimeMs = 0L

    // Hysteresis state for pinch (ACTIVATE) gesture.
    // Once clenched, a larger distance is required to release — prevents flickering.
    @Volatile private var isPinchActive = false
    private var lastPinchValidTimeMs = 0L
    private val pinchReleaseDebounceMs = 400L // Increased to 400ms to tolerate faster swipes

    companion object {
        private const val TAG = "HandGestureDetector"
        private const val MODEL_FILE = "hand_landmarker.task"
        private const val COOLDOWN_MS = 650L
        private const val DISPLACEMENT_THRESHOLD = 0.08f
        private const val SPEED_THRESHOLD = 0.25f // normalized units per second

        // Pinch hysteresis thresholds (as fraction of hand scale = wrist→middleMcp distance)
        // Pinch ENGAGES when dist < PINCH_ENGAGE_RATIO  (fingers close together)
        // Pinch RELEASES when dist > PINCH_RELEASE_RATIO (fingers clearly apart)
        private const val PINCH_ENGAGE_RATIO  = 0.28f
        private const val PINCH_RELEASE_RATIO = 0.40f
    }

    private data class TimestampedPoint(val x: Float, val y: Float, val timestampMs: Long, val isClenched: Boolean)

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(MODEL_FILE)

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setMinHandDetectionConfidence(0.4f)
                .setMinTrackingConfidence(0.4f)
                .setMinHandPresenceConfidence(0.4f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, mpImage ->
                    processResult(result)
                    try {
                        val bitmap = BitmapExtractor.extract(mpImage)
                        bitmap.recycle()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error recycling input frame bitmap inside ResultListener", e)
                    }
                }
                .setErrorListener { error ->
                    Log.e(TAG, "MediaPipe Hand Landmarker Error: ${error.message}", error)
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize HandLandmarker", e)
        }
    }

    /**
     * Feed camera frames into MediaPipe asynchronously.
     * [bitmap] should be scaled and rotated correctly before passing in.
     * [timestampMs] is the timestamp of the frame in milliseconds.
     */
    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        synchronized(lock) {
            val landmarker = handLandmarker
            if (landmarker == null) {
                bitmap.recycle() // Recycle immediately if landmarker is not initialized or has been closed
                return
            }
            try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                landmarker.detectAsync(mpImage, timestampMs)
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting hand landmarks in live stream", e)
                bitmap.recycle() // Recycle immediately on failure to prevent memory leak
                
                // Self-heal: If MediaPipe task crashed (e.g. OpenGL context loss onPause), reset it
                try {
                    landmarker.close()
                } catch (ce: Exception) {
                    // Ignore close errors
                }
                handLandmarker = null
                setupHandLandmarker()
            }
        }
    }

    /**
     * Re-initializes the MediaPipe landmarker if it was closed (e.g. after returning from background).
     */
    fun reinitialize() {
        synchronized(lock) {
            if (handLandmarker == null) {
                setupHandLandmarker()
            }
        }
    }

    private fun processResult(result: HandLandmarkerResult) {
        val landmarksList = result.landmarks()
        val now = SystemClock.uptimeMillis()
        if (landmarksList.isNullOrEmpty()) {
            // Debounce completely lost tracking frames (e.g. from motion blur)
            // Maintain the last known good state for up to 250ms before clearing it.
            if (now - lastHandDetectedTimeMs < 250L && isPinchActive) {
                return
            }

            _landmarksFlow.value = null
            _gestureFlow.value = Gesture.NONE
            isPinchActive = false // Reset pinch state when hand is completely lost for >250ms
            synchronized(history) {
                // Clear history only if hand has been missing for a significant duration (e.g. 350ms)
                if (now - lastHandDetectedTimeMs > 350L) {
                    history.clear()
                }
            }
            return
        }

        lastHandDetectedTimeMs = now

        // We process the first detected hand for gesture recognition
        val handLandmarks = landmarksList[0]
        val landmarksData = handLandmarks.mapIndexed { idx, lm ->
            HandLandmarkData(
                x = lm.x(),
                y = lm.y(),
                z = lm.z(),
                index = idx
            )
        }
        _landmarksFlow.value = landmarksData

        // Perform gesture recognition
        val gesture = recognizeGesture(landmarksData)
        _gestureFlow.value = gesture
    }

    internal fun recognizeGesture(landmarks: List<HandLandmarkData>): Gesture {
        val now = SystemClock.uptimeMillis()
        val wrist = landmarks[0]
        val thumbTip = landmarks[4]
        val indexTip = landmarks[8]
        val indexMcp = landmarks[5]
        val middleTip = landmarks[12]
        val middleMcp = landmarks[9]
        val ringTip = landmarks[16]
        val ringMcp = landmarks[13]
        val pinkyTip = landmarks[20]
        val pinkyMcp = landmarks[17]

        // Pre-evaluate static clenched state (Pinch of Thumb & Index finger tips)
        // Uses hysteresis: different thresholds for engage vs. release to prevent flicker.
        val handScale = distance(wrist, middleMcp)
        _handScaleFlow.value = handScale
        val pinchDist = distance(thumbTip, indexTip)
        
        val isClenched: Boolean
        
        if (isPinchActive) {
            // Already pinching — only release if fingers are clearly apart AND scale is valid,
            // OR if the tracking is bad (scale < 0.01f). In BOTH release cases, we use the debounce!
            val isValidAndPinched = handScale > 0.01f && pinchDist < handScale * PINCH_RELEASE_RATIO
            if (isValidAndPinched) {
                lastPinchValidTimeMs = now
                isClenched = true
            } else {
                // If it's invalid or fingers opened, hold the state for the debounce period
                isClenched = now - lastPinchValidTimeMs < pinchReleaseDebounceMs
            }
        } else {
            // Not yet pinching — engage only when tracking is good and fingers are clearly close
            if (handScale > 0.01f && pinchDist < handScale * PINCH_ENGAGE_RATIO) {
                lastPinchValidTimeMs = now
                isClenched = true
            } else {
                isClenched = false
            }
        }
        isPinchActive = isClenched

        // 1. Evaluate dynamic wave gestures first
        val palmCenterX = (wrist.x + indexMcp.x + pinkyMcp.x) / 3f
        val palmCenterY = (wrist.y + indexMcp.y + pinkyMcp.y) / 3f

        var detectedWave = Gesture.NONE

        synchronized(history) {
            // Add center point to window, capturing clenched state
            history.add(TimestampedPoint(palmCenterX, palmCenterY, now, isClenched))
            
            // Clean up old points
            val cutoff = now - historyWindowMs
            history.removeAll { it.timestampMs < cutoff }

            // Evaluate wave gesture if cooldown is active and history contains enough samples (at least 3 frames)
            if (now - lastGestureTimeMs >= COOLDOWN_MS && history.size >= 3) {
                // To perform a swipe, the hand must currently be clenched (ACTIVATE)
                // and have been clenched for at least 50% of the movement duration
                val isClenchedSwipe = history.last().isClenched &&
                        history.count { it.isClenched } >= (history.size * 0.5f).coerceAtLeast(2f)

                if (isClenchedSwipe) {
                    val start = history.first()
                    val end = history.last()
                    val durationSec = (end.timestampMs - start.timestampMs) / 1000f

                    if (durationSec > 0.05f) {
                        val dx = end.x - start.x
                        val dy = end.y - start.y
                        val speedX = abs(dx) / durationSec
                        val speedY = abs(dy) / durationSec

                        // Check total path vs displacement for linearity
                        var pathLength = 0f
                        for (i in 1 until history.size) {
                            val p1 = history[i - 1]
                            val p2 = history[i]
                            pathLength += sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
                        }
                        val displacement = sqrt(dx.pow(2) + dy.pow(2))
                        val isStraight = displacement > 0.60f * pathLength // relatively linear movement

                        if (isStraight) {
                            if (abs(dx) > abs(dy)) {
                                // Horizontal wave
                                if (abs(dx) > DISPLACEMENT_THRESHOLD && speedX > SPEED_THRESHOLD) {
                                    detectedWave = if (dx > 0f) Gesture.RIGHT else Gesture.LEFT
                                }
                            } else {
                                // Vertical wave
                                if (abs(dy) > DISPLACEMENT_THRESHOLD && speedY > SPEED_THRESHOLD) {
                                    detectedWave = if (dy > 0f) Gesture.DOWN else Gesture.UP
                                }
                            }
                        }
                    }
                }
            }

            if (detectedWave != Gesture.NONE) {
                lastGestureTimeMs = now
                history.clear() // Clear sliding window to prevent double-firing
            }
        }

        if (detectedWave != Gesture.NONE) {
            return detectedWave
        }

        // Evaluate Fist to Open Palm
        val isFist = isFingerFolded(wrist, indexTip, indexMcp) &&
                     isFingerFolded(wrist, middleTip, middleMcp) &&
                     isFingerFolded(wrist, ringTip, ringMcp) &&
                     isFingerFolded(wrist, pinkyTip, pinkyMcp)
                     
        val isOpenPalm = !isFingerFolded(wrist, indexTip, indexMcp) &&
                         !isFingerFolded(wrist, middleTip, middleMcp) &&
                         !isFingerFolded(wrist, ringTip, ringMcp) &&
                         !isFingerFolded(wrist, pinkyTip, pinkyMcp)

        if (isFist) {
            lastFistTimeMs = now
        }

        if (isOpenPalm && (now - lastFistTimeMs) < 800L && (now - lastGestureTimeMs) >= COOLDOWN_MS) {
            lastGestureTimeMs = now
            lastFistTimeMs = 0L
            return Gesture.FIST_TO_OPEN_PALM
        }

        // 2. If no wave gesture is active, check static "ACTIVATE" poses
        if (isClenched) {
            return Gesture.ACTIVATE
        }

        return Gesture.NONE
    }

    internal fun isFingerFolded(wrist: HandLandmarkData, tip: HandLandmarkData, mcp: HandLandmarkData): Boolean {
        val tipToWrist = distance(wrist, tip)
        val mcpToWrist = distance(wrist, mcp)
        // If the tip is closer to the wrist than the knuckle joint, the finger is folded
        return tipToWrist < mcpToWrist * 1.05f
    }

    internal fun distance(p1: HandLandmarkData, p2: HandLandmarkData): Float {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2) + (p1.z - p2.z).pow(2))
    }

    override fun close() {
        synchronized(lock) {
            handLandmarker?.close()
            handLandmarker = null
        }
    }
}
