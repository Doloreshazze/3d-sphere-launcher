package com.example.spherelauncher.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.spherelauncher.data.AppInfo

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
                            }
                        )
                    } else {
                        Sphere3D(
                            apps = state.filteredApps,
                            style = state.style,
                            isAutoDriftEnabled = state.isAutoDriftEnabled,
                            isTiltEnabled = state.isTiltEnabled,
                            shapeType = state.shapeType,
                            isPerspectiveEnabled = state.isPerspectiveEnabled,
                            isShapeLocked = state.isShapeLocked,
                            isInertiaEnabled = state.isInertiaEnabled,
                            glowColor = state.glowColor,
                            glowOpacity = state.glowOpacity,
                            glowBrightness = state.glowBrightness,
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
                            }
                        )
                    }
                }
            }

            if (!state.isStandardView) {
                // 3. Floating Bottom Controls Indicator
                Box(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .background(Color(0x1F000000), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Используйте жесты: Свайп - Инерция, Щипок - Зум, Наклон - 3D-эффект",
                        fontSize = 10.sp,
                        color = Color(0x66FFFFFF),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (!state.isStandardView) {
            // 4. Floating Glowing Settings Button (Bottom-Right)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
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
                SettingsSheetContent(
                    state = state,
                    onAutoDriftChanged = { viewModel.setAutoDrift(it) },
                    onTiltChanged = { viewModel.setTiltEnabled(it) },
                    onShapeSelected = { viewModel.setShapeType(it) },
                    onPerspectiveChanged = { viewModel.setPerspectiveEnabled(it) },
                    onGlowColorSelected = { viewModel.setGlowColor(it) },
                    onGlowOpacityChanged = { viewModel.setGlowOpacity(it) },
                    onGlowBrightnessChanged = { viewModel.setGlowBrightness(it) },
                    onRefreshApps = {
                        viewModel.loadApps()
                        showSettings = false
                    },
                    onClose = { showSettings = false }
                )
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
    onPerspectiveChanged: (Boolean) -> Unit,
    onGlowColorSelected: (GlowColorOption) -> Unit,
    onGlowOpacityChanged: (Float) -> Unit,
    onGlowBrightnessChanged: (Float) -> Unit,
    onRefreshApps: () -> Unit,
    onClose: () -> Unit
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "3D-перспектива иконок",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Наклон иконок по плоскости 3D-фигуры",
                    fontSize = 11.sp,
                    color = Color(0x66FFFFFF)
                )
            }
            Switch(
                checked = state.isPerspectiveEnabled,
                onCheckedChange = onPerspectiveChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00F2FE),
                    checkedTrackColor = Color(0xFF00F2FE).copy(alpha = 0.3f),
                    uncheckedThumbColor = Color(0xFF808080),
                    uncheckedTrackColor = Color(0x1Fffffff)
                )
            )
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

        Spacer(modifier = Modifier.height(16.dp))

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
                onClick = { onAppClick(app) }
            )
        }
    }
}

@Composable
fun StandardAppCard(
    app: AppInfo,
    onClick: () -> Unit,
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
            .clickable { onClick() }
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
