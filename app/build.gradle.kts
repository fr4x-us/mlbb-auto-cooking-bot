plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "us.fr4x.mlbbbot"
    compileSdk = 34

    defaultConfig {
        applicationId = "us.fr4x.mlbbbot"
        minSdk = 28 // BluetoothHidDevice tersedia sejak API 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-phase1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
