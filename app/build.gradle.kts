import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.livesub.translate"
    compileSdk = 35

    signingConfigs {
        // Optional: only configured when release.* keys exist in local.properties,
        // so anyone can clone & build debug APKs without the signing keystore.
        if (localProps["release.storeFile"] != null) {
            create("release") {
                storeFile = file(localProps["release.storeFile"] as String)
                keyAlias = localProps["release.keyAlias"] as String
                storePassword = localProps["release.storePassword"] as String
                keyPassword = localProps["release.keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.livesub.translate"
        minSdk = 30
        targetSdk = 35
        versionCode = 9
        versionName = "1.8"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (localProps["release.storeFile"] != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
}
