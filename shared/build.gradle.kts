plugins {
    kotlin("multiplatform") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
}

kotlin {
    jvmToolchain(17)

    // JVM target — used only for fast unit tests of the pure domain logic.
    jvm()

    // iOS targets — produce a static framework named "Shared" for the SwiftUI app.
    val iosTargets = listOf(iosArm64(), iosSimulatorArm64(), iosX64())
    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = false
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
