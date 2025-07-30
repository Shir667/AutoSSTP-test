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


tasks.register("packForXcode") {
    group = "build"
    dependsOn("linkDebugFrameworkIosArm64")
    doLast {
        val from = buildDir.resolve("bin/iosArm64/debugFramework")
        val to   = buildDir.resolve("xcode-frameworks")
        from.copyRecursively(to, overwrite = true)
    }
}