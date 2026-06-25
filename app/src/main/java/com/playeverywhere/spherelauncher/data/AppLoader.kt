package com.playeverywhere.spherelauncher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory

class AppLoader(private val context: Context) {
    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val sortedApps = resolveInfos.mapNotNull { resolveInfo ->
            try {
                val label = resolveInfo.loadLabel(pm).toString()
                val packageName = resolveInfo.activityInfo.packageName
                val activityName = resolveInfo.activityInfo.name
                val rawIcon = resolveInfo.loadIcon(pm)
                val rawBitmap = drawableToBitmap(rawIcon)
                val circularBitmap = getCircularBitmap(rawBitmap)
                val hue = getDominantHue(circularBitmap)
                val iconBitmap = circularBitmap.asImageBitmap()
                AppInfo(label, packageName, activityName, iconBitmap, hue)
            } catch (e: Exception) {
                android.util.Log.e("AppLoader", "Failed to load info for package: ${resolveInfo.activityInfo.packageName}", e)
                null
            }
        }.sortedBy { it.label.lowercase() }
        
        saveAppsToCache(sortedApps)
        sortedApps
    }

    private suspend fun saveAppsToCache(apps: List<AppInfo>) = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "app_icons").apply { mkdirs() }
            val jsonArray = JSONArray()
            apps.forEach { app ->
                val jsonObject = JSONObject().apply {
                    put("label", app.label)
                    put("packageName", app.packageName)
                    put("activityName", app.activityName)
                    put("colorHue", app.colorHue.toDouble())
                }
                jsonArray.put(jsonObject)

                val file = File(cacheDir, "${app.packageName}.png")
                FileOutputStream(file).use { out ->
                    app.iconBitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            File(context.cacheDir, "apps_cache.json").writeText(jsonArray.toString())
        } catch (e: Exception) {
            android.util.Log.e("AppLoader", "Failed to save apps to cache", e)
        }
    }

    suspend fun loadCachedApps(): List<AppInfo>? = withContext(Dispatchers.IO) {
        try {
            val jsonFile = File(context.cacheDir, "apps_cache.json")
            if (!jsonFile.exists()) return@withContext null

            val jsonArray = JSONArray(jsonFile.readText())
            val cacheDir = File(context.cacheDir, "app_icons")
            val cachedApps = mutableListOf<AppInfo>()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val label = jsonObject.getString("label")
                val packageName = jsonObject.getString("packageName")
                val activityName = jsonObject.getString("activityName")
                if (!jsonObject.has("colorHue")) {
                    return@withContext null // Force reload if old cache
                }
                val colorHue = jsonObject.optDouble("colorHue", 0.0).toFloat()

                val file = File(cacheDir, "$packageName.png")
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        cachedApps.add(AppInfo(label, packageName, activityName, bitmap.asImageBitmap(), colorHue))
                    }
                }
            }
            if (cachedApps.isEmpty()) null else cachedApps
        } catch (e: Exception) {
            android.util.Log.e("AppLoader", "Failed to load cached apps", e)
            null
        }
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

    private fun getDominantHue(bitmap: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(bitmap, 16, 16, true)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0
        for (x in 0 until 16) {
            for (y in 0 until 16) {
                val pixel = scaled.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(pixel)
                if (alpha > 50) {
                    sumR += android.graphics.Color.red(pixel)
                    sumG += android.graphics.Color.green(pixel)
                    sumB += android.graphics.Color.blue(pixel)
                    count++
                }
            }
        }
        if (count == 0) return 0f
        val avgR = (sumR / count).toInt()
        val avgG = (sumG / count).toInt()
        val avgB = (sumB / count).toInt()
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(avgR, avgG, avgB, hsv)
        val hue = hsv[0]
        val sat = hsv[1]
        val value = hsv[2]
        
        if (sat < 0.15f || value < 0.15f) {
            return 1000f + value
        }
        
        return if (hue > 330f) hue - 360f else hue
    }
}
