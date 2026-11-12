plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ca.gureet.kitae"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ca.gureet.kitae"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 300000
        versionName = "3.0.0"
    }

    buildTypes {
        debug {
            // Debug builds install beside Kitae for Android 2.0.0 without touching its data.
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
}
