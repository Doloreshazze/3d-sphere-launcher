package com.playeverywhere.spherelauncher.data

import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val iconBitmap: ImageBitmap
)
