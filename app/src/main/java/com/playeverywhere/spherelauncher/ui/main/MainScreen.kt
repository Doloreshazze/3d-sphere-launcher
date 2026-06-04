package com.playeverywhere.spherelauncher.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.text.style.TextOverflow
import com.playeverywhere.spherelauncher.data.AppInfo
import androidx.compose.ui.res.stringResource
import com.playeverywhere.spherelauncher.R
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.antigravity.gesture.HandGestureDetector
import com.antigravity.gesture.Gesture
import androidx.compose.ui.platform.LocalView
import android.view.MotionEvent
import android.os.SystemClock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var selectedAppForAction by remember { mutableStateOf<AppInfo?>(null) }

    // Gesture tracking variables
    val projectedNodesList = remember { ArrayList<AppRenderNode>() }
    var surfaceProvider by remember { mutableStateOf<androidx.camera.core.Preview.SurfaceProvider?>(null) }
    var previewViewInstance by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }

    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setGestureControlEnabled(true)
        } else {
            Toast.makeText(context, context.getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    // Proactively request camera permission on startup if gesture control is enabled
    LaunchedEffect(state.isGestureControlEnabled) {
        if (state.isGestureControlEnabled) {
            val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasCameraPermission) {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }
    val densityDp = density.density

    // Initialize the HandGestureDetector
    val gestureDetector = remember { HandGestureDetector(context.applicationContext) }
    val landmarks by gestureDetector.landmarksFlow.collectAsStateWithLifecycle()
    val activeGesture by gestureDetector.gestureFlow.collectAsStateWithLifecycle()
    val isHandClenched = activeGesture == Gesture.ACTIVATE

    // Background gesture camera engine launch
    GestureCameraLauncher(
        gestureDetector = gestureDetector,
        isEnabled = state.isGestureControlEnabled,
        previewSurfaceProvider = surfaceProvider
    )

    // Sync hand cursor position and presence to the ViewModel using landmarks
    LaunchedEffect(landmarks) {
        val lms = landmarks
        if (lms != null) {
            val wrist = lms[0]
            val indexMcp = lms[5]
            val pinkyMcp = lms[17]
            val palmCenterX = (wrist.x + indexMcp.x + pinkyMcp.x) / 3f
            val palmCenterY = (wrist.y + indexMcp.y + pinkyMcp.y) / 3f
            // MediaPipe coordinates are already rotated and mirrored horizontally, so they map perfectly
            viewModel.updateHandCursor(palmCenterX, palmCenterY, true)
        } else {
            viewModel.updateHandCursor(0.5f, 0.5f, false)
        }
    }

    // Translate coordinates and their changes (deltas) to simulated native touch events on the main screen.
    val view = LocalView.current
    var wasClenched by remember { mutableStateOf(false) }
    var touchDownTime by remember { mutableStateOf(0L) }

    LaunchedEffect(state.handCursorX, state.handCursorY, isHandClenched, state.isHandDetected, state.isGestureControlEnabled) {
        if (!state.isGestureControlEnabled) {
            if (wasClenched) {
                val eventTime = SystemClock.uptimeMillis()
                val x = state.handCursorX * view.width
                val y = state.handCursorY * view.height
                val event = MotionEvent.obtain(touchDownTime, eventTime, MotionEvent.ACTION_UP, x, y, 0)
                view.dispatchTouchEvent(event)
                event.recycle()
                wasClenched = false
            }
            return@LaunchedEffect
        }

        if (!state.isHandDetected) {
            if (wasClenched) {
                // Hand lost while clenched -> release touch safely
                val eventTime = SystemClock.uptimeMillis()
                val x = state.handCursorX * view.width
                val y = state.handCursorY * view.height
                val event = MotionEvent.obtain(touchDownTime, eventTime, MotionEvent.ACTION_UP, x, y, 0)
                view.dispatchTouchEvent(event)
                event.recycle()
                wasClenched = false
            }
            return@LaunchedEffect
        }

        val x = state.handCursorX * view.width
        val y = state.handCursorY * view.height
        val now = SystemClock.uptimeMillis()

        if (isHandClenched) {
            if (!wasClenched) {
                // Active mode gesture start (pinch fingers together) -> dispatch ACTION_DOWN
                touchDownTime = now
                val event = MotionEvent.obtain(touchDownTime, now, MotionEvent.ACTION_DOWN, x, y, 0)
                view.dispatchTouchEvent(event)
                event.recycle()
                wasClenched = true
            } else {
                // Active mode gesture drag -> dispatch ACTION_MOVE
                val event = MotionEvent.obtain(touchDownTime, now, MotionEvent.ACTION_MOVE, x, y, 0)
                view.dispatchTouchEvent(event)
                event.recycle()
            }
        } else {
            if (wasClenched) {
                // Active mode gesture end (release fingers) -> dispatch ACTION_UP
                val event = MotionEvent.obtain(touchDownTime, now, MotionEvent.ACTION_UP, x, y, 0)
                view.dispatchTouchEvent(event)
                event.recycle()
                wasClenched = false
            }
        }
    }

    // Gesture-based app launching and hover focus have been removed per user request.
    // Gestures are now strictly used to rotate/control the 3D sphere.

    // Intercept back presses (a launcher should prevent closing on back button)
    BackHandler {
        // Do nothing, just stay on home screen
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C24), // Ultra deep space violet-blue
                        Color(0xFF07050E), // Galaxy center
                        Color(0xFF020104)  // Outer pitch black space
                    )
                )
            )
    ) {
        // Background decorative neon nebula glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x0C3E2723), // Warm reddish dust
                            Color(0x087B1FA2), // Purple nebula
                            Color.Transparent
                        )
                    )
                )
        )

        // Draw the semi-transparent hand overlay matching user movements
        if (state.isGestureControlEnabled) {
            SemiTransparentHandOverlay(
                landmarks = landmarks,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Holographic Floating Search Bar & Toggle Switch Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Bar taking up remaining horizontal space
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x22FFFFFF),
                                    Color(0x0AFFFFFF)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x3BFFFFFF),
                                    Color(0x12FFFFFF)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xB300F2FE),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            textStyle = LocalTextStyle.current.copy(
                                color = Color.White,
                                fontSize = 15.sp
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (state.searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_placeholder),
                                        color = Color(0x80FFFFFF),
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.setSearchQuery("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0x80FFFFFF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Beautiful glassmorphic 3D/2D visual switcher
                ViewTypeToggle(
                    isStandardView = state.isStandardView,
                    onToggle = { viewModel.setStandardView(it) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Main 3D Sphere Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF00F2FE),
                        strokeWidth = 3.dp
                    )
                } else if (state.errorMessage != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = state.errorMessage ?: "",
                            color = Color(0xFFFF5252),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.loadApps() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.retry_button), color = Color.Black)
                        }
                    }
                } else if (state.filteredApps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_apps_found),
                        color = Color(0x80FFFFFF),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    if (state.isStandardView) {
                        StandardAppGrid(
                            apps = state.filteredApps,
                            onAppClick = { app ->
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launchIntent)
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.fail_launch_app, app.label), Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.error_prefix, e.message ?: ""), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAppLongClick = { selectedAppForAction = it }
                        )
                    } else {
                        Sphere3D(
                            apps = state.filteredApps,
                            style = state.style,
                            isAutoDriftEnabled = state.isAutoDriftEnabled,
                            isTiltEnabled = state.isTiltEnabled,
                            shapeType = state.shapeType,
                            isShapeLocked = state.isShapeLocked,
                            isInertiaEnabled = state.isInertiaEnabled,
                            glowColor = state.glowColor,
                            glowOpacity = state.glowOpacity,
                            glowBrightness = state.glowBrightness,
                            isPulsingEnabled = state.isPulsingEnabled,
                            isAudioReactiveEnabled = state.isAudioReactiveEnabled,
                            audioAmplitude = state.audioAmplitude,
                            isGestureEnabled = state.isGestureControlEnabled,
                            handCursorX = state.handCursorX,
                            handCursorY = state.handCursorY,
                            isHandDetected = state.isHandDetected,
                            isHandClenched = isHandClenched,
                            projectedNodes = projectedNodesList,
                            onAppClick = { app ->
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launchIntent)
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.fail_launch_app, app.label), Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.error_prefix, e.message ?: ""), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAppLongClick = { selectedAppForAction = it }
                        )
                    }
                }
            }
        }

        if (!state.isStandardView) {
            // Floating Bottom-Center Home Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                // Exit to System Launcher Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF8E25FF), // Gorgeous violet
                                    Color(0xFFF355FF)  // to magenta
                                )
                            ),
                            shape = CircleShape
                        )
                        .border(
                            width = 2.5.dp,
                            color = Color.White.copy(alpha = 0.8f),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable {
                            try {
                                val pm = context.packageManager
                                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_HOME)
                                }
                                val resolveInfos = pm.queryIntentActivities(homeIntent, 0)
                                val otherLaunchers = resolveInfos.filter {
                                    it.activityInfo.packageName != context.packageName
                                }
                                val systemLauncher = otherLaunchers.find {
                                    (it.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                                } ?: otherLaunchers.firstOrNull()

                                if (systemLauncher != null) {
                                    val intent = Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_HOME)
                                        setClassName(systemLauncher.activityInfo.packageName, systemLauncher.activityInfo.name)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e2: Exception) {
                                    Toast.makeText(context, "Settings unavailable", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = stringResource(R.string.exit_to_system_launcher),
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // 5. Floating Bottom-Right Stack (Camera Quick Button + Settings Button)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Camera Quick Launch Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF55BB),
                                    Color(0xFFFF2A68)
                                )
                            ),
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable {
                            try {
                                val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.camera_unavailable, e.message ?: ""), Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(24.dp)) {
                        val tintColor = Color.Black
                        val strokeWidth = 2.dp.toPx()
                        
                        // Draw top bump
                        drawRect(
                            color = tintColor,
                            topLeft = androidx.compose.ui.geometry.Offset(size.width / 2f - 4.dp.toPx(), 2.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 3.dp.toPx())
                        )
                        // Draw camera body
                        drawRoundRect(
                            color = tintColor,
                            topLeft = androidx.compose.ui.geometry.Offset(2.dp.toPx(), 5.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(20.dp.toPx(), 14.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                        // Draw lens
                        drawCircle(
                            color = tintColor,
                            radius = 3.8f.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f + 1.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                        // Draw flash dot
                        drawCircle(
                            color = tintColor,
                            radius = 1.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(size.width - 5.dp.toPx(), 8.dp.toPx())
                        )
                    }
                }

                // Settings Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00F2FE),
                                    Color(0xFF4FACFE)
                                )
                            ),
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable { showSettings = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 6. Floating Cyber Action Stack (Bottom-Left)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Inertia Toggle Button
                val isInertia = state.isInertiaEnabled
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            brush = if (isInertia) {
                                Brush.linearGradient(colors = listOf(Color(0xFF00F2FE), Color(0xFF4FACFE)))
                            } else {
                                Brush.linearGradient(colors = listOf(Color(0x1AFFFFFF), Color(0x0AFFFFFF)))
                            },
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = if (isInertia) Color.White.copy(alpha = 0.6f) else Color(0x6600F2FE),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable { viewModel.setInertiaEnabled(!isInertia) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = stringResource(R.string.rotation_inertia),
                        tint = if (isInertia) Color.Black else Color(0xFF00F2FE),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Padlock Shape Lock Button
                val isLocked = state.isShapeLocked
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            brush = if (isLocked) {
                                Brush.linearGradient(colors = listOf(Color(0xFF00F2FE), Color(0xFF4FACFE)))
                            } else {
                                Brush.linearGradient(colors = listOf(Color(0x1AFFFFFF), Color(0x0AFFFFFF)))
                            },
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = if (isLocked) Color.White.copy(alpha = 0.6f) else Color(0x6600F2FE),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable { viewModel.setShapeLocked(!isLocked) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = stringResource(R.string.rotation_lock),
                        tint = if (isLocked) Color.Black else Color(0xFF00F2FE),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 5. Settings Bottom Sheet Dialog
        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                containerColor = Color(0xC007050C), // Beautiful space-themed dark glassmorphism (75% opacity)
                contentColor = Color.White,
                scrimColor = Color.Transparent, // COMPLETELY remove the dark background scrim so the 3D Sphere is fully visible in real-time!
                tonalElevation = 0.dp, // Disable Material 3 surface tint overlays to keep the translucent color pure
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .background(Color(0x33FFFFFF), CircleShape)
                    )
                }
            ) {
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.setAudioReactiveEnabled(true)
                    } else {
                        Toast.makeText(context, context.getString(R.string.mic_permission_denied), Toast.LENGTH_SHORT).show()
                    }
                }
                SettingsSheetContent(
                    state = state,
                    onAutoDriftChanged = { viewModel.setAutoDrift(it) },
                    onTiltChanged = { viewModel.setTiltEnabled(it) },
                    onShapeSelected = { viewModel.setShapeType(it) },
                    onGlowColorSelected = { viewModel.setGlowColor(it) },
                    onGlowOpacityChanged = { viewModel.setGlowOpacity(it) },
                    onGlowBrightnessChanged = { viewModel.setGlowBrightness(it) },
                    onPulsingChanged = { viewModel.setPulsingEnabled(it) },
                    onAudioReactiveChanged = { enabled ->
                        if (enabled) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.setAudioReactiveEnabled(true)
                            } else {
                                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            viewModel.setAudioReactiveEnabled(false)
                        }
                    },
                    onGestureControlChanged = { enabled ->
                        if (enabled) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.CAMERA
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.setGestureControlEnabled(true)
                            } else {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        } else {
                            viewModel.setGestureControlEnabled(false)
                        }
                    },
                    onRefreshApps = {
                        viewModel.loadApps()
                        showSettings = false
                    },
                    onClose = { showSettings = false },
                    onShowOnboarding = {
                        viewModel.showOnboarding()
                        showSettings = false
                    },
                    onUnhideAllApps = {
                        viewModel.unhideAllApps()
                        showSettings = false
                    }
                )
            }
        }

        if (state.isFirstLaunch) {
            OnboardingTour(
                onComplete = { viewModel.completeOnboarding() }
            )
        }

        if (selectedAppForAction != null) {
            val app = selectedAppForAction!!
            AlertDialog(
                onDismissRequest = { selectedAppForAction = null },
                containerColor = Color(0xE00D0B18), // Gorgeous glassmorphic deep space dark
                shape = RoundedCornerShape(20.dp),
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            text = app.label,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00F2FE),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = app.packageName,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                text = {
                    Text(
                        text = stringResource(R.string.dialog_choose_action),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.hideApp(app.packageName)
                                selectedAppForAction = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x33FFFFFF),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Text(stringResource(R.string.dialog_hide_icon), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DELETE).apply {
                                        data = android.net.Uri.parse("package:${app.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.uninstall_fail, e.message ?: ""), Toast.LENGTH_SHORT).show()
                                }
                                selectedAppForAction = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF5252).copy(alpha = 0.15f),
                                contentColor = Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
                        ) {
                            Text(stringResource(R.string.dialog_uninstall_app), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        TextButton(
                            onClick = { selectedAppForAction = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.dialog_cancel), color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                        }
                    }
                }
            )
        }

        // ---------------- AIR GESTURE CONTROL HUD & CURSOR OVERLAYS ----------------
        if (state.isGestureControlEnabled) {
            // 1. Cyber Floating camera preview (Top-Right HUD Bubble)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 90.dp, end = 20.dp)
                    .size(90.dp)
                    .background(Color(0x5507050C), CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = if (state.isHandDetected) listOf(Color(0xFF00FF88), Color(0xFF00FFCC))
                            else listOf(Color(0xFFFF55BB), Color(0xFFFF2A68))
                        ),
                        shape = CircleShape
                    )
                    .clip(CircleShape)
            ) {
                AndroidView(
                    factory = { ctx ->
                        androidx.camera.view.PreviewView(ctx).apply {
                            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                            implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                            previewViewInstance = this
                            surfaceProvider = this.surfaceProvider
                        }
                    },
                    update = { view ->
                        previewViewInstance = view
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Holographic Scanner Overlay Target Reticle
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val color = if (state.isHandDetected) Color(0x6000FF88) else Color(0x60FF55BB)
                    val strokeWidth = 1.dp.toPx()
                    // Draw horizontal and vertical scan crosshairs
                    drawLine(
                        color = color,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = strokeWidth
                    )
                    drawCircle(
                        color = color,
                        radius = 12.dp.toPx(),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }

            // Sleek glowing tracking indicator card
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 188.dp, end = 20.dp)
                    .background(Color(0xE007050C), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Small blinking status light
                    val infiniteTransition = rememberInfiniteTransition(label = "blink")
                    val alphaPulse by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "blinkAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = (if (state.isHandDetected) Color(0xFF00FF88) else Color(0xFFFF55BB)).copy(alpha = alphaPulse),
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = stringResource(if (state.isHandDetected) R.string.hand_detected else R.string.hand_searching),
                        color = if (state.isHandDetected) Color(0xFF00FF88) else Color(0xFFFF55BB),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // 2. Glowing Neon Hand Cursor
            if (state.isHandDetected) {
                val cursorColor = if (isHandClenched) Color(0xFFCC00FF) else Color(0xFF00F2FE)
                Box(
                    modifier = Modifier
                        .offset(
                            x = (state.handCursorX * screenWidthDp).dp - 24.dp,
                            y = (state.handCursorY * screenHeightDp).dp - 24.dp
                        )
                        .size(48.dp)
                        .background(Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Soft outer radial halo glow
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        cursorColor.copy(alpha = 0.4f),
                                        Color(0xFF8E25FF).copy(alpha = 0.1f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                    // Inner bright targeting ring
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .border(2.dp, cursorColor, CircleShape)
                            .background(Color.White.copy(alpha = 0.8f), CircleShape)
                    )

                    // Radial countdown progress circle (fills up on hover selection)
                    if (state.hoverProgress > 0f) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(36.dp)) {
                            drawArc(
                                color = Color(0xFF00FF88),
                                startAngle = -90f,
                                sweepAngle = state.hoverProgress * 360f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

// BasicTextField is a light implementation helper
@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    decorationBox: @Composable (@Composable () -> Unit) -> Unit
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        modifier = modifier,
        singleLine = singleLine,
        decorationBox = decorationBox
    )
}

@Composable
fun SettingsSheetContent(
    state: MainUiState,
    onAutoDriftChanged: (Boolean) -> Unit,
    onTiltChanged: (Boolean) -> Unit,
    onShapeSelected: (ShapeType) -> Unit,
    onGlowColorSelected: (GlowColorOption) -> Unit,
    onGlowOpacityChanged: (Float) -> Unit,
    onGlowBrightnessChanged: (Float) -> Unit,
    onPulsingChanged: (Boolean) -> Unit,
    onAudioReactiveChanged: (Boolean) -> Unit,
    onGestureControlChanged: (Boolean) -> Unit,
    onRefreshApps: () -> Unit,
    onClose: () -> Unit,
    onShowOnboarding: () -> Unit,
    onUnhideAllApps: () -> Unit
) {
    val systemPrimary = MaterialTheme.colorScheme.primary
    val systemSecondary = MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00F2FE),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 3D Shape Selector
        Text(
            text = stringResource(R.string.shape_selector_title),
            fontSize = 13.sp,
            color = Color(0x80FFFFFF),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val shapes = listOf(
                Triple(ShapeType.SPHERE, R.string.shape_sphere, Color(0xFF00F2FE)),
                Triple(ShapeType.SNAKE, R.string.shape_snake, Color(0xFF00FF88))
            )
            shapes.forEach { (shapeOption, labelRes, colorAccent) ->
                val isSelected = state.shapeType == shapeOption
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected) colorAccent.copy(alpha = 0.2f) else Color(0x0DFFFFFF),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) colorAccent else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onShapeSelected(shapeOption) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(labelRes),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else Color(0xB3FFFFFF)
                    )
                }
            }
        }

        // Toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.auto_drift_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.auto_drift_desc),
                    fontSize = 11.sp,
                    color = Color(0x66FFFFFF)
                )
            }
            Switch(
                checked = state.isAutoDriftEnabled,
                onCheckedChange = onAutoDriftChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00F2FE),
                    checkedTrackColor = Color(0xFF00F2FE).copy(alpha = 0.3f),
                    uncheckedThumbColor = Color(0xFF808080),
                    uncheckedTrackColor = Color(0x1Fffffff)
                )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.device_tilt_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.device_tilt_desc),
                    fontSize = 11.sp,
                    color = Color(0x66FFFFFF)
                )
            }
            Switch(
                checked = state.isTiltEnabled,
                onCheckedChange = onTiltChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00F2FE),
                    checkedTrackColor = Color(0xFF00F2FE).copy(alpha = 0.3f),
                    uncheckedThumbColor = Color(0xFF808080),
                    uncheckedTrackColor = Color(0x1Fffffff)
                )
            )
        }

        // --- BREEDING PULSATION ROW ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.pulsation_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.pulsation_desc),
                    fontSize = 11.sp,
                    color = Color(0x66FFFFFF)
                )
            }
            Switch(
                checked = state.isPulsingEnabled,
                onCheckedChange = onPulsingChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00F2FE),
                    checkedTrackColor = Color(0xFF00F2FE).copy(alpha = 0.3f),
                    uncheckedThumbColor = Color(0xFF808080),
                    uncheckedTrackColor = Color(0x1Fffffff)
                )
            )
        }

        // --- AUDIO REACTIVITY ROW ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.audio_reactivity),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.audio_reactive_desc),
                    fontSize = 11.sp,
                    color = Color(0x66FFFFFF)
                )
            }
            Switch(
                checked = state.isAudioReactiveEnabled,
                onCheckedChange = onAudioReactiveChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00F2FE),
                    checkedTrackColor = Color(0xFF00F2FE).copy(alpha = 0.3f),
                    uncheckedThumbColor = Color(0xFF808080),
                    uncheckedTrackColor = Color(0x1Fffffff)
                )
            )
        }

        // --- AIR GESTURE CONTROL ROW ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.gesture_control_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.gesture_control_desc),
                    fontSize = 11.sp,
                    color = Color(0x66FFFFFF)
                )
            }
            Switch(
                checked = state.isGestureControlEnabled,
                onCheckedChange = onGestureControlChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00F2FE),
                    checkedTrackColor = Color(0xFF00F2FE).copy(alpha = 0.3f),
                    uncheckedThumbColor = Color(0xFF808080),
                    uncheckedTrackColor = Color(0x1Fffffff)
                )
            )
        }

        // --- BIND TO HOME BUTTON ROW ---
        Spacer(modifier = Modifier.height(8.dp))
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bind_home_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.bind_home_desc),
                    fontSize = 11.sp,
                    color = Color(0x66FFFFFF)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    try {
                        // Direct, ultra-reliable way to open system Default Home settings screen on Samsung/all Androids
                        val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            // Fallback to manage default apps settings screen (API 24+)
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            try {
                                // Fallback to prompt Home chooser
                                val intent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_HOME)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e3: Exception) {
                                Toast.makeText(context, context.getString(R.string.uninstall_fail, e3.message ?: ""), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FF88).copy(alpha = 0.15f),
                    contentColor = Color(0xFF00FF88)
                ),
                border = BorderStroke(1.dp, Color(0xFF00FF88).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.bind_home_btn), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (state.shapeType == ShapeType.SPHERE) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x1FFFFFFF))
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.neon_glow_settings),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00F2FE),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Horizontal color dot selector
            Text(
                text = stringResource(R.string.neon_color),
                fontSize = 12.sp,
                color = Color(0x80FFFFFF),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlowColorOption.values().forEach { option ->
                    val isSelected = state.glowColor == option
                    
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color.White else Color(0x33FFFFFF),
                                shape = CircleShape
                            )
                            .padding(4.dp)
                            .background(Color.Transparent, CircleShape)
                            .clip(CircleShape)
                            .clickable { onGlowColorSelected(option) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = if (option == GlowColorOption.SYSTEM) {
                                            listOf(systemPrimary, systemSecondary)
                                        } else {
                                            listOf(Color(option.color1), Color(option.color2))
                                        }
                                    ),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }

            // Opacity slider (0% to 300%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.glow_intensity),
                    fontSize = 12.sp,
                    color = Color(0xB3FFFFFF)
                )
                Text(
                    text = "${(state.glowOpacity * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00F2FE)
                )
            }
            Slider(
                value = state.glowOpacity,
                onValueChange = onGlowOpacityChanged,
                valueRange = 0f..3f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00F2FE),
                    activeTrackColor = Color(0xFF00F2FE),
                    inactiveTrackColor = Color(0x1Fffffff)
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Brightness / Size slider (50% to 200%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.glow_size),
                    fontSize = 12.sp,
                    color = Color(0xB3FFFFFF)
                )
                Text(
                    text = "${(state.glowBrightness * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00F2FE)
                )
            }
            Slider(
                value = state.glowBrightness,
                onValueChange = onGlowBrightnessChanged,
                valueRange = 0.5f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00F2FE),
                    activeTrackColor = Color(0xFF00F2FE),
                    inactiveTrackColor = Color(0x1Fffffff)
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // User Guide Tour Button
        Button(
            onClick = onShowOnboarding,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF55BB).copy(alpha = 0.15f),
                contentColor = Color(0xFFFF55BB)
            ),
            border = BorderStroke(1.dp, Color(0xFFFF55BB).copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.show_manual), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        if (state.hiddenAppsCount > 0) {
            Button(
                onClick = onUnhideAllApps,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00F2FE).copy(alpha = 0.15f),
                    contentColor = Color(0xFF00F2FE)
                ),
                border = BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.restore_hidden_apps, state.hiddenAppsCount), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRefreshApps,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.refresh_button), fontSize = 13.sp)
            }

            Button(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00F2FE),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.close_button), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ViewTypeToggle(
    isStandardView: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0x12FFFFFF), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(24.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val activeBg = Brush.linearGradient(
            colors = listOf(
                Color(0xFF00F2FE),
                Color(0xFF4FACFE)
            )
        )
        // 3D option
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .let {
                    if (!isStandardView) it.background(brush = activeBg) else it
                }
                .clickable { onToggle(false) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "3D",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (!isStandardView) Color.Black else Color.White.copy(alpha = 0.7f)
            )
        }
        
        // 2D option
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .let {
                    if (isStandardView) it.background(brush = activeBg) else it
                }
                .clickable { onToggle(true) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "2D",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isStandardView) Color.Black else Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun StandardAppGrid(
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            StandardAppCard(
                app = app,
                onClick = { onAppClick(app) },
                onLongClick = { onAppLongClick(app) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StandardAppCard(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x12FFFFFF),
                        Color(0x04FFFFFF)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x24FFFFFF),
                        Color(0x06FFFFFF)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 12.dp, horizontal = 6.dp)
    ) {
        // App Icon Container with dynamic neon space glow
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x2200F2FE),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                bitmap = app.iconBitmap,
                contentDescription = app.label,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun OnboardingTour(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentSlide by remember { mutableStateOf(0) }
    val totalSlides = 4

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}, // Intercept clicks to background
        contentAlignment = Alignment.Center
    ) {
        // Main Glassmorphic Dialog Card
        Box(
            modifier = Modifier
                .width(320.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x2BFFFFFF),
                            Color(0x0FFFFFFF)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.8.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00F2FE),
                            Color(0xFFFF55BB)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Interactive Animated Visual for each slide
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color(0x1F000000), CircleShape)
                        .border(1.dp, Color(0x12FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (currentSlide) {
                        0 -> {
                            // Slide 0: Permission Disclosure Shield with dynamic green pulse
                            val infiniteTransition = rememberInfiniteTransition(label = "shield")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 0.85f,
                                targetValue = 1.15f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulse"
                            )
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(80.dp)) {
                                val cX = size.width / 2f
                                val cY = size.height / 2f
                                val width = size.width
                                val height = size.height
                                
                                // Glowing background pulse
                                drawCircle(
                                    color = Color(0xFF00FF88).copy(alpha = 0.15f * (2f - pulseScale)),
                                    radius = (width / 2f) * pulseScale
                                )
                                
                                // Shield path drawing
                                val shieldPath = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(cX, cY - 24.dp.toPx())
                                    quadraticTo(cX + 18.dp.toPx(), cY - 24.dp.toPx(), cX + 20.dp.toPx(), cY - 16.dp.toPx())
                                    lineTo(cX + 20.dp.toPx(), cY + 4.dp.toPx())
                                    quadraticTo(cX + 20.dp.toPx(), cY + 18.dp.toPx(), cX, cY + 24.dp.toPx())
                                    quadraticTo(cX - 20.dp.toPx(), cY + 18.dp.toPx(), cX - 20.dp.toPx(), cY + 4.dp.toPx())
                                    lineTo(cX - 20.dp.toPx(), cY - 16.dp.toPx())
                                    quadraticTo(cX - 18.dp.toPx(), cY - 24.dp.toPx(), cX, cY - 24.dp.toPx())
                                    close()
                                }
                                
                                drawPath(
                                    path = shieldPath,
                                    color = Color(0xFF00FF88).copy(alpha = 0.2f)
                                )
                                
                                drawPath(
                                    path = shieldPath,
                                    color = Color(0xFF00FF88),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                )
                                
                                // Inner keyhole
                                drawCircle(
                                    color = Color(0xFF00FF88),
                                    radius = 4.dp.toPx(),
                                    center = androidx.compose.ui.geometry.Offset(cX, cY - 2.dp.toPx())
                                )
                                drawRect(
                                    color = Color(0xFF00FF88),
                                    topLeft = androidx.compose.ui.geometry.Offset(cX - 2.dp.toPx(), cY + 2.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), 8.dp.toPx())
                                )
                            }
                        }
                        1 -> {
                            // Slide 1: Glowing 3D Wireframe Sphere
                            val infiniteTransition = rememberInfiniteTransition(label = "sphere")
                            val rotAngle by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(4000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "rot"
                            )
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(80.dp)) {
                                val cX = size.width / 2f
                                val cY = size.height / 2f
                                val rad = size.width / 2f
                                val strokeW = 1.5.dp.toPx()
                                
                                drawCircle(
                                    color = Color(0xFF00F2FE).copy(alpha = 0.3f),
                                    radius = rad,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                                )
                                drawCircle(
                                    color = Color(0xFF00F2FE).copy(alpha = 0.15f),
                                    radius = rad * 0.6f,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                                )
                                
                                // Rotating ellipse lines to simulate 3D rotation
                                val ellipseW = rad * kotlin.math.abs(kotlin.math.cos(Math.toRadians(rotAngle.toDouble()))).toFloat()
                                drawOval(
                                    color = Color(0xFF00F2FE).copy(alpha = 0.4f),
                                    topLeft = androidx.compose.ui.geometry.Offset(cX - ellipseW, 0f),
                                    size = androidx.compose.ui.geometry.Size(ellipseW * 2f, size.height),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                                )
                                val ellipseH = rad * kotlin.math.abs(kotlin.math.sin(Math.toRadians(rotAngle.toDouble()))).toFloat()
                                drawOval(
                                    color = Color(0xFF00F2FE).copy(alpha = 0.4f),
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, cY - ellipseH),
                                    size = androidx.compose.ui.geometry.Size(size.width, ellipseH * 2f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                                )
                            }
                        }
                        2 -> {
                            // Slide 2: Symmetrical buttons panel mock
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFF00F2FE).copy(alpha = 0.2f), CircleShape)
                                        .border(2.dp, Color(0xFF00F2FE), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00F2FE), modifier = Modifier.size(20.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFFFF55BB).copy(alpha = 0.2f), CircleShape)
                                        .border(2.dp, Color(0xFFFF55BB), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
                                        val tintColor = Color(0xFFFF55BB)
                                        val strokeWidth = 1.5.dp.toPx()
                                        drawRect(
                                            color = tintColor,
                                            topLeft = androidx.compose.ui.geometry.Offset(size.width / 2f - 3.dp.toPx(), 2.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 2.dp.toPx())
                                        )
                                        drawRoundRect(
                                            color = tintColor,
                                            topLeft = androidx.compose.ui.geometry.Offset(1.dp.toPx(), 4.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(18.dp.toPx(), 13.dp.toPx()),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                        )
                                        drawCircle(
                                            color = tintColor,
                                            radius = 3f.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f + 1.dp.toPx()),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                        )
                                    }
                                }
                            }
                        }
                        3 -> {
                            // Slide 3: Pulsing audio wave visualizer
                            val infiniteTransition = rememberInfiniteTransition(label = "audio")
                            val anim1 by infiniteTransition.animateFloat(initialValue = 0.2f, targetValue = 0.9f, animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse), label = "a1")
                            val anim2 by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 1.0f, animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse), label = "a2")
                            val anim3 by infiniteTransition.animateFloat(initialValue = 0.1f, targetValue = 0.8f, animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "a3")
                            val anim4 by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 0.95f, animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse), label = "a4")
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(60.dp)
                            ) {
                                val heights = listOf(anim1, anim2, anim3, anim4, anim1)
                                val colors = listOf(Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFFFF55BB), Color(0xFFFF2A68), Color(0xFF00FF88))
                                heights.forEachIndexed { i, scale ->
                                    Box(
                                        modifier = Modifier
                                            .width(5.dp)
                                            .fillMaxHeight(scale)
                                            .background(colors[i % colors.size], RoundedCornerShape(2.5.dp))
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Title
                val titles = listOf(
                    R.string.ob_title_permission,
                    R.string.ob_title_sphere,
                    R.string.ob_title_access,
                    R.string.ob_title_music
                )
                Text(
                    text = stringResource(titles[currentSlide]),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Description
                val descriptions = listOf(
                    R.string.ob_desc_permission,
                    R.string.ob_desc_sphere,
                    R.string.ob_desc_access,
                    R.string.ob_desc_music
                )
                Text(
                    text = stringResource(descriptions[currentSlide]),
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .heightIn(min = 80.dp)
                        .verticalScroll(rememberScrollState())
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Pager Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    repeat(totalSlides) { i ->
                        val isActive = i == currentSlide
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isActive) 18.dp else 6.dp)
                                .background(
                                    color = if (isActive) Color(0xFF00F2FE) else Color(0x33FFFFFF),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
                
                // Bottom control row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentSlide < totalSlides - 1) {
                        Text(
                            text = stringResource(R.string.ob_btn_skip),
                            color = Color(0x80FFFFFF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { onComplete() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(70.dp))
                    }
                    
                    Button(
                        onClick = {
                            if (currentSlide < totalSlides - 1) {
                                currentSlide++
                            } else {
                                onComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00F2FE),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (currentSlide == 0) {
                                stringResource(R.string.ob_btn_agree)
                            } else if (currentSlide == totalSlides - 1) {
                                stringResource(R.string.ob_btn_start)
                            } else {
                                stringResource(R.string.ob_btn_next)
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
