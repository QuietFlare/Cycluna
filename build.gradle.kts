// Plugin versions are declared here once, with `apply false`, and the modules apply them
// without a version. Two sibling modules each declaring `kotlin("...") version "..."` would
// load the plugin on two separate classloaders, which breaks Kotlin Multiplatform + AGP.
//
// AGP 8.10.x is the ceiling for the Gradle 8.11.1 wrapper: 8.10 requires exactly 8.11.1,
// and AGP 8.11+ requires Gradle 8.13.
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    kotlin("multiplatform") version "2.2.0" apply false
    kotlin("android") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    kotlin("plugin.compose") version "2.2.0" apply false
}
