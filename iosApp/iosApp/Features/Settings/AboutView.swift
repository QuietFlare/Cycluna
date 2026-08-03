import SwiftUI

/// The About sheet: what this app is, which build you're running, and where the legal and
/// support material lives.
///
/// For a reproductive-health app this isn't decoration — App Review looks for the privacy
/// policy and the medical disclaimer, and "which version am I on" is the first question any
/// support conversation starts with.
struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    private static let supportURL = URL(string: "https://quietflare.net/cycluna/support")!
    private static let siteURL = URL(string: "https://quietflare.net/cycluna")!

    /// Read from the bundle rather than hardcoded, so it can never drift from the build.
    private var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "—"
        let build = info?["CFBundleVersion"] as? String ?? "—"
        return "Version \(short) (\(build))"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(spacing: 10) {
                        // The real app artwork, not an SF Symbol stand-in — this is the one
                        // place in the app that should show the icon the user tapped.
                        // `continuous` matches the squircle iOS masks icons with.
                        Image("AppIconPreview")
                            .resizable()
                            .interpolation(.high)
                            .frame(width: 96, height: 96)
                            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous)
                                .stroke(Theme.inkSoft.opacity(0.15)))

                        Text("Cycluna")
                            .font(.cyclunaSerif(30)).foregroundStyle(Theme.ink)
                        Text(version)
                            .font(.subheadline).foregroundStyle(Theme.inkSoft)
                            .textSelection(.enabled)
                        Text("Your rhythm, in tune with the moon and your body.")
                            .font(.callout).foregroundStyle(Theme.inkSoft)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 2)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .listRowBackground(Color.clear)
                }

                Section("Legal") {
                    NavigationLink {
                        PrivacyPolicyView()
                    } label: {
                        aboutRow("Privacy Policy", "lock.shield")
                    }
                    NavigationLink {
                        MedicalDisclaimerView()
                    } label: {
                        aboutRow("Health Disclaimer", "heart.text.square")
                    }
                    NavigationLink {
                        AcknowledgementsView()
                    } label: {
                        aboutRow("Acknowledgements", "text.book.closed")
                    }
                }

                Section {
                    Link(destination: Self.supportURL) {
                        aboutRow("Support", "questionmark.circle")
                    }
                    Link(destination: Self.siteURL) {
                        aboutRow("quietflare.net/cycluna", "globe")
                    }
                } footer: {
                    Text("Made by QuietFlare")
                        .frame(maxWidth: .infinity)
                        .padding(.top, 8)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("About")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func aboutRow(_ title: String, _ icon: String) -> some View {
        Label {
            Text(title).foregroundStyle(Theme.ink)
        } icon: {
            Image(systemName: icon).foregroundStyle(Theme.primary)
        }
    }
}

/// The health disclaimer. Cycluna predicts from dates the user typed in; it measures nothing,
/// and it is not a contraceptive. Saying so plainly protects the user first and the App Store
/// review second (Guideline 1.4.1 on medical claims).
struct MedicalDisclaimerView: View {
    private struct Item: Identifiable {
        let id = UUID()
        let title: String
        let body: String
    }

    private let sections: [Item] = [
        .init(title: "Not medical advice",
              body: """
              Cycluna is a tracking and reflection tool, not a medical device and not a \
              substitute for professional care. Nothing in the app diagnoses, treats, or \
              prevents any condition.
              """),
        .init(title: "Predictions are estimates",
              body: """
              Every date the app shows — your next period, your fertile window, your current \
              phase — is calculated from the dates you enter. The app measures nothing about \
              your body. Cycles vary with stress, illness, travel, medication, and much else, \
              so real dates will differ from predicted ones.
              """),
        .init(title: "Not contraception",
              body: """
              The fertile window is an estimate and must not be used to prevent pregnancy. It \
              is not a fertility-awareness method, and it has not been evaluated or cleared by \
              any regulator for that purpose. Talk to a healthcare professional about \
              contraception.
              """),
        .init(title: "Patterns are your own",
              body: """
              Mood and headache insights describe what you have logged and nothing more. They \
              are observations about your own entries, not findings about health in general, \
              and Cycluna only names a pattern when there is enough data to support one.
              """),
        .init(title: "When to seek care",
              body: """
              Speak to a doctor or midwife about periods that are unusually heavy, painful, \
              absent, or irregular for you, about bleeding between periods or after sex, or \
              about any symptom that worries you. Do not wait on an app. In an emergency, \
              contact your local emergency services.
              """)
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                Text("Health Disclaimer")
                    .font(.cyclunaSerif(30)).foregroundStyle(Theme.ink)

                ForEach(sections) { section in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(section.title)
                            .font(.headline).foregroundStyle(Theme.primary)
                        Text(section.body)
                            .font(.body).foregroundStyle(Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(20)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle("Health Disclaimer")
        .navigationBarTitleDisplayMode(.inline)
    }
}
