plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.aurachat.app"
    compileSdk = 34

    defaultConfig {
        // Eski (Capacitor) uygulamayla yan yana kurulabilsin diye ayrı paket adı
        applicationId = "com.aurachat"
        minSdk = 24
        targetSdk = 34
        val buildNo = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = buildNo
        versionName = "0.1.$buildNo"
        // WebRTC kütüphanesi her işlemci türü için ayrı (~10 MB) kütüphane taşır; sadece modern telefonlar (arm64) kalsın
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // APK her zaman repodaki sabit anahtarla imzalansın (Firebase SHA-1/SHA-256 bununla eşleşir)
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("aura-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Debug derlemesi de release gibi optimize edilsin (R8 + debuggable kapalı): açılış çok daha hızlı olur
    buildTypes {
        getByName("debug") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // QR kod: üretmek için zxing core, taramak için Google kod tarayıcı (kamera izni gerektirmez)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("io.getstream:stream-webrtc-android:1.1.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}
