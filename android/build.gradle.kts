
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "ca.gureet.kitae"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // A new app, with its own key: it installs beside Kitae for Android 2.0.0
        // (ca.gureet.kitae), whose people move over by restoring a backup.
        applicationId = "ca.gureet.kamae"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Major × 10000 + minor × 100 + patch: 0.9.x are test builds, and 1.0.0 is 10000.
        versionCode = 900
        versionName = "0.9.0"
    }

    buildTypes {
        debug {
            // Debug builds install beside a released Kamae without touching its data.
            applicationIdSuffix = ".debug"
        }
    }

    // F-Droid builds leave out the dependency list Google's tools sign into an APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric draws the screens with the app's fonts and colours.
            isIncludeAndroidResources = true
        }
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric draws with the real graphics library, as the screen tests need.
    systemProperty("robolectric.graphicsMode", "NATIVE")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

/** The data files have one copy, the iPhone's: the app's assets take them from its folders. */
abstract class CopyDataFiles : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val files: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun copy() {
        fileSystem.sync {
            from(files)
            into(outputDirectory)
        }
    }
}

val copyDataFiles = tasks.register<CopyDataFiles>("copyDataFiles") {
    files.from(
        "../ios/Kitae/Database/Seed.json",
        "../ios/Kitae/Exercises/Muscles/ExerciseMuscles.json",
        "../ios/Kitae/Exercises/Motion/ExerciseMotions.json",
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyDataFiles, CopyDataFiles::outputDirectory)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
