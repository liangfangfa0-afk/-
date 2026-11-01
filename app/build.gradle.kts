plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ljj.yundongshuhuan"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ljj.yundongshuhuan"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "1.0.22"
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
}
