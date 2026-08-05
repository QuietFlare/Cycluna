# cycluna-app (PUBLIC)

Native Cycluna clients + shared logic. **Public repo** (unlimited GitHub Actions
minutes). Contains **no secrets** — only the public Cloudflare Worker base URL.

## Structure

```
shared/      ← Kotlin Multiplatform core (iOS framework + Android library + JVM test target)
             ├── Moon.kt    — synodic moon-phase math (ported from web moon.ts)
             └── Cycle.kt   — cycle-day/phase prediction (ported from cycle.ts)
iosApp/      ← SwiftUI, links the Shared framework
androidApp/  ← Jetpack Compose, depends on :shared directly
```

Both apps are feature-equivalent and share the same `CycleData` format, so a JSON export
from one reads identically on the other.

## Toolchain (verified working)

- Kotlin **2.2.0**, Gradle wrapper **8.11.1**, `kotlinx-datetime` 0.6.1
- Build/test JVM: **Temurin JDK 17** (not the Homebrew JDK 26 — too new for this Gradle)
- iOS target: **Xcode 26**

## Run the shared-core tests

```bash
cd cycluna-app
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  ./gradlew :shared:jvmTest
```

## Build the iOS framework (needs Xcode 26 installed)

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

## Build and run the Android app

Needs the Android SDK (platform 35). Android Studio is optional — the command line is
enough; point `local.properties` at your SDK with `sdk.dir=…` (it is git-ignored).

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  ./gradlew :androidApp:installDebug
```

`applicationId` is `net.quietflare.cycluna`, matching the iOS bundle id; the code lives in
the `app.cycluna.android` namespace. minSdk 26, target/compile 35.

Backend lives in the private `cycluna-backend` repo. Full plan:
`cycluna-backend/docs/native-migration-plan.md`.

## License
Source-available under the **PolyForm Noncommercial License 1.0.0** — see [`LICENSE`](LICENSE).
You're welcome to read, learn from, and use Cycluna for **noncommercial** purposes, but not to sell
it or ship it (or a derivative) commercially. Copyright © 2026 Seema Jagadeesh / Quietflare.
