package com.playeverywhere.spherelauncher.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
fun FisheyeCameraView(
    modifier: Modifier = Modifier,
    lensFacing: Int = CameraSelector.LENS_FACING_BACK
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                FisheyeGLSurfaceView(ctx).apply {
                    startCamera(ctx, lifecycleOwner, lensFacing)
                }
            },
            update = { view ->
                view.updateLensFacing(lifecycleOwner, lensFacing)
            }
        )
    }
}

class FisheyeGLSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer = FisheyeRenderer(this)
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentLensFacing = CameraSelector.LENS_FACING_BACK
    private var surfaceProviderSet = false

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun startCamera(context: Context, lifecycleOwner: androidx.lifecycle.LifecycleOwner, lensFacing: Int) {
        currentLensFacing = lensFacing
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(lifecycleOwner, lensFacing)
        }, ContextCompat.getMainExecutor(context))
    }
    
    fun updateLensFacing(lifecycleOwner: androidx.lifecycle.LifecycleOwner, lensFacing: Int) {
        if (currentLensFacing != lensFacing && cameraProvider != null) {
            currentLensFacing = lensFacing
            surfaceProviderSet = false
            bindCameraUseCases(lifecycleOwner, lensFacing)
        }
    }

    private fun bindCameraUseCases(lifecycleOwner: androidx.lifecycle.LifecycleOwner, lensFacing: Int) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val preview = Preview.Builder().build()

        preview.setSurfaceProvider { request ->
            renderer.surfaceTexture?.let { surfaceTexture ->
                surfaceTexture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                val surface = Surface(surfaceTexture)
                request.provideSurface(surface, ContextCompat.getMainExecutor(context)) {
                    surface.release()
                }
                surfaceProviderSet = true
            } ?: run {
                postDelayed({ bindCameraUseCases(lifecycleOwner, lensFacing) }, 100)
            }
        }

        try {
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
        } catch (exc: Exception) {
            Log.e("FisheyeCameraView", "Use case binding failed", exc)
        }
    }
}

class FisheyeRenderer(private val glView: GLSurfaceView) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    var surfaceTexture: SurfaceTexture? = null
    private var textureId: Int = 0
    private var program: Int = 0

    private val vertexShaderCode = """
        attribute vec4 position;
        attribute vec4 inputTextureCoordinate;
        uniform mat4 textureTransform;
        varying vec2 textureCoordinate;
        void main() {
            gl_Position = position;
            textureCoordinate = (textureTransform * inputTextureCoordinate).xy;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 textureCoordinate;
        uniform samplerExternalOES videoFrame;
        
        void main() {
            vec2 p = textureCoordinate - vec2(0.5, 0.5);
            float r = length(p);
            
            if (r > 0.5) {
                gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
            } else {
                // Stronger fisheye distortion mapping
                float rn = r * 2.0;
                float r_new = pow(rn, 2.2) * 0.5;
                
                vec2 uv = vec2(0.5, 0.5) + normalize(p) * r_new;
                vec4 color = texture2D(videoFrame, uv);
                
                // Gradient alpha fade out at the edges
                float alpha = smoothstep(0.5, 0.40, r);
                gl_FragColor = vec4(color.rgb * alpha, alpha);
            }
        }
    """.trimIndent()

    private val squareCoords = floatArrayOf(
        -1.0f,  1.0f,
        -1.0f, -1.0f,
         1.0f, -1.0f,
         1.0f,  1.0f
    )
    
    private val textureCoords = floatArrayOf(
        0.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 1.0f
    )

    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(squareCoords.size * 4).run {
        order(ByteOrder.nativeOrder())
        asFloatBuffer().apply {
            put(squareCoords)
            position(0)
        }
    }

    private val textureBuffer: FloatBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4).run {
        order(ByteOrder.nativeOrder())
        asFloatBuffer().apply {
            put(textureCoords)
            position(0)
        }
    }

    private val drawListBuffer = ByteBuffer.allocateDirect(drawOrder.size * 2).run {
        order(ByteOrder.nativeOrder())
        asShortBuffer().apply {
            put(drawOrder)
            position(0)
        }
    }
    
    private val transformMatrix = FloatArray(16)
    private var updateSurface = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@FisheyeRenderer)
        }

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        synchronized(this) {
            if (updateSurface) {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(transformMatrix)
                updateSurface = false
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        val positionHandle = GLES20.glGetAttribLocation(program, "position")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val textureCoordHandle = GLES20.glGetAttribLocation(program, "inputTextureCoordinate")
        GLES20.glEnableVertexAttribArray(textureCoordHandle)
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        val transformHandle = GLES20.glGetUniformLocation(program, "textureTransform")
        GLES20.glUniformMatrix4fv(transformHandle, 1, false, transformMatrix, 0)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureCoordHandle)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) {
            updateSurface = true
        }
        glView.requestRender()
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
