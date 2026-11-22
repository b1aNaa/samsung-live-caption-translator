import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseSigning = localProps["release.storeFile"] != null

android {
    namespace = "com.livesub.translate"
    compileSdk = 35

    signingConfigs {
        // Only configured when release.* keys exist in local.properties, so
        // anyone can clone and build debug APKs without the keystore.
        if (hasReleaseSigning) {
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
        versionCode = 10
        versionName = "2.0"
    }

    buildTypes {
        debug {
            // `-PsignDebugWithRelease` lets a debug build replace an installed
            // release build on a test phone without losing its settings.
            if (hasReleaseSigning && project.hasProperty("signDebugWithRelease")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Renders the Compose screens on the JVM (ScreenshotTest) — no device needed.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
