import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Android 17 = API 37. CI passes the highest platform it can install (-Ptj.sdk=37).
val tjSdk = (project.findProperty("tj.sdk") as String?)?.toIntOrNull() ?: 36
val signProps = Properties().apply { val f = file("signing.properties"); if (f.exists()) f.inputStream().use { load(it) } }
// whisper.cpp source is fetched by the workflow into src/main/cpp/whisper.cpp
val hasWhisper = file("src/main/cpp/whisper.cpp/CMakeLists.txt").exists()
// LAME (MP3 encoder, LGPL) is fetched by the workflow into src/main/cpp/lame
val hasLame = file("src/main/cpp/lame/libmp3lame/lame.c").exists()
val hasNative = hasWhisper || hasLame

android {
    namespace = "com.tomstechsolutions.mic"
    compileSdk = tjSdk

    defaultConfig {
        applicationId = "com.tomstechsolutions.mic"
        minSdk = 24
        targetSdk = tjSdk
        versionCode = 910
        versionName = "0.9.1-beta"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("boolean", "HAS_WHISPER", hasWhisper.toString())
        buildConfigField("boolean", "HAS_MP3", hasLame.toString())
        if (hasNative) {
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
            externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_static", "-DTM_WHISPER=" + (if (hasWhisper) "ON" else "OFF"), "-DTM_LAME=" + (if (hasLame) "ON" else "OFF")); cppFlags += "-std=c++17" } }
        }
    }
    if (hasNative) {
        externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    }

    signingConfigs {
        create("release") {
            storeFile = file("tomsmic.keystore")
            storePassword = signProps.getProperty("storePassword")
            keyAlias = signProps.getProperty("keyAlias")
            keyPassword = signProps.getProperty("keyPassword")
        }
    }
    // the key is unpacked by the workflow from app/signing.enc (secret SIGN_SECRET); without it local builds use the debug key
    val relSign = if (signProps.isEmpty) signingConfigs.getByName("debug") else signingConfigs.getByName("release")
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = relSign
        }
        debug { signingConfig = relSign }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.mlkit:translate:17.0.3")
}
