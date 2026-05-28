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

enum class GlowColorOption(val color1: Long, val color2: Long, val label: String, val previewColor: Long) {
    SYSTEM(0, 0, "Система", 0xFF808080),
    CYAN(0xFF00F2FE, 0xFF4FACFE, "Голубой", 0xFF00F2FE),
    PURPLE(0xFFF355FF, 0xFF8E25FF, "Сиреневый", 0xFFF355FF),
    GREEN(0xFF00FF88, 0xFF00FFCC, "Зеленый", 0xFF00FF88),
    ORANGE(0xFFFF5E3A, 0xFFFF2A68, "Красный", 0xFFFF5E3A),
    GOLD(0xFFFFE259, 0xFFFFA751, "Золотой", 0xFFFFE259)
}

data class MainUiState(
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val style: SphereStyle = SphereStyle.FLOATING_ICONS,
    val isAutoDriftEnabled: Boolean = true,
    val isTiltEnabled: Boolean = false,
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
    val isFirstLaunch: Boolean = true
)

data class SettingsState(
    val style: SphereStyle,
    val isAutoDriftEnabled: Boolean,
    val isTiltEnabled: Boolean,
    val shapeType: ShapeType
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val appLoader = AppLoader(application)
    private val prefs = application.getSharedPreferences("sphere_launcher_prefs", android.content.Context.MODE_PRIVATE)
    private val isFirstLaunchState = MutableStateFlow(prefs.getBoolean("first_launch", true))

    private val appsState = MutableStateFlow<List<AppInfo>>(emptyList())
    private val styleState = MutableStateFlow(SphereStyle.FLOATING_ICONS)
    private val autoDriftState = MutableStateFlow(true)
    private val tiltEnabledState = MutableStateFlow(false)
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

    private val settingsFlow = combine(
        styleState,
        autoDriftState,
        tiltEnabledState,
        shapeTypeState
    ) { style, autoDrift, tiltEnabled, shapeType ->
        SettingsState(style, autoDrift, tiltEnabled, shapeType)
    }

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
        isFirstLaunchState
    ) { array ->
        @Suppress("UNCHECKED_CAST")
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

        val filtered = if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
        }
        MainUiState(
            apps = apps,
            filteredApps = filtered,
            style = settings.style,
            isAutoDriftEnabled = settings.isAutoDriftEnabled,
            isTiltEnabled = settings.isTiltEnabled,
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
            isFirstLaunch = isFirstLaunch
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MainUiState()
    )

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            loadingState.value = true
            errorState.value = null
            try {
                val loadedApps = appLoader.loadInstalledApps()
                appsState.value = loadedApps
            } catch (e: Exception) {
                e.printStackTrace()
                errorState.value = "Failed to load apps: ${e.message}"
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

    fun setTiltEnabled(enabled: Boolean) {
        tiltEnabledState.value = enabled
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
    private var audioRecord: android.media.AudioRecord? = null
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
            
            // Tier 1: Try Global System-wide Visualizer first (captures YouTube/Spotify directly)
            try {
                val maxResolution = android.media.audiofx.Visualizer.getCaptureSizeRange()[1]
                val vis = android.media.audiofx.Visualizer(0).apply {
                    captureSize = maxResolution
                }
                
                vis.setDataCaptureListener(object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: android.media.audiofx.Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (waveform == null || !isAudioReactiveEnabledState.value) return
                        
                        // Compute amplitude (RMS) from waveform bytes (unsigned 8-bit PCM offset by 128)
                        var sum = 0.0
                        for (i in 0 until waveform.size) {
                            val v = (waveform[i].toInt() and 0xFF) - 128
                            sum += v * v
                        }
                        val rms = kotlin.math.sqrt(sum / waveform.size)
                        
                        // Scale and normalize 8-bit RMS (max possible RMS is 128)
                        val normalized = ((rms - 1.5) / 54.0).coerceIn(0.0, 1.0).toFloat()
                        
                        // Low-pass exponential filter
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
            } catch (e: Exception) {
                android.util.Log.w("SphereViewModel", "Visualizer(0) failed, falling back to AudioRecord: ${e.message}")
                stopVisualizerInternal()
            }
            
            // Tier 2: Fallback to Microphone recorder
            val sampleRate = 16000
            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            if (bufferSize == android.media.AudioRecord.ERROR || bufferSize == android.media.AudioRecord.ERROR_BAD_VALUE) {
                return@launch
            }
            
            try {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        getApplication(),
                        android.Manifest.permission.RECORD_AUDIO
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    isAudioReactiveEnabledState.value = false
                    return@launch
                }

                val record = android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize * 2
                )
                
                if (record.state != android.media.AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    return@launch
                }
                
                audioRecord = record
                record.startRecording()
                
                val audioBuffer = ShortArray(bufferSize / 2)
                
                while (isAudioReactiveEnabledState.value) {
                    val readResult = record.read(audioBuffer, 0, audioBuffer.size)
                    if (readResult > 0) {
                        var sum = 0.0
                        for (i in 0 until readResult) {
                            val sample = audioBuffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = kotlin.math.sqrt(sum / readResult)
                        
                        val normalized = ((rms - 150.0) / 4500.0).coerceIn(0.0, 1.0).toFloat()
                        smoothedAmp = smoothedAmp * 0.75f + normalized * 0.25f
                        audioAmplitudeState.value = smoothedAmp
                    }
                    kotlinx.coroutines.delay(16L)
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                isAudioReactiveEnabledState.value = false
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopAudioRecordingInternal()
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
            e.printStackTrace()
        }
        visualizer = null
    }

    private fun stopAudioRecordingInternal() {
        try {
            audioRecord?.apply {
                if (recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
        audioAmplitudeState.value = 0f
    }
    
    private fun stopAudioRecording() {
        audioJob?.cancel()
        audioJob = null
        stopVisualizerInternal()
        stopAudioRecordingInternal()
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioRecording()
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean("first_launch", false).apply()
        isFirstLaunchState.value = false
    }

    fun showOnboarding() {
        isFirstLaunchState.value = true
    }
}
