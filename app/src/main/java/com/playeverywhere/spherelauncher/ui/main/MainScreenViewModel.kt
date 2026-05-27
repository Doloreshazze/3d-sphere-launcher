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
    SNAKE
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
    val glowBrightness: Float = 1.0f
)

data class SettingsState(
    val style: SphereStyle,
    val isAutoDriftEnabled: Boolean,
    val isTiltEnabled: Boolean,
    val shapeType: ShapeType
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val appLoader = AppLoader(application)

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
        glowBrightnessState
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
            glowBrightness = glowBrightness
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
}
