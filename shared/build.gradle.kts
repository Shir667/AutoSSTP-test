plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    iosArm64()
    iosX64()
    iosSimulatorArm64()
}

android {
    namespace = "com.example.shared"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
}

// Basit task: framework klasörünü kopyalar
tasks.register("packForXcode") {
    group = "build"
    dependsOn("linkDebugFrameworkIosArm64")
    doLast {
        val fromDir = buildDir.resolve("bin/iosArm64/debugFramework")
        val toDir  = buildDir.resolve("xcode-frameworks")
        fromDir.copyRecursively(toDir, overwrite = true)
    }
}