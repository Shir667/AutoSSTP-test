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


tasks.register("packForXcode") {
    group = "build"
    dependsOn("linkDebugFrameworkIosArm64")

    doLast {
        val fromDir = buildDir.resolve("bin/iosArm64/debugFramework")
        val toDir   = buildDir.resolve("xcode-frameworks")

        // klasör var mı kontrol et, yoksa oluştur
        if (fromDir.exists()) {
            fromDir.copyRecursively(toDir, overwrite = true)
        } else {
            throw GradleException("Framework klasörü bulunamadı: $fromDir")
        }
    }
}