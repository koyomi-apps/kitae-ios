plugins {
    alias(libs.plugins.kotlin.multiplatform)
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
            implementation(libs.room3.runtime)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
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
    // RoomSchemaTest compares the schema Room exports with the iPhone's.
    dependsOn("copyRoomSchemas")
    val exportedSchema = layout.projectDirectory.file("schemas/ca.gureet.kitae.database.KitaeDatabase/10.json")
    val iPhoneSchema = layout.projectDirectory.file("../ios/KitaeTests/RoomSchema10.json")
    inputs.files(exportedSchema, iPhoneSchema).withPathSensitivity(PathSensitivity.NONE)
    systemProperty("kitae.exportedSchema", exportedSchema.asFile.path)
    systemProperty("kitae.iPhoneSchema", iPhoneSchema.asFile.path)
}
