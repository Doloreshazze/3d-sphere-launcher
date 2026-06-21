package com.playeverywhere.spherelauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.playeverywhere.spherelauncher.theme.SphereLauncherTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    hideSystemUI()

    setContent {
      SphereLauncherTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }

    ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
      val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
      if (!isImeVisible) {
        hideSystemUI()
      }
      ViewCompat.onApplyWindowInsets(v, insets)
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      hideSystemUI()
    }
  }

  private fun hideSystemUI() {
    try {
        // Immersive fullscreen: hide system navigation bars entirely (swipe to show transiently)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // Light status bar icons (contrasty white icons on dark background)
        windowInsetsController.isAppearanceLightStatusBars = false
    } catch (e: Exception) {
        e.printStackTrace()
    }
  }
}
