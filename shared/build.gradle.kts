plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    iosArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = false // Dinamik framework
        }
    }
}

android {
    namespace = "com.example.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }
}