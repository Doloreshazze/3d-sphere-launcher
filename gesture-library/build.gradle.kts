import java.net.URI

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.antigravity.gesture"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

// Automated task to download the MediaPipe Model Asset during build
tasks.register("downloadHandLandmarker") {
    val modelDir = file("src/main/assets")
    val modelFile = file("src/main/assets/hand_landmarker.task")
    outputs.file(modelFile)
    doLast {
        if (!modelFile.exists()) {
            modelDir.mkdirs()
            println("Downloading hand_landmarker.task from MediaPipe repository...")
            val uri = URI("https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task")
            val input: java.io.InputStream = uri.toURL().openStream()
            val output: java.io.OutputStream = modelFile.outputStream()
            try {
                input.copyTo(output)
            } finally {
                input.close()
                output.close()
            }
            println("Download completed successfully!")
        } else {
            println("hand_landmarker.task already exists.")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadHandLandmarker")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.google.mediapipe.vision)
    
    // CameraX is needed so that the library can accept ImageProxy directly
    implementation(libs.androidx.camera.core)
    
    // Testing dependencies
    testImplementation(libs.junit)
}
