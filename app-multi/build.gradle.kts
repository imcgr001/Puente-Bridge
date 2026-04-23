plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pixeltranslator.multi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pixeltranslator.multi"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")

    // ML Kit Language Identification — bundled model, offline-capable,
    // identifies ~110 languages from transcribed text. Adds ~900 KB.
    // Used by auto-detect mode to replace the hand-rolled scorer for
    // open-set identification across languages we haven't curated features for.
    implementation("com.google.mlkit:language-id:17.0.6")

    // ML Kit Translate — per-pair on-device translation (~30 MB per model,
    // downloaded on demand). Covers 59 languages including all 13 of our
    // paired set. Used in the image pipeline as the second step after Gemma
    // OCR, replacing Gemma's text-translate call: faster (~100 ms vs 1-2 s),
    // deterministic, and single-purpose so it can't collapse to OCR-only.
    implementation("com.google.mlkit:translate:17.0.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
