package com.playeverywhere.spherelauncher.ui.main

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playeverywhere.spherelauncher.data.AppInfo
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow

// SphereNode holds the base coordinates on the unit sphere
data class SphereNode(
    val appInfo: AppInfo,
    val xBase: Float,
    val yBase: Float,
    val zBase: Float
)

data class AppRenderNode(
    val appInfo: AppInfo,
    val xProj: Float,
    val yProj: Float,
    val finalScale: Float,
    val alpha: Float,
    val depthRatio: Float,
    val zDepth: Float
)

data class Vector3D(val x: Float, val y: Float, val z: Float) {
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalized(): Vector3D {
        val len = length()
        return if (len > 0f) Vector3D(x / len, y / len, z / len) else this
    }
    operator fun plus(v: Vector3D) = Vector3D(x + v.x, y + v.y, z + v.z)
    operator fun times(s: Float) = Vector3D(x * s, y * s, z * s)
}

// A high-performance, non-compose state reference wrapper to pass values inside drawing phase with zero overhead
class PositionRef(
    var depthRatio: Float = 0.5f,
    var x: Float = 0f,
    var y: Float = 0f
)

class RotationState(
    var pitch: Float = 0f,
    var yaw: Float = 0f,
    var tiltPitch: Float = 0f,
    var tiltYaw: Float = 0f,
    var radius: Float = 300f
)

class FrameRotationData(
    var cosP: Float = 1f, var sinP: Float = 0f,
    var cosY: Float = 1f, var sinY: Float = 0f,
    var radius: Float = 300f
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Sphere3D(
    apps: List<AppInfo>,
    style: SphereStyle,
    isAutoDriftEnabled: Boolean,
    isTiltEnabled: Boolean,
    shapeType: ShapeType,
    modifier: Modifier = Modifier,
    isShapeLocked: Boolean = false,
    isInertiaEnabled: Boolean = true,
    glowColor: GlowColorOption,
    glowOpacity: Float,
    glowBrightness: Float,
    isPulsingEnabled: Boolean = false,
    isAudioReactiveEnabled: Boolean = false,
    audioAmplitude: Float = 0.0f,
    onAppClick: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    
    val systemPrimary = MaterialTheme.colorScheme.primary
    val systemSecondary = MaterialTheme.colorScheme.secondary

    android.util.Log.d("Sphere3D", "=== Sphere3D recomposed ===")

    // Screen dimension calculations
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // Base radius of the sphere adaptively sized to screen
    val baseRadius = (screenWidthPx.coerceAtMost(screenHeightPx) * 0.38f).coerceAtLeast(250f)

    // Rotation state & fast frame cache (completely non-compose states)
    val rotationState = remember { RotationState(radius = baseRadius) }
    val frameRotationData = remember { FrameRotationData(radius = baseRadius) }

    val densityDp = density.density
    val canvasShadowPaint = remember {
        android.graphics.Paint().apply {
            color = 0x01000000
            isAntiAlias = true
            this.style = android.graphics.Paint.Style.FILL
        }
    }
    
    val canvasBorderPaint = remember {
        android.graphics.Paint().apply {
            color = 0x40FFFFFF
            isAntiAlias = true
            this.style = android.graphics.Paint.Style.STROKE
            strokeWidth = 0.8f * densityDp
        }
    }
    
    val canvasIconPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
    }
    
    val canvasGlossPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            this.style = android.graphics.Paint.Style.FILL
        }
    }
    
    val canvasGlowPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            this.style = android.graphics.Paint.Style.FILL
        }
    }

    // Single Compose State ticket to synchronize VSYNC and Draw phases
    var frameTicket by remember { mutableStateOf(0) }

    // Protection against accidental clicks during panning/dragging
    var allowClicks by remember { mutableStateOf(true) }

    val lastProjectedNodes = remember { ArrayList<AppRenderNode>() }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val spherePulseScale by infiniteTransition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spherePulse"
    )

    val columns = 4
    val rows = remember(apps.size) { kotlin.math.ceil((apps.size + 1).toFloat() / columns).toInt().coerceAtLeast(1) }
    val numCells = rows * columns
    val boardPositions = remember(apps) {
        val list = (0 until apps.size).map { it as Int? }.toMutableList()
        while (list.size < numCells) {
            list.add(null)
        }
        androidx.compose.runtime.mutableStateListOf<Int?>().apply {
            addAll(list)
        }
    }
    val onTileTap: (Int) -> Boolean = remember(boardPositions) {
        { appIndex ->
            val currentIndex = boardPositions.indexOf(appIndex)
            if (currentIndex != -1) {
                val col = currentIndex % columns
                val row = currentIndex / columns
                val neighbors = listOfNotNull(
                    if (col > 0) currentIndex - 1 else null,
                    if (col < columns - 1) currentIndex + 1 else null,
                    if (row > 0) currentIndex - columns else null,
                    if (row < rows - 1) currentIndex + columns else null
                )
                val emptyIndex = neighbors.firstOrNull { boardPositions[it] == null }
                if (emptyIndex != null) {
                    boardPositions[emptyIndex] = appIndex
                    boardPositions[currentIndex] = null
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    // Snake Game State
    val snakeGridSize = 12
    val snakeBody = remember { androidx.compose.runtime.mutableStateListOf(Pair(5, 5), Pair(5, 6), Pair(5, 7)) } // Head is at index 0
    var snakeDirection by remember { mutableStateOf(Pair(0, -1)) } // Start moving UP
    var foodPos by remember { mutableStateOf(Pair(8, 4)) }
    var isGameOver by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var highScore by remember { mutableStateOf(0) }
    
    val restartSnakeGame = remember(apps) {
        {
            snakeBody.clear()
            snakeBody.addAll(listOf(Pair(5, 5), Pair(5, 6), Pair(5, 7)))
            snakeDirection = Pair(0, -1)
            var newFood = Pair(
                (0 until snakeGridSize).random(),
                (0 until snakeGridSize).random()
            )
            while (snakeBody.contains(newFood)) {
                newFood = Pair(
                    (0 until snakeGridSize).random(),
                    (0 until snakeGridSize).random()
                )
            }
            foodPos = newFood
            isGameOver = false
            score = 0
        }
    }

    LaunchedEffect(shapeType) {
        if (shapeType == ShapeType.SNAKE) {
            restartSnakeGame()
        }
    }

    LaunchedEffect(shapeType, isGameOver) {
        if (shapeType == ShapeType.SNAKE && !isGameOver) {
            while (true) {
                delay(300L) // Tick rate: 300ms
                val head = snakeBody.firstOrNull() ?: Pair(5, 5)
                val nextHead = Pair(
                    (head.first + snakeDirection.first + snakeGridSize) % snakeGridSize,
                    (head.second + snakeDirection.second + snakeGridSize) % snakeGridSize
                )
                
                if (snakeBody.contains(nextHead)) {
                    isGameOver = true
                    if (score > highScore) {
                        highScore = score
                    }
                    break
                }
                
                snakeBody.add(0, nextHead)
                
                if (nextHead == foodPos) {
                    score++
                    var newFood = Pair((0 until snakeGridSize).random(), (0 until snakeGridSize).random())
                    while (snakeBody.contains(newFood)) {
                        newFood = Pair((0 until snakeGridSize).random(), (0 until snakeGridSize).random())
                    }
                    foodPos = newFood
                } else {
                    if (snakeBody.isNotEmpty()) {
                        snakeBody.removeAt(snakeBody.lastIndex)
                    }
                }
            }
        }
    }

    // Touch velocities for physics fling inertia (regular floats, VSYNC-only changes)
    var yawVelocity = remember { floatArrayOf(0f, 0f) } // index 0 = yawVelocity, 1 = pitchVelocity

    // Raw tilt values from sensor (regular array to prevent recomposition flood)
    val tiltValues = remember { FloatArray(2) }

    // Track user touch state to pause auto-drift
    var isUserInteracting by remember { mutableStateOf(false) }

    // Unified VSYNC-based loop for physics, auto-drift, and tilt updates (recomposes 0 times!)
    LaunchedEffect(isUserInteracting, isAutoDriftEnabled, isTiltEnabled, shapeType, isShapeLocked) {
        if (shapeType == ShapeType.FLAT_PLANE) {
            rotationState.yaw = 0f
            rotationState.pitch = 0f
            yawVelocity[0] = 0f
            yawVelocity[1] = 0f
        }
        val friction = 0.982f // Heavier, more massive physical inertia (decelerates much slower)
        val driftSpeedX = 0.015f // in radians per second
        val driftSpeedY = 0.035f // in radians per second
        var lastTime = withFrameMillis { it }
        
        while (true) {
            withFrameMillis { time ->
                val deltaTime = ((time - lastTime).coerceIn(1L, 100L)) / 1000f // Clamp delta to avoid huge jumps on frame drops
                lastTime = time
                
                val timeFactor = deltaTime * 60f // normalizes physics to 60fps base rate
                
                if (isShapeLocked) {
                    // Lock active rotation and drift, preserving current yaw/pitch values!
                    yawVelocity[0] = 0f
                    yawVelocity[1] = 0f
                    rotationState.tiltYaw = 0f
                    rotationState.tiltPitch = 0f
                } else {
                    // 1. Update physics if not interacting
                    if (!isUserInteracting) {
                        val yVel = yawVelocity[0]
                        val pVel = yawVelocity[1]
                        if (kotlin.math.abs(yVel) > 0.0001f || kotlin.math.abs(pVel) > 0.0001f) {
                            rotationState.yaw += yVel * timeFactor
                            rotationState.pitch += pVel * timeFactor
                            yawVelocity[0] = yVel * friction.pow(timeFactor)
                            yawVelocity[1] = pVel * friction.pow(timeFactor)
                        } else if (isAutoDriftEnabled) {
                            if (shapeType == ShapeType.FLAT_PLANE) {
                                rotationState.yaw += 0.02f * deltaTime   // Gentle slow horizontal float
                                rotationState.pitch += 0.02f * deltaTime  // Gentle slow vertical float
                            } else {
                                rotationState.yaw += driftSpeedY * deltaTime
                                rotationState.pitch += driftSpeedX * deltaTime
                            }
                        }
                    }
                    
                    if (shapeType == ShapeType.FLAT_PLANE) {
                        rotationState.yaw = rotationState.yaw.coerceIn(-1.5f, 1.5f)
                        rotationState.pitch = rotationState.pitch.coerceIn(-1.5f, 1.5f)
                    }
                    
                    // 2. Safely sync raw tilt values strictly at VSYNC rate
                    if (isTiltEnabled) {
                        rotationState.tiltYaw = tiltValues[0]
                        rotationState.tiltPitch = tiltValues[1]
                    } else {
                        rotationState.tiltYaw = 0f
                        rotationState.tiltPitch = 0f
                    }
                }

                // 3. Cache sines and cosines EXACTLY ONCE on the CPU (using fast float math!)
                val totalPitch = rotationState.pitch + rotationState.tiltPitch
                val totalYaw = rotationState.yaw + rotationState.tiltYaw

                frameRotationData.cosP = kotlin.math.cos(totalPitch)
                frameRotationData.sinP = kotlin.math.sin(totalPitch)
                frameRotationData.cosY = kotlin.math.cos(totalYaw)
                frameRotationData.sinY = kotlin.math.sin(totalYaw)
                frameRotationData.radius = rotationState.radius

                // 4. Trigger redraw by incrementing frameTicket
                frameTicket++

                if (frameTicket % 60 == 0) {
                    android.util.Log.d("Sphere3D", "VSYNC loop running, radius: ${rotationState.radius}, frameTicket: $frameTicket")
                }
            }
        }
    }

    // Accelerometer listener for 3D parallax tilt effect (writes to non-state FloatArray)
    if (isTiltEnabled) {
        val currentContext = LocalContext.current
        DisposableEffect(Unit) {
            val sensorManager = currentContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            
            var smoothX = 0f
            var smoothY = 0f
            val alphaFilter = 0.15f // Lower value = smoother/slower tilt reaction
            
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    val x = event.values[0]
                    val y = event.values[1]
                    
                    // Smooth the sensor readings using a low-pass filter
                    smoothX = smoothX * (1f - alphaFilter) + x * alphaFilter
                    smoothY = smoothY * (1f - alphaFilter) + y * alphaFilter
                    
                    // Map to yaw and pitch tilt offsets and store in raw array (no recomposition!)
                    tiltValues[0] = -smoothX * 0.025f
                    tiltValues[1] = (smoothY - 5.0f) * 0.025f
                }
                
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            
            if (accelerometer != null) {
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            }
            
            onDispose {
                sensorManager.unregisterListener(listener)
                tiltValues[0] = 0f
                tiltValues[1] = 0f
            }
        }
    }

    // Distribute apps uniformly based on selected 3D Shape
    val sphereNodes = remember(apps, shapeType) {
        val count = apps.size
        if (count == 0) return@remember emptyList<SphereNode>()
        if (count == 1) return@remember listOf(SphereNode(apps[0], 0f, 0f, -1f))

        if (shapeType == ShapeType.FLAT_PLANE) {
            // Distribute in a spacious flat 4-column puzzle board grid
            val columns = 4
            val spacing = 0.42f
            val numCols = columns.toFloat()
            val numRows = kotlin.math.ceil((count + 1).toFloat() / columns).coerceAtLeast(1f)
            
            apps.mapIndexed { index, app ->
                val col = index % columns
                val row = index / columns
                
                // Center the entire sheet grid perfectly around (0,0)
                val x = (col - (numCols - 1) / 2f) * spacing
                val y = ((numRows - 1) / 2f - row) * spacing
                val z = 0f
                
                SphereNode(app, x, y, z)
            }
        } else if (shapeType == ShapeType.HEAD) {
            // Custom 3D Human Head model!
            val goldenAngle = Math.PI * (3.0 - sqrt(5.0))
            apps.mapIndexed { index, app ->
                val y = 1.0f - (index.toFloat() / (count - 1)) * 2.0f
                val radiusAtY = sqrt((1.0f - y * y).coerceAtLeast(0f))
                val theta = (index * goldenAngle).toFloat()
                val x = cos(theta.toDouble()).toFloat() * radiusAtY
                val z = sin(theta.toDouble()).toFloat() * radiusAtY
                
                // Deform the sphere mathematically into a gorgeous 3D Human Head/Face sculpture
                // Cranium elongation (head is taller than it is wide)
                var newY = y * 1.15f
                
                // Compression for narrower head
                var newX = x * 0.82f
                var newZ = z * 0.95f
                
                if (newZ > 0f) {
                    // Narrow face front
                    newX *= 0.9f
                    
                    // Nose protrusion (front center)
                    if (newY in -0.05f..0.2f && kotlin.math.abs(newX) < 0.12f) {
                        val noseIntensity = (1.0f - kotlin.math.abs(newX) / 0.12f) * (1.0f - kotlin.math.abs(newY - 0.08f) / 0.13f)
                        newZ += 0.35f * noseIntensity
                    }
                    
                    // Chin protrusion (lower center)
                    if (newY in -0.65f..-0.35f && kotlin.math.abs(newX) < 0.15f) {
                        val chinIntensity = (1.0f - kotlin.math.abs(newX) / 0.15f) * (1.0f - kotlin.math.abs(newY + 0.5f) / 0.15f)
                        newZ += 0.18f * chinIntensity
                        newY -= 0.05f * chinIntensity
                    }
                    
                    // Forehead slope
                    if (newY > 0.3f) {
                        newZ -= (newY - 0.3f) * 0.15f
                    }
                }
                
                // Jaw narrowing
                if (newY < -0.4f) {
                    newX *= (1.0f - (newY + 0.4f) * 0.3f)
                }
                
                SphereNode(app, newX, newY, newZ)
            }
        } else {
            // All our shapes (Sphere, Polyhedron, Solid Sphere) distribute points perfectly uniformly 
            // across the entire sphere bounds to maximize visual balance and eliminate overlaps!
            val goldenAngle = Math.PI * (3.0 - sqrt(5.0))
            apps.mapIndexed { index, app ->
                val y = 1.0f - (index.toFloat() / (count - 1)) * 2.0f
                val radiusAtY = sqrt((1.0f - y * y).coerceAtLeast(0f))
                val theta = (index * goldenAngle).toFloat()
                val x = cos(theta.toDouble()).toFloat() * radiusAtY
                val z = sin(theta.toDouble()).toFloat() * radiusAtY
                SphereNode(app, x, y, z)
            }
        }
    }

    // Box where the 3D Sphere is rendered
    Box(
        modifier = modifier
            .fillMaxSize()
            // High-performance single pointerInput block running in PointerEventPass.Initial
            // Intercepts swipe and pinch gestures before clickable children consume them,
            // while allowing click/tap events to pass down when there is no movement!
            .pointerInput(isShapeLocked, isInertiaEnabled, shapeType) {
                awaitPointerEventScope {
                    var totalDragDistance = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        
                        // 1. Update user interaction status based on active touches
                        val anyPressed = event.changes.any { it.pressed }
                        val downEvent = event.changes.any { it.pressed && !it.previousPressed }
                        
                        if (downEvent) {
                            allowClicks = true
                            totalDragDistance = 0f
                            isUserInteracting = true
                            yawVelocity[0] = 0f
                            yawVelocity[1] = 0f
                        } else if (!anyPressed && isUserInteracting) {
                            isUserInteracting = false
                        }
                        
                        // 2. Track swipe and pinch-to-zoom gestures
                        val changes = event.changes.filter { it.pressed && it.previousPressed }
                        if (changes.size == 1) {
                            // One-finger swipe rotation
                            val change = changes[0]
                            val delta = change.position - change.previousPosition
                            
                            if (shapeType == ShapeType.SNAKE) {
                                val dx = delta.x
                                val dy = delta.y
                                if (kotlin.math.abs(dx) > 8f || kotlin.math.abs(dy) > 8f) {
                                    if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                        if (dx > 0f && snakeDirection.first != -1) {
                                            snakeDirection = Pair(1, 0)
                                        } else if (dx < 0f && snakeDirection.first != 1) {
                                            snakeDirection = Pair(-1, 0)
                                        }
                                    } else {
                                        if (dy > 0f && snakeDirection.second != -1) {
                                            snakeDirection = Pair(0, 1)
                                        } else if (dy < 0f && snakeDirection.second != 1) {
                                            snakeDirection = Pair(0, -1)
                                        }
                                    }
                                }
                            } else {
                                val dragDist = kotlin.math.sqrt(delta.x * delta.x + delta.y * delta.y)
                                totalDragDistance += dragDist
                                if (totalDragDistance > 15f) { // ~5-8 dps threshold to filter out accidental clicks
                                    allowClicks = false
                                }
                                
                                if (!isShapeLocked) {
                                    val sensitivity = 0.0028f
                                    val dYaw = -delta.x * sensitivity
                                    val dPitch = delta.y * sensitivity
                                    
                                    rotationState.yaw += dYaw
                                    rotationState.pitch += dPitch
                                    
                                    if (shapeType == ShapeType.FLAT_PLANE) {
                                        rotationState.yaw = rotationState.yaw.coerceIn(-1.5f, 1.5f)
                                        rotationState.pitch = rotationState.pitch.coerceIn(-1.5f, 1.5f)
                                    }
                                    
                                    if (isInertiaEnabled) {
                                        // Keep velocities for inertia fling
                                        val flingVelocityScale = 0.45f
                                        yawVelocity[0] = yawVelocity[0] * 0.7f + (dYaw * flingVelocityScale) * 0.3f
                                        yawVelocity[1] = yawVelocity[1] * 0.7f + (dPitch * flingVelocityScale) * 0.3f
                                    } else {
                                        // Instantly stop flinging/inertia
                                        yawVelocity[0] = 0f
                                        yawVelocity[1] = 0f
                                    }
                                }
                            }
                        } else if (changes.size >= 2) {
                            // Pinch also disables clicks
                            allowClicks = false
                            
                            val p1 = changes[0]
                            val p2 = changes[1]
                            
                            val dxCurr = p1.position.x - p2.position.x
                            val dyCurr = p1.position.y - p2.position.y
                            val distCurrent = kotlin.math.sqrt(dxCurr * dxCurr + dyCurr * dyCurr)

                            val dxPrev = p1.previousPosition.x - p2.previousPosition.x
                            val dyPrev = p1.previousPosition.y - p2.previousPosition.y
                            val distPrevious = kotlin.math.sqrt(dxPrev * dxPrev + dyPrev * dyPrev)
                            
                            if (distPrevious > 0.1f) {
                                val zoomMultiplier = distCurrent / distPrevious
                                rotationState.radius = (rotationState.radius * zoomMultiplier).coerceIn(120f, 1000f)
                            }
                            
                            if (!isShapeLocked) {
                                // Also support two-finger panning
                                val centroidCurrentX = (p1.position.x + p2.position.x) / 2f
                                val centroidCurrentY = (p1.position.y + p2.position.y) / 2f
                                val centroidPreviousX = (p1.previousPosition.x + p2.previousPosition.x) / 2f
                                val centroidPreviousY = (p1.previousPosition.y + p2.previousPosition.y) / 2f
                                
                                val dX = centroidCurrentX - centroidPreviousX
                                val dY = centroidCurrentY - centroidPreviousY
                                
                                val sensitivity = 0.0028f
                                rotationState.yaw += -dX * sensitivity
                                rotationState.pitch += dY * sensitivity
                            }
                            
                            if (shapeType == ShapeType.FLAT_PLANE) {
                                rotationState.yaw = rotationState.yaw.coerceIn(-1.5f, 1.5f)
                                rotationState.pitch = rotationState.pitch.coerceIn(-1.5f, 1.5f)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (shapeType == ShapeType.SPHERE || shapeType == ShapeType.HEAD) {
            val tileSize = (400f / kotlin.math.sqrt(apps.size.toFloat())).coerceIn(45f, 80f)
            val iconSize = tileSize * 0.70f
            
            val tileSizePx = with(LocalDensity.current) { tileSize.dp.toPx() }
            val iconSizePx = with(LocalDensity.current) { iconSize.dp.toPx() }
            
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(apps) {
                        detectTapGestures { tapOffset ->
                            if (allowClicks) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val tapX = tapOffset.x - canvasWidth / 2f
                                val tapY = tapOffset.y - canvasHeight / 2f
                                
                                val hitTarget = lastProjectedNodes
                                    .filter { it.depthRatio > 0.35f }
                                    .minByOrNull { node ->
                                        val dx = tapX - node.xProj
                                        val dy = tapY - node.yProj
                                        kotlin.math.sqrt(dx * dx + dy * dy)
                                    }
                                    
                                if (hitTarget != null) {
                                    val dx = tapX - hitTarget.xProj
                                    val dy = tapY - hitTarget.yProj
                                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                                    val currentIconSize = iconSizePx * hitTarget.finalScale
                                    val maxTouchDist = currentIconSize / 2f + 12f * densityDp
                                    if (dist < maxTouchDist) {
                                        onAppClick(hitTarget.appInfo)
                                    }
                                }
                            }
                        }
                    }
            ) {
                val ticket = frameTicket
                
                val nativeCanvas = drawContext.canvas.nativeCanvas
                val w = size.width
                val h = size.height
                val centerCanvasX = w / 2f
                val centerCanvasY = h / 2f
                
                // 1. Holographic background pulsing glow
                val rad = frameRotationData.radius
                val currentRad = if (isPulsingEnabled) {
                    rad * spherePulseScale
                } else if (isAudioReactiveEnabled) {
                    rad * (1.0f + audioAmplitude * 0.40f)
                } else {
                    rad
                }
                
                val pulse = pulseScale
                val sizePx = currentRad * 1.6f * pulse * glowBrightness * (if (isAudioReactiveEnabled) 1.0f + audioAmplitude * 1.5f else 1.0f)
                
                val c1 = if (glowColor == GlowColorOption.SYSTEM) systemPrimary.toArgb() else glowColor.color1.toInt()
                val c2 = if (glowColor == GlowColorOption.SYSTEM) systemSecondary.toArgb() else glowColor.color2.toInt()
                
                val colorsHolo = intArrayOf(
                    c1,
                    c2,
                    0x00000000
                )
                val stopsHolo = floatArrayOf(0.0f, 0.5f, 1.0f)
                
                val shaderHolo = android.graphics.RadialGradient(
                    centerCanvasX,
                    centerCanvasY,
                    sizePx,
                    colorsHolo,
                    stopsHolo,
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvasGlowPaint.shader = shaderHolo
                val audioAlphaMultiplier = if (isAudioReactiveEnabled) 1.0f + audioAmplitude * 2.0f else 1.0f
                canvasGlowPaint.alpha = (0.24f * glowOpacity * audioAlphaMultiplier * 255).toInt().coerceIn(0, 255)
                nativeCanvas.drawCircle(centerCanvasX, centerCanvasY, sizePx, canvasGlowPaint)
                
                // 2. Trig cache
                val cosP = frameRotationData.cosP
                val sinP = frameRotationData.sinP
                val cosY = frameRotationData.cosY
                val sinY = frameRotationData.sinY
                
                // 3. Project 3D nodes
                val tempNodes = ArrayList<AppRenderNode>(sphereNodes.size)
                sphereNodes.forEach { node ->
                    val y1 = node.yBase * cosP - node.zBase * sinP
                    val z1 = node.yBase * sinP + node.zBase * cosP
                    
                    val x2 = node.xBase * cosY + z1 * sinY
                    val z2 = -node.xBase * sinY + z1 * cosY
                    
                    val cameraDist = 3.0f
                    val scale = cameraDist / (cameraDist + z2)
                    
                    val zoomFactor = currentRad / baseRadius
                    val finalScale = scale * zoomFactor
                    
                    val xProj = x2 * currentRad * scale
                    val yProj = y1 * currentRad * scale
                    
                    val depthRatio = (1.0f - z2) / 2.0f
                    
                    val calculatedAlpha = if (z2 > 0f) 0f
                    else if (z2 > -0.2f) (-z2 / 0.2f) * 0.95f
                    else 1.0f
                    
                    tempNodes.add(
                        AppRenderNode(
                            appInfo = node.appInfo,
                            xProj = xProj,
                            yProj = yProj,
                            finalScale = finalScale,
                            alpha = calculatedAlpha,
                            depthRatio = depthRatio,
                            zDepth = z2
                        )
                    )
                }
                
                // Cache node projected locations for tap detector
                lastProjectedNodes.clear()
                lastProjectedNodes.addAll(tempNodes)
                
                // 4. Sort back-to-front (descending zDepth)
                tempNodes.sortByDescending { it.zDepth }
                
                // 5. Draw
                val rectF = android.graphics.RectF()
                tempNodes.forEach { node ->
                    if (node.alpha > 0.01f) {
                        val cX = centerCanvasX + node.xProj
                        val cY = centerCanvasY + node.yProj
                        
                        val currentTileSize = tileSizePx * node.finalScale
                        val currentIconSize = iconSizePx * node.finalScale
                        val halfIcon = currentIconSize / 2f
                        
                        val paintAlpha = (node.alpha * 255).toInt().coerceIn(0, 255)
                        
                        // 5a. Shadow
                        if (node.depthRatio > 0.35f) {
                            val blurRadius = 14f * densityDp * node.depthRatio
                            val offsetY = 8f * densityDp * node.depthRatio
                            val shadowAlpha = (0.42f * node.depthRatio * node.alpha * 255).toInt().coerceIn(0, 255)
                            val shadowColor = (shadowAlpha shl 24) or 0x00000000
                            canvasShadowPaint.setShadowLayer(
                                blurRadius,
                                0f,
                                offsetY,
                                shadowColor
                            )
                            val shadowRadius = (currentTileSize / 2f) * 0.9f
                            nativeCanvas.drawCircle(cX, cY, shadowRadius, canvasShadowPaint)
                        }
                        
                        // 5b. Icon Bitmap (Pre-cropped!)
                        canvasIconPaint.alpha = paintAlpha
                        rectF.set(cX - halfIcon, cY - halfIcon, cX + halfIcon, cY + halfIcon)
                        val androidBitmap = node.appInfo.iconBitmap.asAndroidBitmap()
                        nativeCanvas.drawBitmap(androidBitmap, null, rectF, canvasIconPaint)
                        
                        // 5c. Border Outline
                        canvasBorderPaint.alpha = (0.25f * node.alpha * 255).toInt().coerceIn(0, 255)
                        nativeCanvas.drawCircle(cX, cY, halfIcon, canvasBorderPaint)
                        
                        // 5d. Specular dome glass shine
                        val shineRadius = currentIconSize * 0.75f
                        val gradCenterX = cX - halfIcon + currentIconSize * (0.35f - (node.xProj / rad) * 0.15f)
                        val gradCenterY = cY - halfIcon + currentIconSize * (0.35f - (node.yProj / rad) * 0.15f)
                        
                        val shineAlpha1 = (0.45f * node.alpha * 255).toInt().coerceIn(0, 255)
                        val shineAlpha2 = (0.08f * node.alpha * 255).toInt().coerceIn(0, 255)
                        val shineAlpha4 = (0.42f * node.alpha * 255).toInt().coerceIn(0, 255)
                        
                        val colorsShine = intArrayOf(
                            (shineAlpha1 shl 24) or 0x00FFFFFF,
                            (shineAlpha2 shl 24) or 0x00FFFFFF,
                            0x00FFFFFF,
                            (shineAlpha4 shl 24) or 0x00000000
                        )
                        val stopsShine = floatArrayOf(0.0f, 0.35f, 0.75f, 1.0f)
                        
                        val shaderShine = android.graphics.RadialGradient(
                            gradCenterX,
                            gradCenterY,
                            shineRadius,
                            colorsShine,
                            stopsShine,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        canvasGlossPaint.shader = shaderShine
                        nativeCanvas.drawCircle(cX, cY, halfIcon, canvasGlossPaint)
                    }
                }
            }
        } else {
            val finalRadiusProvider = {
                val base = frameRotationData.radius
                if (isPulsingEnabled) {
                    base * spherePulseScale
                } else if (isAudioReactiveEnabled) {
                    base * (1.0f + audioAmplitude * 0.40f)
                } else {
                    base
                }
            }
            // Render Snake mode using standard Composable layers (only active inside SNAKE mode, 0 lag!)
            if (shapeType == ShapeType.SOLID_SPHERE) {
                SolidSphereCore(
                    radiusProvider = finalRadiusProvider,
                    yawProvider = { rotationState.yaw },
                    pitchProvider = { rotationState.pitch },
                    frameTicketProvider = { frameTicket }
                )
            } else if (shapeType == ShapeType.POLYHEDRON) {
                MoonCore(
                    radiusProvider = finalRadiusProvider,
                    yawProvider = { rotationState.yaw },
                    pitchProvider = { rotationState.pitch },
                    frameTicketProvider = { frameTicket }
                )
            } else if (shapeType == ShapeType.FLAT_PLANE) {
                PlaneBoardCore(
                    radiusProvider = finalRadiusProvider,
                    yawProvider = { rotationState.yaw },
                    pitchProvider = { rotationState.pitch },
                    tiltYawProvider = { rotationState.tiltYaw },
                    tiltPitchProvider = { rotationState.tiltPitch },
                    rows = rows,
                    columns = columns,
                    frameTicketProvider = { frameTicket }
                )
            } else if (shapeType == ShapeType.SNAKE) {
                PlaneBoardCore(
                    radiusProvider = finalRadiusProvider,
                    yawProvider = { 0f },
                    pitchProvider = { 0f },
                    tiltYawProvider = { 0f },
                    tiltPitchProvider = { 0f },
                    rows = 12,
                    columns = 12,
                    frameTicketProvider = { frameTicket }
                )
            }

            // Draw each app node inside a static, non-recomposing layout!
            sphereNodes.forEach { node ->
                // Use Compose key() to ensure layout preservation and absolute rendering stability
                key(node.appInfo.packageName) {
                    val positionRef = remember { PositionRef() }
                    val depthRatioProvider = { positionRef.depthRatio }

                    // Calculate current grid position in boardPositions list for Flat Plane mode
                    val appIndex = remember(apps) { apps.indexOf(node.appInfo) }
                    val boardIndex = if (shapeType == ShapeType.FLAT_PLANE && appIndex != -1) {
                        boardPositions.indexOf(appIndex)
                    } else {
                        -1
                    }
                    
                    // Snake mode mappings
                    val isSnakeMode = shapeType == ShapeType.SNAKE
                    var isFood = false
                    var isSnakeSegment = false
                    var segmentIndex = -1
                    var cellX = 0
                    var cellY = 0
                    
                    if (isSnakeMode && appIndex != -1) {
                        val foodIndex = snakeBody.size % apps.size
                        if (appIndex == foodIndex) {
                            isFood = true
                            cellX = foodPos.first
                            cellY = foodPos.second
                        } else {
                            val matchingSegmentIndex = (0 until snakeBody.size).firstOrNull { it % apps.size == appIndex }
                            if (matchingSegmentIndex != null) {
                                isSnakeSegment = true
                                segmentIndex = matchingSegmentIndex
                                val cell = snakeBody[matchingSegmentIndex]
                                cellX = cell.first
                                cellY = cell.second
                            }
                        }
                    }
                    
                    val spacing = 0.42f
                    val snakeSpacing = 0.28f
                    val targetX = if (shapeType == ShapeType.FLAT_PLANE && boardIndex != -1) {
                        val col = boardIndex % columns
                        (col - (columns - 1) / 2f) * spacing
                    } else if (shapeType == ShapeType.SNAKE) {
                        val col = cellX
                        (col - (snakeGridSize - 1) / 2f) * snakeSpacing
                    } else {
                        node.xBase
                    }
                    
                    val targetY = if (shapeType == ShapeType.FLAT_PLANE && boardIndex != -1) {
                        val row = boardIndex / columns
                        ((rows - 1) / 2f - row) * spacing
                    } else if (shapeType == ShapeType.SNAKE) {
                        val row = cellY
                        ((snakeGridSize - 1) / 2f - row) * snakeSpacing
                    } else {
                        node.yBase
                    }
                    
                    // Animate x and y coordinates smoothly when they are rearranged!
                    val animX by animateFloatAsState(targetValue = targetX, label = "x")
                    val animY by animateFloatAsState(targetValue = targetY, label = "y")

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                // 1. Read frameTicket Compose State to register dependency and force Draw phase redraw on tick
                                val ticket = frameTicket

                                // 2. Read sines/cosines from precalculated frameRotationData cache (0 double precision calls!)
                                val cosP = frameRotationData.cosP
                                val sinP = frameRotationData.sinP
                                val cosY = frameRotationData.cosY
                                val sinY = frameRotationData.sinY
                                val rad = frameRotationData.radius

                                var finalX = if (shapeType == ShapeType.FLAT_PLANE) animX else node.xBase
                                var finalY = if (shapeType == ShapeType.FLAT_PLANE) animY else node.yBase
                                var finalZ = node.zBase

                                if (shapeType == ShapeType.FLAT_PLANE) {
                                    // Map yaw and pitch to horizontal/vertical panning offsets on the 2D plane
                                    val scrollSpeed = 0.45f
                                    finalX = animX - (rotationState.yaw + rotationState.tiltYaw) * scrollSpeed
                                    finalY = animY - (rotationState.pitch + rotationState.tiltPitch) * scrollSpeed
                                    finalZ = 0f
                                } else if (shapeType == ShapeType.SNAKE) {
                                    finalX = animX
                                    finalY = animY
                                    finalZ = 0f
                                }

                                // 3. Rotate around X-axis (pitch) using cached values
                                val y1 = if (shapeType == ShapeType.FLAT_PLANE || shapeType == ShapeType.SNAKE) finalY else (node.yBase * cosP - node.zBase * sinP)
                                val z1 = if (shapeType == ShapeType.FLAT_PLANE || shapeType == ShapeType.SNAKE) finalZ else (node.yBase * sinP + node.zBase * cosP)

                                // 4. Rotate around Y-axis (yaw)
                                val x2 = if (shapeType == ShapeType.FLAT_PLANE || shapeType == ShapeType.SNAKE) finalX else (node.xBase * cosY + z1 * sinY)
                                val z2 = if (shapeType == ShapeType.FLAT_PLANE || shapeType == ShapeType.SNAKE) finalZ else (-node.xBase * sinY + z1 * cosY) // depth

                                // 5. Perspective calculation
                                // Z ranges from -1 (closest) to +1 (furthest). We scale by radius when applying.
                                val cameraDist = 3.0f
                                val scale = cameraDist / (cameraDist + z2)

                                // Proportional scaling of icons based on zoom factor relative to baseRadius
                                val zoomFactor = rad / baseRadius
                                val finalScale = scale * zoomFactor

                                // Project 3D coordinates to 2D offsets in pixels directly
                                val xProj = x2 * rad * scale
                                val yProj = y1 * rad * scale

                                // Depth calculations for Alpha and Size
                                val depthRatio = (1.0f - z2) / 2.0f // 0.0 (furthest) to 1.0 (closest)
                                
                                // Write depth and position values to reference wrapper
                                positionRef.depthRatio = depthRatio
                                positionRef.x = x2
                                positionRef.y = y1

                                  val calculatedAlpha = if (shapeType == ShapeType.SOLID_SPHERE || shapeType == ShapeType.POLYHEDRON || shapeType == ShapeType.SPHERE) {
                                      // Completely hide items behind the solid planet/moon horizon (z2 > 0)
                                      // and smoothly fade out near the edge to prevent harsh pixel popping
                                      if (z2 > 0f) 0f
                                      else if (z2 > -0.2f) (-z2 / 0.2f) * 0.95f
                                      else 1.0f
                                  } else if (shapeType == ShapeType.FLAT_PLANE) {
                                      // Smooth fade-out near the physical screen edges
                                      val padX = 40f * density.density
                                      val padY = 40f * density.density
                                      val limitX = screenWidthPx / 2f - padX
                                      val limitY = screenHeightPx / 2f - padY
                                      
                                      val absXProj = kotlin.math.abs(xProj)
                                      val absYProj = kotlin.math.abs(yProj)
                                      
                                      val fadeX = if (absXProj > limitX) {
                                          ((screenWidthPx / 2f - absXProj) / padX).coerceIn(0f, 1f)
                                      } else 1f
                                      
                                      val fadeY = if (absYProj > limitY) {
                                          ((screenHeightPx / 2f - absYProj) / padY).coerceIn(0f, 1f)
                                      } else 1f
                                      
                                      fadeX * fadeY * 0.95f
                                  } else if (shapeType == ShapeType.SNAKE) {
                                      if (isFood || isSnakeSegment) 0.95f else 0.0f
                                  } else {
                                      0.15f + 0.85f * depthRatio // Standard fade out for back elements
                                  }

                                  scaleX = finalScale
                                  scaleY = finalScale
                                  translationX = xProj
                                  translationY = yProj
                                  alpha = calculatedAlpha

                                  // Highly optimized Z-depth sorting on GPU with zero shadow stencil overhead
                                  shadowElevation = if (shapeType == ShapeType.SOLID_SPHERE || shapeType == ShapeType.POLYHEDRON) {
                                      if (z2 < 0f) (1.0f + z2) * 5f else 0f
                                  } else if (shapeType == ShapeType.FLAT_PLANE) {
                                      3f // Elegant floating card shadow for the flat sheet
                                  } else if (shapeType == ShapeType.SNAKE) {
                                      if (isSnakeSegment && segmentIndex == 0) 5f else 2f
                                   } else {
                                       0f // Replaced depthRatio shadow to remove hardware-rendered ugly dark rings around holograms!
                                   }
                                
                                rotationX = 0f
                                rotationY = 0f
                            }
                            .combinedClickable(
                                onClick = {
                                    if (allowClicks && depthRatioProvider() > 0.35f) {
                                        if (shapeType == ShapeType.FLAT_PLANE) {
                                            if (appIndex != -1) {
                                                onTileTap(appIndex)
                                            }
                                        } else if (shapeType == ShapeType.SNAKE) {
                                            // Do nothing in Snake mode to prevent accidental launching during play
                                        } else {
                                            onAppClick(node.appInfo)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (allowClicks && depthRatioProvider() > 0.35f) {
                                        if (shapeType == ShapeType.FLAT_PLANE) {
                                            onAppClick(node.appInfo)
                                        }
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AppSphereItem(
                            app = node.appInfo,
                            shapeType = shapeType,
                            appCount = sphereNodes.size,
                            depthRatioProvider = depthRatioProvider,
                            xProvider = { positionRef.x },
                            yProvider = { positionRef.y },
                            isFood = isFood,
                            isSnakeSegment = isSnakeSegment,
                            segmentIndex = segmentIndex
                        )
                    }
                }
            }
        }
        
        if (shapeType == ShapeType.SNAKE) {
            // Floating cyber D-pad and score overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Score panel on the top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color(0xBB000000), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF00FF88), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Score: $score  |  High: $highScore",
                        color = Color(0xFF00FF88),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isGameOver) {
                    // Cyber D-Pad buttons layout
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        // Up Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0x3300FF88), CircleShape)
                                .border(1.5.dp, Color(0xFF00FF88), CircleShape)
                                .clickable {
                                    if (snakeDirection.second != 1) {
                                        snakeDirection = Pair(0, -1)
                                    }
                                }
                        ) {
                            Text("▲", color = Color(0xFF00FF88), fontSize = 18.sp)
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0x3300FF88), CircleShape)
                                    .border(1.5.dp, Color(0xFF00FF88), CircleShape)
                                    .clickable {
                                        if (snakeDirection.first != 1) {
                                            snakeDirection = Pair(-1, 0)
                                        }
                                    }
                            ) {
                                Text("◀", color = Color(0xFF00FF88), fontSize = 18.sp)
                            }
                            
                            // Center gap spacer
                            Spacer(modifier = Modifier.size(40.dp))
                            
                            // Right Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0x3300FF88), CircleShape)
                                    .border(1.5.dp, Color(0xFF00FF88), CircleShape)
                                    .clickable {
                                        if (snakeDirection.first != -1) {
                                            snakeDirection = Pair(1, 0)
                                        }
                                    }
                            ) {
                                Text("▶", color = Color(0xFF00FF88), fontSize = 18.sp)
                            }
                        }
                        
                        // Down Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0x3300FF88), CircleShape)
                                .border(1.5.dp, Color(0xFF00FF88), CircleShape)
                                .clickable {
                                    if (snakeDirection.second != -1) {
                                        snakeDirection = Pair(0, 1)
                                    }
                                }
                        ) {
                            Text("▼", color = Color(0xFF00FF88), fontSize = 18.sp)
                        }
                    }
                } else {
                    // Game Over Screen Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color(0xEE0D0D14), RoundedCornerShape(20.dp))
                            .border(2.dp, Color(0xFF00FF88), RoundedCornerShape(20.dp))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "ИГРА ОКОНЧЕНА",
                                color = Color(0xFFFF5252),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ваш счет: $score\nРекорд: $highScore",
                                color = Color.White,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .background(Color(0xFF00FF88), RoundedCornerShape(12.dp))
                                    .clickable { restartSnakeGame() }
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Начать заново",
                                    color = Color(0xFF0F0E1E),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HolographicCore(
    radiusProvider: () -> Float,
    frameTicketProvider: () -> Int
) {
    val density = LocalDensity.current.density
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val glowPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                frameTicketProvider()
                alpha = 0.08f
            }
            .drawBehind {
                val rad = radiusProvider()
                val pulse = pulseScale
                val sizePx = rad * 1.6f * pulse
                
                val colors = intArrayOf(
                    0xFF00F2FE.toInt(),
                    0xFF4FACFE.toInt(),
                    0x00000000
                )
                val stops = floatArrayOf(0.0f, 0.5f, 1.0f)
                
                val shader = android.graphics.RadialGradient(
                    center.x,
                    center.y,
                    sizePx,
                    colors,
                    stops,
                    android.graphics.Shader.TileMode.CLAMP
                )
                glowPaint.shader = shader
                
                drawContext.canvas.nativeCanvas.drawCircle(
                    center.x,
                    center.y,
                    sizePx,
                    glowPaint
                )
            }
    )
}

@Composable
fun SolidSphereCore(
    radiusProvider: () -> Float,
    yawProvider: () -> Float,
    pitchProvider: () -> Float,
    frameTicketProvider: () -> Int
) {
    val density = LocalDensity.current.density
    
    Box(
        modifier = Modifier
            .size(250.dp) // Base size
            .graphicsLayer {
                frameTicketProvider() // Read frame ticket to redraw/rescale
                val rad = radiusProvider()
                // Make the opaque sphere slightly smaller than the outer icon radius (e.g. rad * 1.9f)
                // so the icons sit perfectly stuck "on" the surface!
                val baseSizePx = 250f * density
                val targetSizePx = rad * 1.9f
                scaleX = targetSizePx / baseSizePx
                scaleY = targetSizePx / baseSizePx
                alpha = 1.0f
            }
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2E2C48), // Highlight lighted spot
                        Color(0xFF0F0E1E), // Matte space violet body
                        Color(0xFF020106)  // Shadow boundary
                    ),
                    center = androidx.compose.ui.geometry.Offset(
                        // Shift the light spot slightly based on current yaw/pitch rotation to give light-reflection volume
                        x = 250f * density * (0.35f - yawProvider() * 0.08f),
                        y = 250f * density * (0.35f - pitchProvider() * 0.08f)
                    ),
                    radius = 250f * density * 0.85f
                ),
                shape = CircleShape
            )
    )
}

@Composable
fun MoonCore(
    radiusProvider: () -> Float,
    yawProvider: () -> Float,
    pitchProvider: () -> Float,
    frameTicketProvider: () -> Int
) {
    val density = LocalDensity.current.density
    
    Box(
        modifier = Modifier
            .size(250.dp) // Base size
            .graphicsLayer {
                frameTicketProvider() // Read frame ticket to redraw/rescale
                val rad = radiusProvider()
                val baseSizePx = 250f * density
                // Moon core size matches the solid sphere size
                val targetSizePx = rad * 1.9f
                scaleX = targetSizePx / baseSizePx
                scaleY = targetSizePx / baseSizePx
                alpha = 1.0f
            }
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE8E8E8), // Lunar sunlight spot
                        Color(0xFF9E9E9E), // Standard lunar gray surface
                        Color(0xFF424242), // Lunar terminator/shadow
                        Color(0xFF0D0D0D)  // Deep space shadow
                    ),
                    center = androidx.compose.ui.geometry.Offset(
                        x = 250f * density * (0.32f - yawProvider() * 0.08f),
                        y = 250f * density * (0.32f - pitchProvider() * 0.08f)
                    ),
                    radius = 250f * density * 0.85f
                ),
                shape = CircleShape
            )
    ) {
        // Draw a few soft organic lunar maria (dark seas) to give it a realistic moon texture
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x55303030), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(80f * density, 70f * density),
                        radius = 65f * density
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x442C2C2C), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(160f * density, 110f * density),
                        radius = 50f * density
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x44242424), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(100f * density, 160f * density),
                        radius = 45f * density
                    )
                )
        )
    }
}

@Composable
fun PlaneBoardCore(
    radiusProvider: () -> Float,
    yawProvider: () -> Float,
    pitchProvider: () -> Float,
    tiltYawProvider: () -> Float,
    tiltPitchProvider: () -> Float,
    rows: Int,
    columns: Int,
    frameTicketProvider: () -> Int
) {
    val density = LocalDensity.current.density
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                frameTicketProvider()
                alpha = 0.95f
            }
            .drawBehind {
                val rad = radiusProvider()
                val scrollSpeed = 0.45f
                val offsetX = -(yawProvider() + tiltYawProvider()) * scrollSpeed * rad
                val offsetY = -(pitchProvider() + tiltPitchProvider()) * scrollSpeed * rad
                
                val isSnake = (rows == 12 && columns == 12)
                val spacing = if (isSnake) 0.28f else 0.42f
                val boardWidth = columns * spacing * rad
                val boardHeight = rows * spacing * rad
                
                val left = center.x - boardWidth / 2f + offsetX
                val top = center.y - boardHeight / 2f + offsetY
                val right = left + boardWidth
                val bottom = top + boardHeight
                
                // 1. Draw a futuristic semi-transparent cyber background plate (frosted board)
                drawRoundRect(
                    color = if (isSnake) Color(0x0A00FF88) else Color(0x1100E5FF),
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(boardWidth, boardHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f * density, 16f * density)
                )
                
                // 2. Draw outer border frame using beautiful cyber-neon gradient
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = if (isSnake) listOf(Color(0xFF00FF88), Color(0xFF00FFCC)) else listOf(Color(0xFF00E5FF), Color(0xFF4FACFE))
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(boardWidth, boardHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f * density, 16f * density),
                    style = Stroke(width = 2.5f * density)
                )
                
                // 3. Draw sub-grid separator lines for each cell inside the puzzle board
                for (c in 1 until columns) {
                    val xCell = left + c * spacing * rad
                    drawLine(
                        color = if (isSnake) Color(0x1800FF88) else Color(0x2200E5FF),
                        start = androidx.compose.ui.geometry.Offset(xCell, top),
                        end = androidx.compose.ui.geometry.Offset(xCell, bottom),
                        strokeWidth = 1.2f * density
                    )
                }
                for (r in 1 until rows) {
                    val yCell = top + r * spacing * rad
                    drawLine(
                        color = if (isSnake) Color(0x1800FF88) else Color(0x2200E5FF),
                        start = androidx.compose.ui.geometry.Offset(left, yCell),
                        end = androidx.compose.ui.geometry.Offset(right, yCell),
                        strokeWidth = 1.2f * density
                    )
                }
            }
    )
}

@Composable
fun AppSphereItem(
    app: AppInfo,
    shapeType: ShapeType,
    appCount: Int,
    depthRatioProvider: () -> Float,
    xProvider: () -> Float = { 0f },
    yProvider: () -> Float = { 0f },
    isFood: Boolean = false,
    isSnakeSegment: Boolean = false,
    segmentIndex: Int = -1
) {
    val density = LocalDensity.current.density
    
    val shadowPaint = remember {
        android.graphics.Paint().apply {
            color = 0x01000000 // Almost transparent black to force setShadowLayer
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }
    }
    
    val glossPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }
    }
    
    // Dynamically calculate tile, icon and text sizing based on the number of apps
    // to ensure perfectly proportioned layout with absolutely 0 overlaps!
    val tileSize = remember(appCount, shapeType) {
        if (shapeType == ShapeType.SNAKE) 28f
        else (400f / kotlin.math.sqrt(appCount.toFloat())).coerceIn(45f, 80f)
    }
    val iconSize = remember(tileSize, shapeType) {
        if (shapeType == ShapeType.POLYHEDRON) tileSize * 0.45f // sitting inside the crater bowl!
        else if (shapeType == ShapeType.SNAKE) tileSize * 0.75f
        else tileSize * 0.70f
    }
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(if (shapeType == ShapeType.SNAKE) 1.dp else 6.dp)
            .then(
                if (shapeType == ShapeType.POLYHEDRON) {
                    Modifier
                        .size(tileSize.dp)
                        .drawBehind {
                            val radius = size.width.coerceAtMost(size.height) / 2f * 0.95f
                            
                            // 1. Draw the dark concave crater bowl
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF141414), // Deep darkest shadow inside crater center
                                        Color(0xFF2C2C2C), // Gray inside walls
                                        Color(0xFF4C4C4C)  // Lighter gray near the rim
                                    ),
                                    center = center,
                                    radius = radius
                                )
                            )
                            
                            // 2. Draw a skeuomorphic raised rim (crater lip) using a dual light-shadow stroke
                            drawCircle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFE0E0E0), // Sunlit rim (top-left)
                                        Color(0xFF5A5A5A)  // Shadowed rim (bottom-right)
                                    ),
                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(size.width, size.height)
                                ),
                                radius = radius,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                } else if (shapeType == ShapeType.SOLID_SPHERE) {
                    // Realistic flat die-cut sticker look with solid vinyl background and silver outline
                    Modifier
                        .size(tileSize.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFFBFBFD), Color(0xFFECEFF1))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.5.dp,
                            color = Color(0xFFCFD8DC),
                            shape = RoundedCornerShape(12.dp)
                        )
                } else if (shapeType == ShapeType.FLAT_PLANE) {
                    // Translucent cyber-neon flat card
                    Modifier
                        .size(tileSize.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0x1FFFFFFF), Color(0x0CFFFFFF))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0x8000E5FF), Color(0x334FACFE))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                } else if (shapeType == ShapeType.SNAKE) {
                    if (isFood) {
                        Modifier
                            .size(tileSize.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0x2DFFE259), Color(0x11FFE259))
                                ),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFFFB300), Color(0xFFFF5252))
                                ),
                                shape = RoundedCornerShape(6.dp)
                            )
                    } else if (segmentIndex == 0) {
                        // Head
                        Modifier
                            .size(tileSize.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0x5D00FF88), Color(0x3200FF88))
                                ),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 2.dp,
                                color = Color(0xFF00FF88),
                                shape = RoundedCornerShape(6.dp)
                            )
                    } else {
                        // Body segment
                        Modifier
                            .size(tileSize.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0x2D00FF88), Color(0x0C00FF88))
                                ),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.2.dp,
                                color = Color(0xAA00FF88),
                                shape = RoundedCornerShape(6.dp)
                            )
                    }
                } else {
                    Modifier
                        .size(tileSize.dp)
                        .drawBehind {
                            val depth = depthRatioProvider()
                            if (depth > 0.35f) {
                                val nativeCanvas = drawContext.canvas.nativeCanvas
                                val blurRadius = 14f * density * depth
                                val offsetY = 8f * density * depth
                                val shadowColor = Color.Black.copy(alpha = 0.42f * depth).toArgb()
                                shadowPaint.setShadowLayer(
                                    blurRadius,
                                    0f,
                                    offsetY,
                                    shadowColor
                                )
                                val shadowRadius = (size.width / 2f) * 0.9f
                                nativeCanvas.drawCircle(
                                    center.x,
                                    center.y,
                                    shadowRadius,
                                    shadowPaint
                                )
                            }
                        }
                }
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                bitmap = app.iconBitmap,
                contentDescription = app.label,
                modifier = Modifier
                    .size(iconSize.dp)
                    .then(
                        if (shapeType != ShapeType.SOLID_SPHERE && shapeType != ShapeType.POLYHEDRON && shapeType != ShapeType.FLAT_PLANE && shapeType != ShapeType.SNAKE) {
                            Modifier
                                .clip(CircleShape)
                                .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .drawWithContent {
                                    drawContent()
                                    val x = xProvider()
                                    val y = yProvider()
                                    val w = size.width
                                    val h = size.height
                                    val radius = w.coerceAtMost(h) * 0.75f
                                    
                                    val centerX = w * (0.35f - x * 0.15f)
                                    val centerY = h * (0.35f - y * 0.15f)
                                    
                                    val colors = intArrayOf(
                                        0x73FFFFFF, // Color.White.copy(alpha = 0.45f).toArgb()
                                        0x14FFFFFF, // Color.White.copy(alpha = 0.08f).toArgb()
                                        0x00FFFFFF, // Color.Transparent.toArgb()
                                        0x6B000000  // Color.Black.copy(alpha = 0.42f).toArgb()
                                    )
                                    val stops = floatArrayOf(0.0f, 0.35f, 0.75f, 1.0f)
                                    
                                    val shader = android.graphics.RadialGradient(
                                        centerX,
                                        centerY,
                                        radius,
                                        colors,
                                        stops,
                                        android.graphics.Shader.TileMode.CLAMP
                                    )
                                    glossPaint.shader = shader
                                    
                                    drawContext.canvas.nativeCanvas.drawCircle(
                                        w / 2f,
                                        h / 2f,
                                        w / 2f,
                                        glossPaint
                                    )
                                }
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Fit
            )
        }
    }
}
