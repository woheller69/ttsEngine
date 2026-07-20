plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.k2fsa.sherpa.onnx.tts.engine"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.woheller69.ttsengine"
        minSdk = 29
        targetSdk = 35
        versionCode = 34
        versionName = "3.4"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildFeatures {
            viewBinding = true
            buildConfig = true
            compose = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        disable += "MissingTranslation"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.github.woheller69:FreeDroidWarn:+")
    implementation("org.jsoup:jsoup:1.22.1")
    implementation ("androidx.work:work-runtime:2.10.2")
}