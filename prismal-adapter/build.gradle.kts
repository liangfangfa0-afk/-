plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.styropyr0.prismal"
    compileSdk = 37

    defaultConfig {
        minSdk = 25
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("../libs/PrismalAGSL/Prismal/src/main/AndroidManifest.xml")
            java.srcDir("../libs/PrismalAGSL/Prismal/src/main/java")
            assets.srcDir("../libs/PrismalAGSL/Prismal/src/main/assets")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    api(composeBom)
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.ui:ui")
    api("androidx.compose.ui:ui-graphics")
    api("androidx.compose.runtime:runtime")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.startup:startup-runtime:1.2.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xcontext-parameters")
        optIn.add("kotlin.time.ExperimentalTime")
    }
}
