plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xiaoxi.codepad"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xiaoxi.codepad"
        minSdk = 28
        targetSdk = 28
        versionCode = 17
        versionName = "0.8.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
