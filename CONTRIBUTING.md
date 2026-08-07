# Contributing to Cycluna

Cycluna is a menstrual cycle and moon companion for iPhone and Android. Everything stays on the
user's device: no account, no server, no network calls, no ads, no tracking. It is free and will
stay free.

Contributions from women are especially welcome. Lived experience counts as expertise here, and
much of the most useful work needs no code.

## Ways to help without code

- **Translations.** The highest value contribution right now.
- **The words.** The text describing each cycle phase. If it reads wrong or condescending, say so
  and suggest better.
- **Bug reports.** What you expected, what happened, your iOS or Android version.
- **Design and accessibility.** Type sizes, contrast, VoiceOver and TalkBack labels.

Open an issue for any of these. Issues labelled `good first issue` are scoped to be picked up
without reading the whole codebase.

## Code

```
shared/     Kotlin Multiplatform core: cycle math, moon math, insight rules. Pure logic, tested.
iosApp/     SwiftUI app, consumes the core.
androidApp/ Jetpack Compose app, consumes the same core.
```

Logic both platforms need belongs in `shared/`, with tests. UI, copy, and colours belong in the
native apps.

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
./gradlew :shared:jvmTest
```

JDK 17 (Temurin) is required. For iOS, run `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
then `cd iosApp && xcodegen generate`. The Xcode project is generated from `iosApp/project.yml`;
edit the yml, not the `.xcodeproj`.

## What will not be merged

Cycluna's promise is that cycle data never leaves the device. These are out of scope:

- Network calls, analytics, crash reporting, or third-party SDKs
- Advertising, tracking, or monetizing user data
- Accounts or cloud sync in the current version
- Anything that diagnoses, predicts disease, or acts as contraception
- Claims that the moon influences cycles or mood

## Pull requests

One change per pull request. Add tests for anything in `shared/` and run `./gradlew
:shared:jvmTest` before pushing. Screenshots help for visual changes.

There is no contributor licence agreement. Cycluna is MIT licensed, and opening a pull request
offers your contribution under that same licence.

Review happens alongside other work, so replies may take a while. Every contribution gets read.

## Conduct and security

Everyone taking part follows the [Code of Conduct](CODE_OF_CONDUCT.md).

For a security or privacy issue, please do not open a public issue. Email contact@quietflare.net.
