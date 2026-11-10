plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.gus.carhome"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.gus.carhome"
        minSdk = 29
        targetSdk = 29          // the head unit runs Android 10
        versionCode = 1
        versionName = "0.1"
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
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
    androidResources { noCompress += "ttf" }
    testOptions {
        unitTests.isIncludeAndroidResources = true   // Robolectric: real layouts, drawables, assets
        unitTests.all { it.maxHeapSize = "2g" }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
