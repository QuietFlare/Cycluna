import SwiftUI

/// The in-app privacy policy, required by App Store Guideline 5.1.1 (and checked
/// closely for reproductive-health apps under 5.1.3).
///
/// The text is embedded rather than loaded from the web on purpose: it must be
/// readable offline, and this policy claims the app makes no network requests.
///
/// ⚠️ Same policy, two homes — keep them in sync:
///   • in-app: this file
///   • hosted: `quiet-flare/src/pages/CyclunaPrivacy.tsx` → https://quietflare.net/cycluna/privacy
///     (App Store Connect requires a privacy policy *URL* as well as this screen)
///
/// ⚠️ Every statement below must stay TRUE as the app grows. If a backend, sync,
/// analytics SDK, or crash reporter is ever added, this text, the hosted page,
/// `PrivacyInfo.xcprivacy`, and the App Store Connect privacy labels all have to
/// change together.
struct PrivacyPolicyView: View {
    /// Mirrors "Effective August 2026" on the hosted page — bump both together.
    static let effective = "August 2026"
    static let supportEmail = "contact@quietflare.net"

    private struct Section: Identifiable {
        let id = UUID()
        let title: String
        let body: String
    }

    private let sections: [Section] = [
        .init(title: "The short version",
              body: """
              Cycluna keeps everything on your iPhone. There is no account, no sign-up, and the \
              app makes no network requests — your cycle data is never sent to us or to anyone \
              else, because there is nowhere for it to go.
              """),
        .init(title: "No accounts",
              body: """
              Cycluna requires no registration, login, email address, or any other identifier. \
              We have no way to know who you are.
              """),
        .init(title: "What Cycluna stores",
              body: """
              Only what you enter: the name you choose for your greeting, your period start \
              dates, your cycle and period lengths, and any mood, headache, or journal entries \
              you log. The app also remembers your settings, such as your theme and whether \
              reminders and the Face ID lock are switched on.
              """),
        .init(title: "On-device storage",
              body: """
              Your data lives in Cycluna's own storage area on your device, written with iOS \
              complete file protection — the file is encrypted while your device is locked and \
              cannot be read by other apps. Journal text is kept inside that protected file as \
              ordinary text; end-to-end encryption of journal entries is planned for a future \
              release. Deleting the app deletes everything.
              """),
        .init(title: "No data collection",
              body: """
              We do not collect, receive, transmit, sell, or share any personal data. The app \
              contains no analytics, no trackers, no advertising SDKs, no crash reporting, and \
              no third-party SDKs of any kind. It is a reproductive-health app, and it is built \
              so that your data never leaves your hands.
              """),
        .init(title: "Notifications",
              body: """
              Period and ovulation reminders are local notifications, scheduled and delivered \
              entirely on your device from your own predictions. No push-notification server is \
              involved. Cycluna asks for notification permission only when you turn a reminder \
              on, and switching the toggles off cancels them. While enabled, reminder text may \
              appear on your lock screen.
              """),
        .init(title: "Face ID and Touch ID",
              body: """
              If you turn on the app lock, iOS performs the check and tells Cycluna only whether \
              it succeeded. The app never sees your face, fingerprint, or passcode.
              """),
        .init(title: "Exporting and deleting your data",
              body: """
              Export writes a copy of your data and hands it to the iOS share sheet; once you \
              send that file somewhere it is outside Cycluna's protection and governed by \
              wherever you put it. Deleting your data erases the stored file from your device \
              permanently and cancels any scheduled reminders. It cannot be undone — and there \
              is no copy anywhere else for us to delete.
              """),
        .init(title: "Device backups",
              body: """
              Cycluna's data file may be included in your device backups according to your own \
              iOS backup settings, which are governed by Apple's privacy policy.
              """),
        .init(title: "Children",
              body: """
              Cycluna is not directed to children under 13 and does not knowingly store data \
              from them.
              """),
        .init(title: "Not medical advice",
              body: """
              Cycluna's predictions are estimates based on the dates you log. They are not \
              medical advice, not a diagnosis, and not a method of contraception. Talk to a \
              healthcare professional about anything that matters to your health.
              """),
        .init(title: "Changes & contact",
              body: """
              If a future version of Cycluna ever changes any of the above — for example when \
              optional, end-to-end encrypted sync arrives — this policy will be updated before \
              that version ships.
              """)
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Privacy Policy")
                        .font(.cyclunaSerif(30))
                        .foregroundStyle(Theme.ink)
                    Text("Effective \(Self.effective)")
                        .font(.footnote)
                        .foregroundStyle(Theme.inkSoft)
                }

                ForEach(sections) { section in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(section.title)
                            .font(.headline)
                            .foregroundStyle(Theme.primary)
                        Text(section.body)
                            .font(.body)
                            .foregroundStyle(Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                Text("Questions? \(Self.supportEmail)")
                    .font(.footnote)
                    .foregroundStyle(Theme.inkSoft)
                    .textSelection(.enabled)
                    .padding(.top, 4)
            }
            .padding(20)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle("Privacy Policy")
        .navigationBarTitleDisplayMode(.inline)
    }
}
