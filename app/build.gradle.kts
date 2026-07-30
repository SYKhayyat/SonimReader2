plugins {
    id("com.android.application")
}

android {
    namespace = "com.sonim.reader"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.sonim.reader"
        minSdk = 24
        // Sonim XP5s is AOSP Android 8.1 (API 27). Targeting 28 keeps the
        // legacy external-storage model the File-based picker relies on and
        // avoids scoped-storage behaviour the device never had.
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // Standard AndroidX library for basic UI components
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Pure-JVM unit tests for the framework-free `core` package.
    testImplementation("junit:junit:4.13.2")
}