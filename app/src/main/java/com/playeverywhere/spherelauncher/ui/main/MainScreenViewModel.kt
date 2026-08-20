package com.playeverywhere.spherelauncher.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playeverywhere.spherelauncher.data.AppInfo
import com.playeverywhere.spherelauncher.data.AppLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import com.playeverywhere.spherelauncher.R

enum class SphereStyle {
    FLOATING_ICONS
}

enum class ShapeType {
    SPHERE,
    POLYHEDRON,
    SOLID_SPHERE,
    FLAT_PLANE,
    SNAKE,
    CUBE,
    PYRAMID
}

val ShapeType.isGame: Boolean get() = this == ShapeType.SNAKE

enum class GlowColorOption(val color1: Long, val color2: Long, val labelResId: Int, val previewColor: Long) {
    SYSTEM(0, 0, R.string.color_system, 0xFF808080),
    CYAN(0xFF00F2FE, 0xFF4FACFE, R.string.color_cyan, 0xFF00F2FE),
    PURPLE(0xFFF355FF, 0xFF8E25FF, R.string.color_purple, 0xFFF355FF),
    GREEN(0xFF00FF88, 0xFF00FFCC, R.string.color_green, 0xFF00FF88),
    ORANGE(0xFFFF5E3A, 0xFFFF2A68, R.string.color_red, 0xFFFF5E3A),
    GOLD(0xFFFFE259, 0xFFFFA751, R.string.color_gold, 0xFFFFE259)
}

data class MainUiState(
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val style: SphereStyle = SphereStyle.FLOATING_ICONS,
    val isAutoDriftEnabled: Boolean = true,
    val shapeType: ShapeType = ShapeType.SPHERE,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isStandardView: Boolean = false,
    val isShapeLocked: Boolean = false,
    val isInertiaEnabled: Boolean = true,
    val glowColor: GlowColorOption = GlowColorOption.SYSTEM,
    val glowOpacity: Float = 1.0f,
    val glowBrightness: Float = 1.0f,
    val isPulsingEnabled: Boolean = false,
    val isAudioReactiveEnabled: Boolean = false,
    val audioAmplitude: Float = 0.0f,
    val isFirstLaunch: Boolean = true,
    val hiddenAppsCount: Int = 0,
    val isGestureControlEnabled: Boolean = false,
    val handCursorX: Float = 0.5f,
    val handCursorY: Float = 0.5f,
    val isHandDetected: Boolean = false,
    val handScale: Float = 0.5f,
    val appleSize: Float = 0f,
    val handCursorSpeed: Float = 0f,
    val hoverProgress: Float = 0f,
    val focusedApp: AppInfo? = null,
    val reductionCoefficient: Float = 0.75f,
    val isEarthInsideEnabled: Boolean = false,
    val isRealisticEarthEnabled: Boolean = false,
    val isBlackHoleEnabled: Boolean = false,
    val isBlackHoleSideEnabled: Boolean = false,
    val isZoomEnabled: Boolean = false,
    val isHandOverlayEnabled: Boolean = true,
    val isStarfieldEnabled: Boolean = true,
    val isCameraInsideEnabled: Boolean = false,
    val cameraLensFacing: Int = 1,
    val isSettingsOpaque: Boolean = true,
    val launchCounts: Map<String, Int> = emptyMap()
)

data class SettingsState(
    val style: SphereStyle,
    val isAutoDriftEnabled: Boolean,
    val shapeType: ShapeType
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val appLoader = AppLoader(application)
    private val prefs = application.getSharedPreferences("sphere_launcher_prefs", android.content.Context.MODE_PRIVATE)
    private val isFirstLaunchState = MutableStateFlow(prefs.getBoolean("first_launch", true))
    private val hiddenPackagesState = MutableStateFlow<Set<String>>(
        prefs.getStringSet("hidden_packages", emptySet()) ?: emptySet()
    )

    private val appsState = MutableStateFlow<List<AppInfo>>(emptyList())
    private val styleState = MutableStateFlow(SphereStyle.FLOATING_ICONS)
    private val autoDriftState = MutableStateFlow(true)
    private val shapeTypeState = MutableStateFlow(ShapeType.SPHERE)
    private val searchQueryState = MutableStateFlow("")
    private val loadingState = MutableStateFlow(true)
    private val errorState = MutableStateFlow<String?>(null)
    private val isStandardViewState = MutableStateFlow(false)
    private val isShapeLockedState = MutableStateFlow(false)
    private val isInertiaEnabledState = MutableStateFlow(true)
    private val glowColorState = MutableStateFlow(GlowColorOption.SYSTEM)
    private val glowOpacityState = MutableStateFlow(1.0f)
    private val glowBrightnessState = MutableStateFlow(1.0f)
    private val isPulsingEnabledState = MutableStateFlow(false)
    private val isAudioReactiveEnabledState = MutableStateFlow(false)
    private val audioAmplitudeState = MutableStateFlow(0.0f)
    
    // Gesture tracking state flows
    private val gestureControlEnabledState = MutableStateFlow(prefs.getBoolean("gesture_control_enabled", false))
    private val handCursorXState = MutableStateFlow(0.5f)
    private val handCursorYState = MutableStateFlow(0.5f)
    private val isHandDetectedState = MutableStateFlow(false)
    private val handScaleState = MutableStateFlow(0.5f)
    private val appleSizeState = MutableStateFlow(0f)
    private val handCursorSpeedState = MutableStateFlow(0f)
    private val hoverProgressState = MutableStateFlow(0f)
    private val focusedAppState = MutableStateFlow<AppInfo?>(null)

    private val isEarthInsideEnabledState = MutableStateFlow(prefs.getBoolean("earth_inside_enabled", false))
    private val isRealisticEarthEnabledState = MutableStateFlow(prefs.getBoolean("realistic_earth_enabled", false))
    private val isBlackHoleEnabledState = MutableStateFlow(prefs.getBoolean("black_hole_enabled", false))
    private val isBlackHoleSideEnabledState = MutableStateFlow(prefs.getBoolean("black_hole_side_enabled", false))
    private val isZoomEnabledState = MutableStateFlow(prefs.getBoolean("zoom_enabled", true))
    private val isHandOverlayEnabledState = MutableStateFlow(prefs.getBoolean("hand_overlay_enabled", true))
    private val isStarfieldEnabledState = MutableStateFlow(prefs.getBoolean("starfield_enabled", true))
    private val isCameraInsideEnabledState = MutableStateFlow(prefs.getBoolean("camera_inside_enabled", false))
    private val cameraLensFacingState = MutableStateFlow(prefs.getInt("camera_lens_facing", 1))
    private val isSettingsOpaqueState = MutableStateFlow(prefs.getBoolean("settings_opaque", true))
    private fun loadLaunchCounts(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key.startsWith("launch_cnt_") && value is Int) {
                map[key.removePrefix("launch_cnt_")] = value
            }
        }
        return map
    }

    private val launchCountsState = MutableStateFlow<Map<String, Int>>(loadLaunchCounts())

    private val settingsFlow = combine(
        styleState,
        autoDriftState,
        shapeTypeState
    ) { style, autoDrift, shapeType ->
        SettingsState(style, autoDrift, shapeType)
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<MainUiState> = combine(
        appsState,
        settingsFlow,
        searchQueryState,
        loadingState,
        errorState,
        isStandardViewState,
        isShapeLockedState,
        isInertiaEnabledState,
        glowColorState,
        glowOpacityState,
        glowBrightnessState,
        isPulsingEnabledState,
        isAudioReactiveEnabledState,
        audioAmplitudeState,
        isFirstLaunchState,
        hiddenPackagesState,
        gestureControlEnabledState,
        handCursorXState,
        handCursorYState,
        isHandDetectedState,
        handScaleState,
        appleSizeState,
        handCursorSpeedState,
        hoverProgressState,
        focusedAppState,
        isEarthInsideEnabledState,
        isRealisticEarthEnabledState,
        isBlackHoleEnabledState,
        isBlackHoleSideEnabledState,
        isZoomEnabledState,
        isHandOverlayEnabledState,
        isStarfieldEnabledState,
        isCameraInsideEnabledState,
        cameraLensFacingState,
        isSettingsOpaqueState,
        launchCountsState
    ) { array ->
        val apps = array[0] as List<AppInfo>
        val settings = array[1] as SettingsState
        val query = array[2] as String
        val loading = array[3] as Boolean
        val error = array[4] as String?
        val isStandard = array[5] as Boolean
        val isShapeLocked = array[6] as Boolean
        val isInertiaEnabled = array[7] as Boolean
        val glowColor = array[8] as GlowColorOption
        val glowOpacity = array[9] as Float
        val glowBrightness = array[10] as Float
        val isPulsingEnabled = array[11] as Boolean
        val isAudioReactiveEnabled = array[12] as Boolean
        val audioAmplitude = array[13] as Float
        val isFirstLaunch = array[14] as Boolean
        val hiddenPackages = array[15] as Set<String>
        val isGestureEnabled = array[16] as Boolean
        val handCursorX = array[17] as Float
        val handCursorY = array[18] as Float
        val isHandDetected = array[19] as Boolean
        val handScale = array[20] as Float
        val appleSize = array[21] as Float
        val handSpeed = array[22] as Float
        val hoverProgress = array[23] as Float
        val focusedApp = array[24] as AppInfo?
        val isEarthInside = array[25] as Boolean
        val isRealisticEarth = array[26] as Boolean
        val isBlackHole = array[27] as Boolean
        val isBlackHoleSide = array[28] as Boolean
        val isZoomEnabled = array[29] as Boolean
        val isHandOverlayEnabled = array[30] as Boolean
        val isStarfieldEnabled = array[31] as Boolean
        val isCameraInsideEnabled = array[32] as Boolean
        val cameraLensFacing = array[33] as Int
        val isSettingsOpaque = array[34] as Boolean
        @Suppress("UNCHECKED_CAST")
        val launchCounts = array[35] as Map<String, Int>

        val visibleApps = apps.filter { it.packageName !in hiddenPackages }
        val filtered = if (query.isBlank()) {
            visibleApps
        } else {
            visibleApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        MainUiState(
            apps = visibleApps,
            filteredApps = filtered,
            style = settings.style,
            isAutoDriftEnabled = settings.isAutoDriftEnabled,
            shapeType = settings.shapeType,
            searchQuery = query,
            isLoading = loading,
            errorMessage = error,
            isStandardView = isStandard,
            isShapeLocked = isShapeLocked,
            isInertiaEnabled = isInertiaEnabled,
            glowColor = glowColor,
            glowOpacity = glowOpacity,
            glowBrightness = glowBrightness,
            isPulsingEnabled = isPulsingEnabled,
            isAudioReactiveEnabled = isAudioReactiveEnabled,
            audioAmplitude = audioAmplitude,
            isFirstLaunch = isFirstLaunch,
            hiddenAppsCount = hiddenPackages.size,
            isGestureControlEnabled = isGestureEnabled,
            handCursorX = handCursorX,
            handCursorY = handCursorY,
            isHandDetected = isHandDetected,
            handScale = handScale,
            appleSize = appleSize,
            handCursorSpeed = handSpeed,
            hoverProgress = hoverProgress,
            focusedApp = focusedApp,
            reductionCoefficient = prefs.getFloat("reduction_coefficient", 0.75f),
            isEarthInsideEnabled = isEarthInside,
            isRealisticEarthEnabled = isRealisticEarth,
            isBlackHoleEnabled = isBlackHole,
            isBlackHoleSideEnabled = isBlackHoleSide,
            isZoomEnabled = isZoomEnabled,
            isHandOverlayEnabled = isHandOverlayEnabled,
            isStarfieldEnabled = isStarfieldEnabled,
            isCameraInsideEnabled = isCameraInsideEnabled,
            cameraLensFacing = cameraLensFacing,
            isSettingsOpaque = isSettingsOpaque,
            launchCounts = launchCounts
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MainUiState()
    )

    private val packageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            loadApps()
        }
    }

    init {
        loadApps()
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
                addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
                addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                application.registerReceiver(packageReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                application.registerReceiver(packageReceiver, filter)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainScreenViewModel", "Failed to register package receiver", e)
        }
    }

    private var loadAppsJob: kotlinx.coroutines.Job? = null

    fun loadApps() {
        if (loadAppsJob?.isActive == true) return
        
        loadAppsJob = viewModelScope.launch {
            if (appsState.value.isEmpty()) {
                val cachedApps = appLoader.loadCachedApps()
                if (cachedApps != null && cachedApps.isNotEmpty()) {
                    appsState.value = cachedApps
                } else {
                    loadingState.value = true
                }
            }
            errorState.value = null
            try {
                val loadedApps = appLoader.loadInstalledApps()
                appsState.value = loadedApps
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MainScreenViewModel", "Failed to load installed apps", e)
                if (appsState.value.isEmpty()) {
                    errorState.value = getApplication<Application>().getString(R.string.load_apps_failed)
                }
            } finally {
                loadingState.value = false
            }
        }
    }

    fun setStyle(style: SphereStyle) {
        styleState.value = style
    }

    fun setAutoDrift(enabled: Boolean) {
        autoDriftState.value = enabled
    }

    fun setShapeType(shapeType: ShapeType) {
        shapeTypeState.value = shapeType
    }

    fun setSearchQuery(query: String) {
        searchQueryState.value = query
    }

    fun setStandardView(enabled: Boolean) {
        isStandardViewState.value = enabled
    }

    fun setShapeLocked(locked: Boolean) {
        isShapeLockedState.value = locked
    }

    fun setInertiaEnabled(enabled: Boolean) {
        isInertiaEnabledState.value = enabled
    }

    fun setGlowColor(color: GlowColorOption) {
        glowColorState.value = color
    }

    fun setGlowOpacity(opacity: Float) {
        glowOpacityState.value = opacity
    }

    fun setGlowBrightness(brightness: Float) {
        glowBrightnessState.value = brightness
    }

    fun setPulsingEnabled(enabled: Boolean) {
        isPulsingEnabledState.value = enabled
    }

    // --- LOW-LATENCY DUAL-MODE AUDIO VISUALIZER ENGINE ---
    private var visualizer: android.media.audiofx.Visualizer? = null
    private var audioJob: kotlinx.coroutines.Job? = null

    fun setAudioReactiveEnabled(enabled: Boolean) {
        isAudioReactiveEnabledState.value = enabled
        if (enabled) {
            startAudioRecording()
        } else {
            stopAudioRecording()
        }
    }

    private fun startAudioRecording() {
        if (audioJob != null) return // Already running
        
        audioJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var smoothedAmp = 0f
            
            // On Android 12 (API 31) and higher, Visualizer(0) can cause native crashes (SIGSEGV).
            // So we only try Visualizer(0) on older versions, and use AudioRecord on newer.
            if (android.os.Build.VERSION.SDK_INT < 31) {
                // Tier 1: Try Global System-wide Visualizer
                try {
                    val maxResolution = android.media.audiofx.Visualizer.getCaptureSizeRange()[1]
                    val vis = android.media.audiofx.Visualizer(0).apply {
                        captureSize = maxResolution
                    }
                    
                    vis.setDataCaptureListener(object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(visualizer: android.media.audiofx.Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                            if (waveform == null || !isAudioReactiveEnabledState.value) return
                            
                            var sum = 0.0
                            for (i in 0 until waveform.size) {
                                val v = (waveform[i].toInt() and 0xFF) - 128
                                sum += v * v
                            }
                            val rms = kotlin.math.sqrt(sum / waveform.size)
                            val normalized = ((rms - 1.5) / 54.0).coerceIn(0.0, 1.0).toFloat()
                            
                            smoothedAmp = smoothedAmp * 0.72f + normalized * 0.28f
                            audioAmplitudeState.value = smoothedAmp
                        }

                        override fun onFftDataCapture(visualizer: android.media.audiofx.Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                    }, android.media.audiofx.Visualizer.getMaxCaptureRate() / 2, true, false)
                    
                    visualizer = vis
                    vis.enabled = true
                    
                    while (isAudioReactiveEnabledState.value) {
                        kotlinx.coroutines.delay(100L)
                    }
                    return@launch // Success!
                } catch (e: kotlinx.coroutines.CancellationException) {
                    stopVisualizerInternal()
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("MainScreenViewModel", "System audio visualizer failed; falling back to microphone", e)
                    stopVisualizerInternal()
                }
            }

            // Tier 2: Fallback to AudioRecord for Android 12+ or if Visualizer failed
            var audioRecord: android.media.AudioRecord? = null
            try {
                val sampleRate = 44100
                val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
                val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                
                if (androidx.core.app.ActivityCompat.checkSelfPermission(
                        getApplication(),
                        android.Manifest.permission.RECORD_AUDIO
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    isAudioReactiveEnabledState.value = false
                    return@launch
                }
                
                audioRecord = android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                
                if (audioRecord.state == android.media.AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording()
                    val buffer = ShortArray(bufferSize)
                    
                    while (isAudioReactiveEnabledState.value && isActive) {
                        val readResult = audioRecord.read(buffer, 0, buffer.size)
                        if (readResult > 0) {
                            var sum = 0.0
                            for (i in 0 until readResult) {
                                val v = buffer[i].toDouble()
                                sum += v * v
                            }
                            val rms = kotlin.math.sqrt(sum / readResult)
                            
                            // Normalize 16-bit PCM RMS (max 32768)
                            val normalized = (rms / 8000.0).coerceIn(0.0, 1.0).toFloat()
                            smoothedAmp = smoothedAmp * 0.72f + normalized * 0.28f
                            audioAmplitudeState.value = smoothedAmp
                        } else {
                            kotlinx.coroutines.delay(16L)
                        }
                    }
                } else {
                    isAudioReactiveEnabledState.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MainScreenViewModel", "Microphone audio capture failed", e)
                isAudioReactiveEnabledState.value = false
            } finally {
                audioRecord?.let { recorder ->
                    try {
                        if (recorder.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                            recorder.stop()
                        }
                    } catch (e: IllegalStateException) {
                        android.util.Log.w("MainScreenViewModel", "AudioRecord could not be stopped cleanly", e)
                    }
                    try {
                        recorder.release()
                    } catch (e: Exception) {
                        android.util.Log.w("MainScreenViewModel", "AudioRecord could not be released cleanly", e)
                    }
                }
            }
        }
    }
    
    private fun stopVisualizerInternal() {
        try {
            visualizer?.apply {
                enabled = false
                release()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainScreenViewModel", "Failed to stop visualizer", e)
        }
        visualizer = null
    }

    private fun stopAudioRecording() {
        audioJob?.cancel()
        audioJob = null
        stopVisualizerInternal()
        audioAmplitudeState.value = 0f
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioRecording()
        try {
            getApplication<Application>().unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            android.util.Log.e("MainScreenViewModel", "Failed to unregister package receiver", e)
        }
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean("first_launch", false).apply()
        isFirstLaunchState.value = false
    }

    fun showOnboarding() {
        isFirstLaunchState.value = true
    }

    fun hideApp(packageName: String) {
        val current = hiddenPackagesState.value.toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet("hidden_packages", current).apply()
        hiddenPackagesState.value = current
    }

    fun unhideAllApps() {
        prefs.edit().putStringSet("hidden_packages", emptySet()).apply()
        hiddenPackagesState.value = emptySet()
    }

    fun resetSettings() {
        prefs.edit().clear().apply()
        styleState.value = SphereStyle.FLOATING_ICONS
        autoDriftState.value = true
        shapeTypeState.value = ShapeType.SPHERE
        isStandardViewState.value = false
        isShapeLockedState.value = false
        isInertiaEnabledState.value = true
        glowColorState.value = GlowColorOption.SYSTEM
        glowOpacityState.value = 1.0f
        glowBrightnessState.value = 1.0f
        isPulsingEnabledState.value = false
        isAudioReactiveEnabledState.value = false
        audioAmplitudeState.value = 0.0f
        gestureControlEnabledState.value = false
        hiddenPackagesState.value = emptySet()
        isEarthInsideEnabledState.value = false
        isRealisticEarthEnabledState.value = false
        isBlackHoleEnabledState.value = false
        isBlackHoleSideEnabledState.value = false
        isCameraInsideEnabledState.value = false
        isZoomEnabledState.value = true
        isStarfieldEnabledState.value = true
        stopAudioRecording()
    }

    fun setStarfieldEnabled(enabled: Boolean) {
        isStarfieldEnabledState.value = enabled
        prefs.edit().putBoolean("starfield_enabled", enabled).apply()
    }

    fun setSettingsOpaque(opaque: Boolean) {
        isSettingsOpaqueState.value = opaque
        prefs.edit().putBoolean("settings_opaque", opaque).apply()
    }

    fun setEarthInsideEnabled(enabled: Boolean) {
        isEarthInsideEnabledState.value = enabled
        prefs.edit().putBoolean("earth_inside_enabled", enabled).apply()
        if (enabled) {
            setRealisticEarthEnabled(false)
            setBlackHoleEnabled(false)
            setBlackHoleSideEnabled(false)
            setCameraInsideEnabled(false)
        }
    }

    fun setRealisticEarthEnabled(enabled: Boolean) {
        isRealisticEarthEnabledState.value = enabled
        prefs.edit().putBoolean("realistic_earth_enabled", enabled).apply()
        if (enabled) {
            setEarthInsideEnabled(false)
            setBlackHoleEnabled(false)
            setBlackHoleSideEnabled(false)
            setCameraInsideEnabled(false)
        }
    }

    fun setBlackHoleEnabled(enabled: Boolean) {
        isBlackHoleEnabledState.value = enabled
        prefs.edit().putBoolean("black_hole_enabled", enabled).apply()
        if (enabled) {
            setEarthInsideEnabled(false)
            setRealisticEarthEnabled(false)
            setBlackHoleSideEnabled(false)
            setCameraInsideEnabled(false)
        }
    }

    fun setBlackHoleSideEnabled(enabled: Boolean) {
        isBlackHoleSideEnabledState.value = enabled
        prefs.edit().putBoolean("black_hole_side_enabled", enabled).apply()
        if (enabled) {
            setEarthInsideEnabled(false)
            setRealisticEarthEnabled(false)
            setBlackHoleEnabled(false)
            setCameraInsideEnabled(false)
        }
    }

    fun onAppLaunched(packageName: String) {
        val currentCount = prefs.getInt("launch_cnt_$packageName", 0)
        val newCount = currentCount + 1

        prefs.edit()
            .putInt("launch_cnt_$packageName", newCount)
            .apply()

        val updatedMap = launchCountsState.value.toMutableMap()
        updatedMap[packageName] = newCount
        launchCountsState.value = updatedMap
    }

    fun setZoomEnabled(enabled: Boolean) {
        isZoomEnabledState.value = enabled
        prefs.edit().putBoolean("zoom_enabled", enabled).apply()
    }

    fun setHandOverlayEnabled(enabled: Boolean) {
        isHandOverlayEnabledState.value = enabled
        prefs.edit().putBoolean("hand_overlay_enabled", enabled).apply()
    }

    fun setGestureControlEnabled(enabled: Boolean) {
        gestureControlEnabledState.value = enabled
        prefs.edit().putBoolean("gesture_control_enabled", enabled).apply()
    }

    fun updateHandCursor(x: Float, y: Float, isDetected: Boolean, scale: Float = 0.5f, speed: Float = 0f, appleSize: Float = 0f) {
        handCursorXState.value = x
        handCursorYState.value = y
        isHandDetectedState.value = isDetected
        handScaleState.value = scale
        handCursorSpeedState.value = speed
        appleSizeState.value = appleSize
    }

    fun updateHoverProgress(progress: Float) {
        hoverProgressState.value = progress
    }

    fun setFocusedApp(app: AppInfo?) {
        focusedAppState.value = app
    }

    fun setCameraInsideEnabled(enabled: Boolean) {
        isCameraInsideEnabledState.value = enabled
        prefs.edit().putBoolean("camera_inside_enabled", enabled).apply()
        // If camera is enabled, disable other background modes
        if (enabled) {
            setEarthInsideEnabled(false)
            setRealisticEarthEnabled(false)
            setBlackHoleEnabled(false)
            setBlackHoleSideEnabled(false)
        }
    }

    fun setCameraLensFacing(facing: Int) {
        cameraLensFacingState.value = facing
        prefs.edit().putInt("camera_lens_facing", facing).apply()
    }
}
