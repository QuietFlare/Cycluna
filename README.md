# cycluna-app (PUBLIC)

Native Cycluna clients + shared logic. **Public repo** (unlimited GitHub Actions
minutes). Contains **no secrets**, only the public Cloudflare Worker base URL.

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
- Build/test JVM: **Temurin JDK 17** (not the Homebrew JDK 26, too new for this Gradle)
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

Needs the Android SDK (platform 36). Android Studio is optional — the command line is
enough; point `local.properties` at your SDK with `sdk.dir=…` (it is git-ignored).

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  ./gradlew :androidApp:installDebug
```

`applicationId` is `net.quietflare.cycluna`, matching the iOS bundle id; the code lives in
the `app.cycluna.android` namespace. minSdk 26, target/compile 36.

Backend lives in the private `cycluna-backend` repo. Full plan:
`cycluna-backend/docs/native-migration-plan.md`.

## Contributing

Cycluna is women's health software, and it is open to the people it is for. If you have ever
wished a cycle app did something differently, you are qualified to say so here. Contributions
from women are especially welcome, and lived experience counts as expertise: translations, the
words used to describe each phase, bug reports, and design all matter as much as code.

Start with [`CONTRIBUTING.md`](CONTRIBUTING.md). Everyone taking part is expected to follow the
[Code of Conduct](CODE_OF_CONDUCT.md).

## License

MIT, see [`LICENSE`](LICENSE). Use it, fork it, learn from it, build on it. The cycle and moon
math in `shared/` is meant to be reusable by other women's health projects.

Copyright © 2026 Seema Jagadeesh / QuietFlare. The MIT license covers the code, not the Cycluna
name or icon.
