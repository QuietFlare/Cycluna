package app.cycluna.android.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import app.cycluna.android.designsystem.Crescent
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.cyclunaCard
import app.cycluna.android.designsystem.serif

private const val SUPPORT_URL = "https://quietflare.net/cycluna/support"
// SITE_URL lives in AppLinks.kt — the share sheet points at the same page.

/** Which page of the About stack is showing. */
enum class AboutPage { ROOT, PRIVACY, DISCLAIMER, ACKNOWLEDGEMENTS }

/**
 * The About stack: what this app is, which build you're running, and where the legal and
 * support material lives.
 *
 * For a reproductive-health app this isn't decoration — store review looks for the privacy
 * policy and the medical disclaimer, and "which version am I on" is the first question any
 * support conversation starts with.
 */
@Composable
fun AboutScreen(page: AboutPage, onNavigate: (AboutPage) -> Unit) {
    when (page) {
        AboutPage.ROOT -> AboutRoot(onNavigate)
        AboutPage.PRIVACY -> TextPage("Privacy Policy", "Effective $POLICY_EFFECTIVE", PRIVACY_SECTIONS) {
            Text(
                "Questions? $SUPPORT_EMAIL",
                Modifier.padding(top = 4.dp),
                fontSize = 13.sp,
                color = Theme.inkSoft,
            )
        }
        AboutPage.DISCLAIMER -> TextPage("Health Disclaimer", null, DISCLAIMER_SECTIONS)
        AboutPage.ACKNOWLEDGEMENTS -> AcknowledgementsPage()
    }
}

@Composable
private fun AboutRoot(onNavigate: (AboutPage) -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // The installer cannot change while the app runs, so this is asked once per screen entry.
    val showRate = remember { canRateOnPlay(context) }
    val version = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        // Via the compat helper: PackageInfo.longVersionCode is API 28, and minSdk is 26.
        "Version ${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }.getOrDefault("Version —")

    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.cyclunaCard(padding = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Crescent(
                Modifier.size(84.dp),
                Brush.linearGradient(listOf(Theme.primary, Theme.secondary)),
            )
            Text("Cycluna", style = serif(30).copy(color = Theme.ink))
            Text(version, fontSize = 14.sp, color = Theme.inkSoft)
            Text(
                "Your rhythm, in tune with the moon and your body.",
                fontSize = 15.sp,
                color = Theme.inkSoft,
                textAlign = TextAlign.Center,
            )
        }

        Column(Modifier.cyclunaCard(padding = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("LEGAL", fontSize = 11.sp, letterSpacing = 1.0.sp, color = Theme.accentText)
            LinkRow("Privacy Policy") { onNavigate(AboutPage.PRIVACY) }
            HorizontalDivider(color = Theme.inkSoft.copy(alpha = 0.12f))
            LinkRow("Health Disclaimer") { onNavigate(AboutPage.DISCLAIMER) }
            HorizontalDivider(color = Theme.inkSoft.copy(alpha = 0.12f))
            LinkRow("Acknowledgements") { onNavigate(AboutPage.ACKNOWLEDGEMENTS) }
        }

        Column(Modifier.cyclunaCard(padding = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LinkRow("Support") { uriHandler.openUri(SUPPORT_URL) }
            // Only on a Play install: an F-Droid or sideloaded copy must not link to a
            // store it did not come from.
            if (showRate) {
                HorizontalDivider(color = Theme.inkSoft.copy(alpha = 0.12f))
                LinkRow("Rate Cycluna") { rateApp(context) }
            }
            HorizontalDivider(color = Theme.inkSoft.copy(alpha = 0.12f))
            // Sends quietflare.net/cycluna, not a store listing — one link that works
            // whatever phone the recipient has, and editable without shipping a build.
            // There is deliberately no row that merely *opens* that page: it exists to
            // convince people to install the app, which everyone here already has.
            LinkRow("Share Cycluna") { shareApp(context) }
        }

        Text(
            "Made by QuietFlare",
            Modifier.fillMaxWidth(),
            fontSize = 13.sp,
            color = Theme.inkSoft,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        fontSize = 16.sp,
        color = Theme.ink,
    )
}

@Composable
private fun TextPage(
    title: String,
    subtitle: String?,
    sections: List<Pair<String, String>>,
    footer: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = serif(30).copy(color = Theme.ink))
            subtitle?.let { Text(it, fontSize = 13.sp, color = Theme.inkSoft) }
        }
        sections.forEach { (heading, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(heading, fontSize = 16.sp, color = Theme.primary)
                Text(body, fontSize = 15.sp, lineHeight = 22.sp, color = Theme.ink)
            }
        }
        footer()
    }
}

@Composable
private fun AcknowledgementsPage() {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text("Acknowledgements", style = serif(30).copy(color = Theme.ink))
        Text(
            "Cycluna is built with these open-source libraries.",
            fontSize = 15.sp,
            color = Theme.inkSoft,
        )
        LIBRARIES.forEach { (name, rest) ->
            val (coordinate, notice) = rest
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, fontSize = 16.sp, color = Theme.primary)
                if (coordinate.isNotEmpty()) {
                    Text(coordinate, fontSize = 12.sp, color = Theme.inkSoft)
                }
                Text(notice, fontSize = 14.sp, lineHeight = 20.sp, color = Theme.ink)
            }
        }
        Text(
            "Licensed under the Apache License, Version 2.0. You may obtain a copy of the " +
                "License at apache.org/licenses/LICENSE-2.0. Unless required by applicable law " +
                "or agreed to in writing, software distributed under the License is " +
                "distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY " +
                "KIND, either express or implied.",
            Modifier.clickable { uriHandler.openUri("https://www.apache.org/licenses/LICENSE-2.0") },
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Theme.inkSoft,
        )
    }
}

// ------------------------------------------------------------------------------------
// Copy
// ------------------------------------------------------------------------------------

/** Mirrors "Effective August 2026" on the hosted page — bump both together. */
private const val POLICY_EFFECTIVE = "August 2026"
private const val SUPPORT_EMAIL = "contact@quietflare.net"

/**
 * The in-app privacy policy. Embedded rather than fetched: it must be readable offline, and
 * it claims the app makes no network requests.
 *
 * Same policy, three homes — keep them in sync: this file, the iOS
 * `PrivacyPolicyView.swift`, and the hosted page at quietflare.net/cycluna/privacy.
 *
 * Every statement below must stay TRUE as the app grows. If a backend, sync, analytics SDK
 * or crash reporter is ever added, this text, the iOS copy, the hosted page and the Play
 * Data safety form all have to change together.
 */
private val PRIVACY_SECTIONS = listOf(
    "The short version" to
        "Cycluna keeps everything on your phone. There is no account, no sign-up, and the " +
        "app makes no network requests — your cycle data is never sent to us or to anyone " +
        "else, because there is nowhere for it to go.",
    "No accounts" to
        "Cycluna requires no registration, login, email address, or any other identifier. " +
        "We have no way to know who you are.",
    "What Cycluna stores" to
        "Only what you enter: the name you choose for your greeting, your period start " +
        "dates, your cycle and period lengths, and any mood, headache, or journal entries " +
        "you log. The app also remembers your settings, such as whether reminders and the " +
        "app lock are switched on.",
    "On-device storage" to
        "Your data lives in Cycluna's own private storage area, which other apps cannot " +
        "read and which Android encrypts at rest as part of file-based encryption. Journal " +
        "text is kept inside that file as ordinary text; end-to-end encryption of journal " +
        "entries is planned for a future release. Uninstalling the app deletes everything.",
    "No data collection" to
        "We do not collect, receive, transmit, sell, or share any personal data. The app " +
        "contains no analytics, no trackers, no advertising SDKs, no crash reporting, and " +
        "no third-party SDKs of any kind. It is a reproductive-health app, and it is built " +
        "so that your data never leaves your hands.",
    "Notifications" to
        "Period and ovulation reminders are local notifications, scheduled and delivered " +
        "entirely on your device from your own predictions. No push-notification server is " +
        "involved. Cycluna asks for notification permission only when you turn a reminder " +
        "on, and switching the toggles off cancels them. While enabled, reminder text may " +
        "appear on your lock screen.",
    "Fingerprint and face unlock" to
        "If you turn on the app lock, Android performs the check and tells Cycluna only " +
        "whether it succeeded. The app never sees your fingerprint, face, or PIN.",
    "Exporting and deleting your data" to
        "Export writes a copy of your data and hands it to the Android share sheet; once " +
        "you send that file somewhere it is outside Cycluna's protection and governed by " +
        "wherever you put it. Deleting your data erases the stored file from your device " +
        "permanently and cancels any scheduled reminders. It cannot be undone — and there " +
        "is no copy anywhere else for us to delete.",
    "Device backups" to
        "Cycluna opts out of Android's cloud backup and device-to-device transfer, so your " +
        "cycle data is not copied to Google's servers or carried to a new phone. Setting up " +
        "a new device starts Cycluna empty.",
    "Children" to
        "Cycluna is not directed to children under 13 and does not knowingly store data " +
        "from them.",
    "Not medical advice" to
        "Cycluna's predictions are estimates based on the dates you log. They are not " +
        "medical advice, not a diagnosis, and not a method of contraception. Talk to a " +
        "healthcare professional about anything that matters to your health.",
    "Changes & contact" to
        "If a future version of Cycluna ever changes any of the above — for example when " +
        "optional, end-to-end encrypted sync arrives — this policy will be updated before " +
        "that version ships.",
)

/**
 * The health disclaimer. Cycluna predicts from dates the user typed in; it measures nothing,
 * and it is not a contraceptive. Saying so plainly protects the user first and store review
 * second.
 */
private val DISCLAIMER_SECTIONS = listOf(
    "Not medical advice" to
        "Cycluna is a tracking and reflection tool, not a medical device and not a " +
        "substitute for professional care. Nothing in the app diagnoses, treats, or " +
        "prevents any condition.",
    "Predictions are estimates" to
        "Every date the app shows — your next period, your fertile window, your current " +
        "phase — is calculated from the dates you enter. The app measures nothing about " +
        "your body. Cycles vary with stress, illness, travel, medication, and much else, " +
        "so real dates will differ from predicted ones.",
    "Not contraception" to
        "The fertile window is an estimate and must not be used to prevent pregnancy. It " +
        "is not a fertility-awareness method, and it has not been evaluated or cleared by " +
        "any regulator for that purpose. Talk to a healthcare professional about " +
        "contraception.",
    "Patterns are your own" to
        "Mood and headache insights describe what you have logged and nothing more. They " +
        "are observations about your own entries, not findings about health in general, " +
        "and Cycluna only names a pattern when there is enough data to support one.",
    "When to seek care" to
        "Speak to a doctor or midwife about periods that are unusually heavy, painful, " +
        "absent, or irregular for you, about bleeding between periods or after sex, or " +
        "about any symptom that worries you. Do not wait on an app. In an emergency, " +
        "contact your local emergency services.",
)

/**
 * Third-party attribution for the code Cycluna ships. A licence obligation, not a courtesy:
 * Apache 2.0 §4 requires the notices to travel with the distributed binary.
 *
 * Keep in step with `androidApp/build.gradle.kts` — `AndroidAcknowledgementsTest` fails if a
 * dependency is added there and not listed here.
 */
private val LIBRARIES: List<Pair<String, Pair<String, String>>> = listOf(
    "kotlinx-datetime" to ("org.jetbrains.kotlinx:kotlinx-datetime" to
        "Copyright © 2019–2026 JetBrains s.r.o. Apache License 2.0"),
    "kotlinx.serialization" to ("org.jetbrains.kotlinx:kotlinx-serialization-json" to
        "Copyright © 2017–2026 JetBrains s.r.o. Apache License 2.0"),
    "Kotlin standard library" to ("" to
        "Copyright © 2010–2026 JetBrains s.r.o. and Kotlin Programming Language " +
        "contributors. Apache License 2.0"),
    "Jetpack Compose" to ("androidx.compose" to
        "Copyright © 2019–2026 The Android Open Source Project. Apache License 2.0"),
    "AndroidX Activity, Core, Lifecycle, DataStore, Biometric, Fragment" to
        ("androidx.activity:activity-compose, androidx.core:core-ktx, " +
            "androidx.lifecycle, androidx.datastore:datastore-preferences, " +
            "androidx.biometric:biometric, androidx.fragment:fragment-ktx" to
            "Copyright © 2018–2026 The Android Open Source Project. Apache License 2.0"),
)
