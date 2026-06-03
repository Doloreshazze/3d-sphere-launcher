package com.playeverywhere.spherelauncher.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.antigravity.gesture.HandGestureDetector
import java.util.concurrent.Executors

@Composable
fun GestureCameraLauncher(
    gestureDetector: HandGestureDetector,
    isEnabled: Boolean,
    previewSurfaceProvider: Preview.SurfaceProvider?
) {
    if (!isEnabled || previewSurfaceProvider == null) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(isEnabled, previewSurfaceProvider) {
        val cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Configure Front Camera Selector
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                // Target standard matching resolution
                val cameraResolution = Size(640, 480)

                // Configure Preview Use Case
                val preview = Preview.Builder()
                    .setTargetResolution(cameraResolution)
                    .build().apply {
                        setSurfaceProvider(previewSurfaceProvider)
                    }

                // Configure Image Analysis Use Case to feed MediaPipe HandGestureDetector
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(cameraResolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            try {
                                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                val bitmap = imageProxy.toBitmap()

                                // Rotate and mirror horizontally for front camera preview
                                val matrix = Matrix().apply {
                                    postRotate(rotationDegrees.toFloat())
                                    postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                                }

                                val transformedBitmap = Bitmap.createBitmap(
                                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                )
                                bitmap.recycle() // Recycle temporary bitmap immediately

                                val timestampMs = SystemClock.uptimeMillis()
                                gestureDetector.detectAsync(transformedBitmap, timestampMs)
                            } catch (e: Exception) {
                                Log.e("GestureCameraLauncher", "Frame analysis failed", e)
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                // Unbind previous use cases before binding new ones
                cameraProvider.unbindAll()

                // Bind both Preview (for screen HUD view) and ImageAnalysis (for MediaPipe tracking)
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                
                Log.d("GestureCameraLauncher", "CameraX successfully bound Preview + ImageAnalysis to front camera.")
            } catch (e: Exception) {
                Log.e("GestureCameraLauncher", "Failed to bind CameraX Preview + ImageAnalysis", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                cameraExecutor.shutdown()
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider.unbindAll()
                Log.d("GestureCameraLauncher", "CameraX successfully unbound.")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
