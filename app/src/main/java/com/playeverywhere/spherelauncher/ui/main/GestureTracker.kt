package com.playeverywhere.spherelauncher.ui.main

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.antigravity.gesture.HandGestureDetector

/**
 * Launches the front camera with only an ImageAnalysis use case (no Preview).
 * Frames are fed to MediaPipe HandGestureDetector for hand landmark detection.
 * No visible camera surface is shown — gesture tracking works entirely in the background.
 */
@Composable
fun GestureCameraLauncher(
    gestureDetector: HandGestureDetector,
    isEnabled: Boolean
) {
    if (!isEnabled) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(isEnabled, lifecycleOwner) {
        var isDisposed = false
        val cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                gestureDetector.close()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                gestureDetector.reinitialize()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        cameraProviderFuture.addListener({
            if (isDisposed) {
                try {
                    cameraProviderFuture.get().unbindAll()
                } catch (e: Exception) {
                    Log.e("GestureCameraLauncher", "Failed to unbind camera provider on disposal", e)
                }
                return@addListener
            }

            try {
                // Reinitialize detector if it was closed
                gestureDetector.reinitialize()
                
                val cameraProvider = cameraProviderFuture.get()

                // Front camera
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                val cameraResolution = Size(640, 480)

                // ImageAnalysis only — no Preview use case needed
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

                                // Rotate and mirror horizontally for front camera
                                val matrix = Matrix().apply {
                                    postRotate(rotationDegrees.toFloat())
                                    postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                                }

                                val transformed = Bitmap.createBitmap(
                                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                )
                                bitmap.recycle()

                                gestureDetector.detectAsync(transformed, SystemClock.uptimeMillis())
                            } catch (e: Exception) {
                                Log.e("GestureCameraLauncher", "Frame analysis failed", e)
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                cameraProvider.unbindAll()

                // Bind only ImageAnalysis — no Preview
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalyzer
                )

                Log.d("GestureCameraLauncher", "CameraX bound ImageAnalysis-only to front camera.")
            } catch (e: Exception) {
                Log.e("GestureCameraLauncher", "Failed to bind camera", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            isDisposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraExecutor.shutdown()
            gestureDetector.close() // <--- Clear state and free resources
            cameraProviderFuture.addListener({
                try {
                    cameraProviderFuture.get().unbindAll()
                    Log.d("GestureCameraLauncher", "CameraX unbound.")
                } catch (e: Exception) {
                    Log.e("GestureCameraLauncher", "Failed to unbind camera provider on disposal", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }
}
