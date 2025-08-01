plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    iosArm64 {          // bu satır framework’ü otomatik oluşturur
        binaries.framework {
            baseName = "Shared"
        }
    }
}

android {
    namespace = "com.example.shared"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
}