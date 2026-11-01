plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.motionguard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.motionguard"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "1.0.20"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // The LSPosed framework supplies this API at runtime.
    compileOnly("io.github.libxposed:api:102.0.0")
}
