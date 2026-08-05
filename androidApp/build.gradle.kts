import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

/**
 * Release signing, read from an untracked `keystore.properties` beside this file:
 *
 *     storeFile=/absolute/path/to/cycluna-release.jks
 *     storePassword=…
 *     keyAlias=…
 *     keyPassword=…
 *
 * The repo is PUBLIC, so neither the keystore nor its passwords may ever be committed —
 * `keystore.properties` and `*.jks` are git-ignored. Without that file the release build
 * still compiles and shrinks, it simply comes out unsigned and cannot be installed, which is
 * the safe failure: a release must never fall back to the debug key, or the app would ship
 * signed with a key that is in every checkout of this repository.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("androidApp/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "app.cycluna.android"
    compileSdk = 35

    defaultConfig {
        // Matches the iOS bundle id. Changing it later makes Android treat the build as a
        // different app — older installs stop updating and have to be deleted by hand.
        applicationId = "net.quietflare.cycluna"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
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
