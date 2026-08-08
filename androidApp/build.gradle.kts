import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

/**
 * Release signing, read from `androidApp/keystore/` — a directory git-ignores wholesale, so
 * anything dropped in it (the properties file, the .jks itself) is covered by default:
 *
 *     storeFile=/absolute/path/to/cycluna-release.jks
 *     storePassword=…
 *     keyAlias=…
 *     keyPassword=…
 *
 * The repo is PUBLIC, so neither the keystore nor its passwords may ever be committed.
 *
 * Missing or incomplete credentials fail the build LOUDLY rather than producing a quietly
 * unsigned artifact — an unsigned .aab is rejected only later, by Play, long after you have
 * stopped thinking about it. A release must also never fall back to the debug key, which is
 * in every checkout of this repository.
 */
val keystoreFile = rootProject.file("androidApp/keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}

/** True only when every field needed to sign is actually filled in. */
val canSignRelease = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }

/**
 * Declared up here so the artifact name can reuse them. `versionCode` must increase for every
 * upload — Play rejects a repeat — while `versionName` is the string users see.
 */
val appVersionCode = 2
val appVersionName = "1.0"

/**
 * Output artifacts are named for the app, not the Gradle module. Without this they come out
 * as `androidApp-release.aab`, which says nothing about which app or which build it is once a
 * few of them are sitting in a folder together. Includes the version code because that is the
 * number Play actually distinguishes uploads by.
 */
base {
    archivesName = "cycluna-$appVersionName-$appVersionCode"
}

android {
    namespace = "app.cycluna.android"
    compileSdk = 36

    defaultConfig {
        // Matches the iOS bundle id. Changing it later makes Android treat the build as a
        // different app — older installs stop updating and have to be deleted by hand.
        applicationId = "net.quietflare.cycluna"
        minSdk = 26
        // Play requires API 36 for uploads from 31 Aug 2026. AGP 8.10's maximum supported
        // compileSdk is exactly 36, so this is the ceiling until the Gradle wrapper moves.
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Fail at the moment the release artifact is requested, naming exactly what is missing.
// Discovering this from Play's upload dialog instead is a much worse afternoon.
tasks.matching { it.name in setOf("bundleRelease", "assembleRelease") }.configureEach {
    doFirst {
        check(canSignRelease) {
            buildString {
                appendLine("Release signing is not configured.")
                appendLine("Expected: ${keystoreFile.path}")
                appendLine(
                    if (keystoreFile.exists()) {
                        "The file exists but these keys are missing or blank: " +
                            listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                                .filter { keystoreProperties.getProperty(it).isNullOrBlank() }
                    } else {
                        "Copy androidApp/keystore.properties.example there and fill it in."
                    }
                )
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

// Every coordinate here must appear on the Acknowledgements screen (Apache 2.0 §4).
dependencies {
    implementation(project(":shared"))

    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    // material3 brings material-icons-core. The `-extended` artifact is deliberately not
    // used: it is thousands of icons for the handful we need, and the platform guidance is
    // to pull in only what you draw. The two glyphs core lacks are drawn on a Canvas.
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
