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
                                        text = "Поиск приложений...",
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
                            Text("Повторить", color = Color.Black)
                        }
                    }
                } else if (state.filteredApps.isEmpty()) {
                    Text(
                        text = "Приложения не найдены",
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
                                        Toast.makeText(context, "Не удалось запустить ${app.label}", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            onAppClick = { app ->
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launchIntent)
                                    } else {
                                        Toast.makeText(context, "Не удалось запустить ${app.label}", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAppLongClick = { selectedAppForAction = it }
                        )
                    }
                }
            }
        }

        if (!state.isStandardView) {
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
                                Toast.makeText(context, "Камера недоступна: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        contentDescription = "Настройки",
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
                        contentDescription = "Инерция вращения",
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
                        contentDescription = "Блокировка вращения",
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
                        Toast.makeText(context, "Разрешение на доступ к микрофону отклонено", Toast.LENGTH_SHORT).show()
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
                        text = "Выберите действие для приложения. При скрытии иконка исчезнет с лаунчера, но приложение останется установленным. При удалении приложение будет полностью удалено с устройства.",
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
                            Text("Скрыть иконку", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DELETE).apply {
                                        data = android.net.Uri.parse("package:${app.packageName}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Не удалось открыть удаление: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            Text("Удалить приложение", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        TextButton(
                            onClick = { selectedAppForAction = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Отмена", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                        }
                    }
                }
            )
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
            text = "Настройки 3D Сферы",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00F2FE),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 3D Shape Selector
        Text(
            text = "Форма 3D-пространства:",
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
                Triple(ShapeType.SPHERE, "Сфера", Color(0xFF00F2FE)),
                Triple(ShapeType.SNAKE, "Змейка", Color(0xFF00FF88))
            )
            shapes.forEach { (shapeOption, label, colorAccent) ->
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
                        text = label,
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
                    text = "Авто-вращение сферы",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Медленный автоматический дрейф икон",
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
                    text = "Наклон устройства (3D-эффект)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Вращение и наклон сферы гироскопом",
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
                    text = "Пульсация 3D-пространства",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Иконки ритмично приближаются и отдаляются",
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
                    text = "Аудио-реактивность",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Сфера пульсирует в такт музыке и звукам (микрофон)",
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
                    text = "Кнопка HOME по умолчанию",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Привязать лаунчер к системной кнопке Home",
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
                                Toast.makeText(context, "Не удалось открыть настройки: ${e3.message}", Toast.LENGTH_LONG).show()
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
                Text("Привязать", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                text = "Настройка свечения ореола",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00F2FE),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Horizontal color dot selector
            Text(
                text = "Цвет неона:",
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
                    text = "Интенсивность неона:",
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
                    text = "Размер свечения:",
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
            Text("Показать инструкцию пользователя", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                Text("Восстановить скрытые приложения (${state.hiddenAppsCount})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                Text("Обновить", fontSize = 13.sp)
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
                Text("Закрыть", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
    val totalSlides = 3

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
                        1 -> {
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
                        2 -> {
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
                    "3D Сфера приложений",
                    "Быстрый доступ",
                    "Музыкальный бит"
                )
                Text(
                    text = titles[currentSlide],
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Description
                val descriptions = listOf(
                    "Добро пожаловать! Ваши приложения парят в трехмерном пространстве. Вращайте сферу свайпами в любом направлении и запускайте их в одно касание.",
                    "В левом нижнем углу замораживайте вращение сферы кнопкой «Замок» или управляйте инерцией. В правом нижнем углу мгновенно открывайте Камеру одной кнопкой!",
                    "Включите режим «Аудио-реактивность» в настройках: сфера и неоновый туман начнут динамично раздуваться и вспыхивать в такт музыке на YouTube или вашему голосу."
                )
                Text(
                    text = descriptions[currentSlide],
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(80.dp)
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
                            text = "Пропустить",
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
                            text = if (currentSlide == totalSlides - 1) "Начать" else "Далее",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
