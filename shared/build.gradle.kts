plugins {
    // Versions live in the root build file so the plugin loads once for the whole build.
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

kotlin {
    // 21 (not 17) because F-Droid's build image ships only JDK 21, and reproducible
    // builds require our published APK and their rebuild to use the same toolchain.
    jvmToolchain(21)

    // JVM target — used only for fast unit tests of the pure domain logic.
    jvm()

    // Android target — the Compose app links this directly, and it is the only place the
    // Keystore-backed KeyVault actual can live.
    androidTarget()

    // iOS targets — produce a static framework named "Shared" for the SwiftUI app.
    val iosTargets = listOf(iosArm64(), iosSimulatorArm64(), iosX64())
    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            // Static: the core links straight into the app binary, so there is no separate
            // dylib for iOS to code-sign, load and rebase on first launch. That validation
            // happens once per install and is the bulk of the slow launch after an update.
            isStatic = true
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

// The core ships no Android libraries of its own — the KeyVault actual uses only framework
// APIs (AndroidKeyStore, SharedPreferences). Keep it that way: a dependency added here has
// to be acknowledged on *both* platforms' Acknowledgements screens (see AcknowledgementsTest).
android {
    namespace = "app.cycluna.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
}
