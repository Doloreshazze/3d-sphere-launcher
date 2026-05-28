package com.playeverywhere.spherelauncher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.pm.LauncherApps
import android.os.Process

class AppLoader(private val context: Context) {
    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val user = Process.myUserHandle()
        val resolveInfos = launcherApps.getActivityList(null, user)
        resolveInfos.mapNotNull { resolveInfo ->
            try {
                val label = resolveInfo.label.toString()
                val packageName = resolveInfo.componentName.packageName
                val activityName = resolveInfo.componentName.className
                val rawIcon = resolveInfo.getIcon(0)
                val rawBitmap = drawableToBitmap(rawIcon)
                val circularBitmap = getCircularBitmap(rawBitmap)
                val iconBitmap = circularBitmap.asImageBitmap()
                AppInfo(label, packageName, activityName, iconBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }.sortedBy { it.label.lowercase() }
    }

    private fun getCircularBitmap(src: Bitmap): Bitmap {
        val size = src.width.coerceAtMost(src.height).coerceAtLeast(96)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }
        
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        paint.setXfermode(android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN))
        val srcRect = android.graphics.Rect(0, 0, src.width, src.height)
        val destRect = android.graphics.Rect(0, 0, size, size)
        canvas.drawBitmap(src, srcRect, destRect, paint)
        
        return output
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        // Handle legacy and adaptive icons properly
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
