plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget() {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = false
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Ortak bağımlılıklar
        }
        androidMain.dependencies {
            // Android'e özel bağımlılıklar
        }
        // iOS kaynak seti için doğru tanımlama
        val iosArm64Main by getting {
            dependencies {
                // iOS'a özel bağımlılıklar
            }
        }
    }
}

android {
    namespace = "com.example.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        // targetSdk = 34  // Deprecated - kaldırıyoruz
    }
    lint {
        targetSdk = 34
    }
}