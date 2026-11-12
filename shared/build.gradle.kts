plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

// The iPhone's targets need Xcode, so they're only built on a Mac. Elsewhere, such as on
// F-Droid's build servers and in CI on Linux, the module has Android and the JVM.
val buildsForIPhone = System.getProperty("os.name").startsWith("Mac")

kotlin {
    android {
        namespace = "ca.gureet.kitae.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    jvm()
    if (buildsForIPhone) {
        iosArm64()
        iosSimulatorArm64()
    }

    compilerOptions {
        // Room's KSP writes the `actual` KitaeDatabaseConstructor for each target.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.room3.runtime)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.room3.testing)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room3.compiler)
    add("kspJvm", libs.room3.compiler)
    if (buildsForIPhone) {
        add("kspIosArm64", libs.room3.compiler)
        add("kspIosSimulatorArm64", libs.room3.compiler)
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

tasks.named<Test>("jvmTest") {
    // The tests read the schemas Room exports, and the iPhone's schema and seed.
    dependsOn("copyRoomSchemas")
    val schemas = layout.projectDirectory.dir("schemas")
    val iPhoneSchema = layout.projectDirectory.file("../ios/KitaeTests/RoomSchema10.json")
    val seed = layout.projectDirectory.file("../ios/Kitae/Database/Seed.json")
    val muscles = layout.projectDirectory.file("../ios/Kitae/Exercises/Muscles/ExerciseMuscles.json")
    inputs.dir(schemas).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(iPhoneSchema, seed, muscles).withPathSensitivity(PathSensitivity.NONE)
    systemProperty("kitae.schemas", schemas.asFile.path)
    systemProperty("kitae.iPhoneSchema", iPhoneSchema.asFile.path)
    systemProperty("kitae.seed", seed.asFile.path)
    systemProperty("kitae.muscles", muscles.asFile.path)
}
