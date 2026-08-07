# Contributing to Cycluna

Cycluna is a menstrual cycle and moon companion for iPhone and Android. Everything stays on the
user's device: no account, no server, no network calls, no ads, no tracking. It is free and will
stay free.

Contributions from women are especially welcome. Lived experience counts as expertise here, and
much of the most useful work needs no code.

## Everything starts with an issue

Open an issue first, for anything: a feature idea, a bug, a translation you want to take on, or a
sentence you think is wrong. That is where it gets discussed and agreed before anyone spends time
building it, and it is how work gets claimed so two people do not do it twice.

Please do not send a pull request for a feature that has no issue. It may be something already
decided against, and it is kinder to find that out before you write the code than after.

Not sure where to start? Open an issue saying what you would like to work on, and it can be scoped
from there.

## Ways to help without code

- **Feature ideas.** If you have ever wished a cycle app did something differently, open an issue
  and describe it. This is a real contribution, and often the most valuable one.
- **Translations.** The highest value code-adjacent contribution right now.
- **The words.** The text describing each cycle phase. If it reads wrong or condescending, say so
  and suggest better.
- **Bug reports.** What you expected, what happened, your iOS or Android version.
- **Design and accessibility.** Type sizes, contrast, VoiceOver and TalkBack labels.

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
- Medical claims. Cycluna is educational. It does not diagnose, treat, predict disease, or act as
  contraception, and it does not interpret lab results.
- Religious claims, or content that frames cycles, fertility, or menstruation through the beliefs
  or practices of any faith.
- Astrological claims. The moon is in Cycluna because it is beautiful, and the app never says it
  influences a cycle or a mood.

## Pull requests

Link the issue your pull request closes. One change per pull request. Add tests for anything in
`shared/` and run `./gradlew :shared:jvmTest` before pushing. Screenshots help for visual changes.

There is no contributor licence agreement. Cycluna is MIT licensed, and opening a pull request
offers your contribution under that same licence.

Review happens alongside other work, so replies may take a while. Every contribution gets read.

## Conduct and security

Everyone taking part follows the [Code of Conduct](CODE_OF_CONDUCT.md).

For a security or privacy issue, please do not open a public issue. Email contact@quietflare.net.
