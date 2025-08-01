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
    // DEĞİŞTİR: Debug yerine RELEASE framework'ü kopyala
    dependsOn("linkReleaseFrameworkIosArm64")  // <-- BU SATIRI DEĞİŞTİRİN

    doLast {
        val fromDir = buildDir.resolve("bin/iosArm64/releaseFramework")  // <-- releaseFramework
        val toDir   = buildDir.resolve("xcode-frameworks")

        if (fromDir.exists()) {
            fromDir.copyRecursively(toDir, overwrite = true)
        } else {
            throw GradleException("Framework klasörü bulunamadı: $fromDir")
        }
    }
}